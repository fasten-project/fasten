/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.core.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.primitives.Longs;
import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JSONCallGraph;
import eu.fasten.core.data.JSONCallGraph.Dependency;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.NullInputStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;

/** A sample in-memory indexer that reads, compresses and stores in memory
 * graphs stored in JSON format and answers to impact queries.
 *
 */
public class InMemoryIndexer {

	public class BVGraphSerializer extends FieldSerializer<BVGraph> {

		public BVGraphSerializer(final Kryo kryo) {
			super(kryo, BVGraph.class);
		}

		@Override
		public BVGraph read(final Kryo kryo, final Input input, final Class<? extends BVGraph> type) {
			final BVGraph read = super.read(kryo, input, type);
			try {
				FieldUtils.writeField(read, "outdegreeIbs",
						new InputBitStream((byte[])FieldUtils.readField(read, "graphMemory", true)), true);
			} catch (final IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			return read;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(InMemoryIndexer.class);

	protected Object2LongMap<FastenURI> URI2GID = new Object2LongOpenHashMap<>();
	{
		URI2GID.defaultReturnValue(-1);
	}
	protected Long2ObjectMap<FastenURI> GID2URI = new Long2ObjectOpenHashMap<>();
	protected ObjectOpenHashSet<CallGraph> callGraphs = new ObjectOpenHashSet<>();

	/* We keep track of dependencies between products, only. The final goal
	 * is to keep track of dependencies between revisions. */
	protected Object2ObjectOpenHashMap<String, ObjectOpenHashSet<String>> product2Dependecies = new Object2ObjectOpenHashMap<>();
	protected Object2ObjectOpenHashMap<String, ObjectOpenHashSet<String>> dependency2Products = new Object2ObjectOpenHashMap<>();

	FieldSerializer<?> serializer;
	/** The RocksDB instance used by this indexer. */
	private final RocksDB db;
	Kryo kryo;

	public InMemoryIndexer(final RocksDB db) {
		this.db = db;
		kryo = new Kryo();
		kryo.register(BVGraph.class, new BVGraphSerializer(kryo));
		kryo.register(byte[].class);
		kryo.register(InputBitStream.class);
		kryo.register(NullInputStream.class);
		kryo.register(EliasFanoMonotoneLongBigList.class, new JavaSerializer());
		kryo.register(MutableString.class, new FieldSerializer<>(kryo, MutableString.class));
	}

	/** Adds a URI to the global maps. If the URI is already present, returns its GID.
	 *
	 * @param uri a Fasten URI.
	 * @return the associated GID.
	 */
	protected long addURI(final FastenURI uri) {
		long gid = URI2GID.getLong(uri);
		if (gid != -1) return gid;
		gid = URI2GID.size();
		URI2GID.put(uri, gid);
		GID2URI.put(gid, uri);
		return gid;
	}

	/** Adds a dependency to the global maps.
	 *
	 * @param product a product.
	 * @param dependency its dependency (another product).
	 */
	protected void addDependency(final String product, final String dependency) {
		ObjectOpenHashSet<String> dependencies = product2Dependecies.get(product);
		if (dependencies == null) product2Dependecies.put(product, dependencies = new ObjectOpenHashSet<>());
		dependencies.add(dependency);

		ObjectOpenHashSet<String> products = dependency2Products.get(dependency);
		if (products == null) dependency2Products.put(dependency, products = new ObjectOpenHashSet<>());
		products.add(product);
	}

	protected class CallGraph {
		public final long[] LID2GID;
		public final Long2IntOpenHashMap GID2LID = new Long2IntOpenHashMap();
		private final String product;
		private final String version;
		private final String forge;
		private final long index;

		protected CallGraph(final JSONCallGraph g, final long index) throws IOException, RocksDBException {
			product = g.product;
			version = g.version;
			forge = g.forge;
			this.index = index;

			LOGGER.info("Analyzing fasten://" + forge + "!" + product + "$" + version);
			final ArrayList<FastenURI[]> arcs = g.graph;

			// Add URIs to the global map, and create a temporary linked local map
			final LongLinkedOpenHashSet gids = new LongLinkedOpenHashSet();
			final int currentGIDs = URI2GID.size();
			// Normalize arcs
			final ObjectArrayList<FastenURI> normalizedSources = new ObjectArrayList<>();
			final ObjectArrayList<FastenURI> normalizedTargets = new ObjectArrayList<>();

			for(final FastenURI[] arc: arcs) {
				// TODO: this should be a raw product and a raw version
				final FastenURI source = FastenURI.create(null, product, version, arc[0].getRawNamespace(), arc[0].getRawEntity());
				final long gid = addURI(source);
				if (gids.add(gid) && gid < currentGIDs) throw new IllegalArgumentException("URI " + source + " appears as source in two different revisions");
				if (!FastenURI.NULL_FASTEN_URI.equals(arc[1])) {
					normalizedSources.add(source);
					String targetRawProduct = arc[1].getRawProduct();
					String targetRawVersion = null;
					if (targetRawProduct == null) {
						targetRawProduct = product;
						targetRawVersion = version;
					}
					final FastenURI target = FastenURI.create(null, targetRawProduct, targetRawVersion, arc[1].getRawNamespace(), arc[1].getRawEntity());
					gids.add(addURI(target));
					normalizedTargets.add(target);
				}
			}
			// Set up local bijection
			LID2GID = LongIterators.unwrap(gids.iterator());
			final LongListIterator iterator = gids.iterator();
			for(int i = 0; i < LID2GID.length; i++) GID2LID.put(iterator.nextLong(), i);

			// Create, store and load compressed versions of the graph and of the transpose.
			final ArrayListMutableGraph mutableGraph = new ArrayListMutableGraph(LID2GID.length);
			for(int i = 0; i < normalizedSources.size(); i++)
				mutableGraph.addArc(GID2LID.get(URI2GID.getLong(normalizedSources.get(i))), GID2LID.get(URI2GID.getLong(normalizedTargets.get(i))));

			final File f = File.createTempFile(InMemoryIndexer.class.getSimpleName(), ".tmpgraph");
			BVGraph.store(mutableGraph.immutableView(), f.toString());

			final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
			final ByteBufferOutput bbo = new ByteBufferOutput(fbaos);
			kryo.writeObject(bbo, BVGraph.load(f.toString()));
			bbo.flush();
			db.put(Longs.toByteArray(index), 0, 8, fbaos.array, 0, fbaos.length);

			//BVGraph.store(Transform.transpose(mutableGraph.immutableView()), f.toString());
			//transpose = BVGraph.load(f.toString());
			new File(f.toString() + BVGraph.PROPERTIES_EXTENSION).delete();
			new File(f.toString() + BVGraph.OFFSETS_EXTENSION).delete();
			new File(f.toString() + BVGraph.GRAPH_EXTENSION).delete();
			f.delete();

			// Store dependencies between products
			for(final Dependency depset: g.depset) addDependency(product, depset.product);
		}

		@SuppressWarnings("null")
		public ImmutableGraph graph() {
			try {
				final byte[] buffer = new byte[1000000];
				db.get(Longs.toByteArray(index), buffer);
				return kryo.readObject(new Input(buffer), BVGraph.class);
			} catch (final RocksDBException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder();
			for(final NodeIterator nodeIterator = graph().nodeIterator(); nodeIterator.hasNext(); ) {
				final FastenURI u = GID2URI.get(LID2GID[nodeIterator.nextInt()]);
				final LazyIntIterator successors = nodeIterator.successors();
				for(int s; (s = successors.nextInt()) != -1; )
					b.append(u).append('\t').append(GID2URI.get(LID2GID[s])).append('\n');
			}
			return b.toString();
		}
	}

	public synchronized Collection<FastenURI> reaches(final FastenURI uri) {
		final long uriGID = URI2GID.getLong(uri);
		if (uriGID == -1) return null;
		// Accumulates results
		final LongLinkedOpenHashSet resultGID = new LongLinkedOpenHashSet();
		// Visit queue
		final ObjectArrayFIFOQueue<Pair<CallGraph, Long>> queue = new ObjectArrayFIFOQueue<>();
		// Compute seed
		for(final CallGraph g: callGraphs)
			if (g.GID2LID.containsKey(uriGID)) queue.enqueue(Pair.of(g, Long.valueOf(uriGID)));

		while(!queue.isEmpty()) {
			final Pair<CallGraph, Long> pair = queue.dequeue();
			final long gid = pair.getRight().longValue();
			final CallGraph callGraph = pair.getLeft();
			final String product = callGraph.product;
			final int lid = callGraph.GID2LID.get(gid);
			final LazyIntIterator successors = callGraph.graph().successors(lid);
			for(int s; (s = successors.nextInt()) != -1;) {
				final long succGID = callGraph.LID2GID[s];
				final FastenURI succURI = GID2URI.get(succGID);

				if (succURI.getProduct().equals(product)) {
					// Internal node
					if (resultGID.add(succGID)) queue.enqueue(Pair.of(callGraph, Long.valueOf(succGID)));
				}
				else {
					final String succProduct = succURI.getProduct();
					assert succURI.getVersion() == null : succURI.getVersion();
					boolean added = false;
					for(final CallGraph g: callGraphs)
						if (g.product.equals(succProduct)) {
							// TODO: should be a raw version
							final FastenURI succVersionedURI = FastenURI.create(null, succURI.getRawProduct(), g.version, succURI.getRawNamespace(), succURI.getRawEntity());
							final long succVersionedGID = URI2GID.getLong(succVersionedURI);
							if (resultGID.add(succVersionedGID)) {
								queue.enqueue(Pair.of(g, Long.valueOf(succVersionedGID)));
								added = true;
							}
						}
					// If no versioned URI is found, we leave the external reference
					if (!added) resultGID.add(succGID);
				}
			}
		}

		final ObjectArrayList<FastenURI> result = new ObjectArrayList<>();
		for(final long g: resultGID) result.add(GID2URI.get(g));
		return result;
	}

	public synchronized void add(final JSONCallGraph g, final long index) throws IOException, RocksDBException {
		callGraphs.add(new CallGraph(g, index));
	}

	public static void main(final String[] args) throws JSONException, URISyntaxException, JSAPException, IOException, RocksDBException {
		final SimpleJSAP jsap = new SimpleJSAP( JSONCallGraph.class.getName(),
				"Creates a searchable in-memory index from a list of JSON files",
				new Parameter[] {
						new FlaggedOption( "input", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'I', "input", "A file containing the input." ),
						new FlaggedOption( "topic", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 't', "topic", "A kafka topic containing the input." ),
						new FlaggedOption( "host", JSAP.STRING_PARSER, "localhost", JSAP.NOT_REQUIRED, 'h', "host", "The host of the Kafka server." ),
						new FlaggedOption( "port", JSAP.INTEGER_PARSER, "30001", JSAP.NOT_REQUIRED, 'p', "port", "The port of the Kafka server." ),
						new UnflaggedOption( "filename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.GREEDY, "The name of the file containing the JSON object." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		RocksDB.loadLibrary();
		final Options options = new Options();
		options.setCreateIfMissing(true);
		final RocksDB db = RocksDB.open(options, "rocksdb");

		final InMemoryIndexer inMemoryIndexer = new InMemoryIndexer(db);
		final Consumer<String, String> consumer;
		final boolean[] stop = new boolean[1];
		if (jsapResult.userSpecified("topic")) {
			// Kafka consumer
			final String topic = jsapResult.getString("topic");
			final Properties props = new Properties();
			props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, jsapResult.getString("host") + ":" + Integer.toString(jsapResult.getInt("port")));
			props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString()); // We want to have a random consumer group.
			props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
			props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
			props.put("auto.offset.reset", "earliest");
			props.put("max.poll.records", Integer.toString(Integer.MAX_VALUE));
			consumer = new KafkaConsumer<>(props);
			consumer.subscribe(Collections.singletonList(topic));

			final Future<?> future = Executors.newSingleThreadExecutor().submit(() -> {
				long index = 0;
				try {
					while(!stop[0]) {
						final ConsumerRecords<String, String> records = consumer.poll(Duration.ofDays(356));

						for (final ConsumerRecord<String, String> record : records) {
							if (stop[0]) break;
							final JSONObject json = new JSONObject(record.value());
							try {
								inMemoryIndexer.add(new JSONCallGraph(json, false), index++);
							} catch(final IllegalArgumentException e) {
								e.printStackTrace(System.err);
								throw new RuntimeException(e);
							}
						}
					}

					return null;
				}
				finally {
					consumer.close();
				}
			});
			try {
				future.get();
			} catch (final ExecutionException e) {
				e.getCause().printStackTrace();
			} catch (final InterruptedException e) {
				System.err.println("*** Interrupted");
			}
		} else {// Files
			consumer = null;
			long index = 0;
			for(final String file: jsapResult.getStringArray("filename")) {
				LOGGER.info("Parsing " + file);
				final FileReader reader = new FileReader(file);
				final JSONObject json = new JSONObject(new JSONTokener(reader));
				inMemoryIndexer.add(new JSONCallGraph(json, false), index++);
				reader.close();
			}
		}

		/*for(final CallGraph g: inMemoryIndexer.callGraphs) {
			System.err.println(g);
		}

		System.out.print(inMemoryIndexer.URI2GID);
		System.out.println(inMemoryIndexer.product2Dependecies);
		System.out.println(inMemoryIndexer.dependency2Products);
		 */

		final BufferedReader br = new BufferedReader( new InputStreamReader( jsapResult.userSpecified( "input" ) ? new FileInputStream( jsapResult.getString( "input") ) : System.in ) );

		for ( ;; ) {
			System.out.print( ">" );
			final String q = br.readLine();
			if (q == null || "$quit".equals(q)) {
				System.err.println("Exiting");
				stop[0] = true;
				consumer.wakeup();
				break; // CTRL-D
			}
			if ( q.length() == 0 ) continue;

			final FastenURI uri = FastenURI.create(q);
			final Collection<FastenURI> result = inMemoryIndexer.reaches(uri);
			if (result == null) System.out.println("Method not indexed");
			else if (result.size() == 0) System.out.println("No method called");
			else {
				final Iterator<FastenURI> iterator = result.iterator();
				for(int i = 0; iterator.hasNext() && i < 50; i++) System.out.println(iterator.next());
			}
		}

		db.close();
		options.close();
	}
}
