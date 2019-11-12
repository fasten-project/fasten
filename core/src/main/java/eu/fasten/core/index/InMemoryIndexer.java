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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.reflect.FieldUtils;
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
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
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
import it.unimi.dsi.webgraph.Transform;
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

	/** Maps schemeless, <em>generic</em> (i.e., without forge and without version, but with a product) FASTEN URIs to a unique identifier. */
	protected Object2LongMap<FastenURI> genericURI2GID = new Object2LongOpenHashMap<>();
	{
		genericURI2GID.defaultReturnValue(-1);
	}
	/** The inverse of {@link #genericURI2GID}. */
	protected Long2ObjectMap<FastenURI> GID2GenericURI = new Long2ObjectOpenHashMap<>();

	/** Maps each GID to a list of revisions (identified by their index) in which the GID appears as an internal node. */
	protected Long2ObjectMap<LongSet> GIDAppearsIn = new Long2ObjectOpenHashMap<>();
	{
		GIDAppearsIn.defaultReturnValue(LongSets.EMPTY_SET);
	}

	/** Maps each GID to a list of revisions (identified by their index) in which the GID appears as an external node. */
	protected Long2ObjectMap<LongSet> GIDCalledBy = new Long2ObjectOpenHashMap<>();
	{
		GIDCalledBy.defaultReturnValue(LongSets.EMPTY_SET);
	}

	protected Long2ObjectOpenHashMap<CallGraph> callGraphs = new Long2ObjectOpenHashMap<>();

	/* We keep track of dependencies between products, only. The final goal
	 * is to keep track of dependencies between revisions. */
	protected Object2ObjectOpenHashMap<String, ObjectOpenHashSet<String>> product2Dependecies = new Object2ObjectOpenHashMap<>();
	protected Object2ObjectOpenHashMap<String, ObjectOpenHashSet<String>> dependency2Products = new Object2ObjectOpenHashMap<>();


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
		long gid = genericURI2GID.getLong(uri);
		if (gid != -1) return gid;
		gid = genericURI2GID.size();
		genericURI2GID.put(uri, gid);
		GID2GenericURI.put(gid, uri);
		return gid;
	}

	protected static void addGidRev(final Long2ObjectMap<LongSet> map, final long gid, final long revIndex) {
		LongSet set = map.get(gid);
		if (set == LongSets.EMPTY_SET) map.put(gid, set = new LongOpenHashSet());
		set.add(revIndex);
	}


	public ObjectList<long[]> successors(final long... node) {
		assert node.length == 2;
		final long gid = node[0];
		final long index = node[1];
		final CallGraph callGraph = callGraphs.get(index);
		assert callGraph != null;

		final ImmutableGraph graph = callGraph.graphs()[0];
		final LazyIntIterator s = graph.successors(callGraph.GID2LID.get(gid));

		final ObjectList<long[]> result = new ObjectArrayList<>();
		int x;

		/* In the successor case, internal nodes can be added directly... */

		while((x = s.nextInt()) != -1 && x < callGraph.nInternal) result.add(new long[] { callGraph.LID2GID[x], index } );

		if (x == -1) return result;

		/* ...but external nodes must be search for in the revision call graphs in which they appear. */
		do {
			final long xGid = callGraph.LID2GID[x];
			for(final LongIterator revisions = GIDAppearsIn.get(xGid).iterator(); revisions.hasNext();)
				result.add(new long[] { xGid, revisions.nextLong() });
		} while((x = s.nextInt()) != -1);

		return result;
	}

	public ObjectList<long[]> predecessors(final long... node) {
		assert node.length == 2;
		final long gid = node[0];
		final long index = node[1];
		final CallGraph callGraph = callGraphs.get(index);
		assert callGraph != null;

		final ImmutableGraph graph = callGraph.graphs()[1];
		final LazyIntIterator s = graph.successors(callGraph.GID2LID.get(gid));

		final ObjectList<long[]> result = new ObjectArrayList<>();
		int x;

		/* In the predecessor case, all nodes returned by the graph are necessarily internal. */
		while((x = s.nextInt()) != -1) {
			assert x < callGraph.nInternal;
			result.add(new long[] { callGraph.LID2GID[x], index } );
		}

		/* To move backward in the call graph, we use GIDCalledBy to find revisions that might
		 * contain external nodes of the form <gid, index>. */
		do
			for(final LongIterator revisions = GIDCalledBy.get(gid).iterator(); revisions.hasNext();) {
				final long revIndex = revisions.nextLong();
				final CallGraph precCallGraph = callGraphs.get(revIndex);
				final ImmutableGraph transpose = precCallGraph.graphs()[1];
				final LazyIntIterator p = transpose.successors(precCallGraph.GID2LID.get(gid));
				for(int y; (y = p.nextInt()) != -1;) result.add(new long[] { precCallGraph.LID2GID[y], revIndex });
			}
		while((x = s.nextInt()) != -1);

		return result;
	}

	public FastenURI node2FastenURI(final long...node) {
		assert node.length == 2;
		final long gid = node[0];
		final long index = node[1];
		final FastenURI genericURI = GID2GenericURI.get(gid);
		if (genericURI == null) return null;
		final CallGraph callGraph = callGraphs.get(index);
		assert genericURI.getProduct().equals(callGraph.product) : genericURI.getProduct() + " != " + callGraph.product;
		return FastenURI.create(callGraph.forge, callGraph.product, callGraph.version, genericURI.getRawNamespace(), genericURI.getRawEntity());
	}

	public long[] fastenURI2Node(final FastenURI fastenURI) {
		if (fastenURI.getVersion() == null) throw new IllegalArgumentException("The FASTEN URI must be versioned");
		final FastenURI genericURI = FastenURI.createSchemeless(null, fastenURI.getRawProduct(), null, fastenURI.getRawNamespace(), fastenURI.getRawEntity());
		final long gid = genericURI2GID.getLong(genericURI);
		if (gid == -1) return null;
		final String version = fastenURI.getVersion();
		for(final long index: GIDAppearsIn.get(gid))
			if (version.equals(callGraphs.get(index).version)) return new long[] { gid, index };

		return null;
	}

	public ObjectList<FastenURI> genericURI2URIs(final FastenURI genericURI) {
		if (genericURI.getVersion() != null || genericURI.getScheme() != null) throw new IllegalArgumentException("The FASTEN URI must be generic and schemeless");
		final long gid = genericURI2GID.getLong(genericURI);
		if (gid == -1) return null;
		final ObjectArrayList<FastenURI> result = new ObjectArrayList<>();
		for(final long index: GIDAppearsIn.get(gid)) result.add(FastenURI.createSchemeless(genericURI.getRawForge(), genericURI.getRawProduct(), callGraphs.get(index).version, genericURI.getRawNamespace(), genericURI.getRawEntity()));
		return result;
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
		/** Number of internal nodes (first {@link #nInternal} GIDs in {@link #LID2GID}). */
		public final int nInternal;
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

			LOGGER.debug("Analyzing fasten://" + forge + "!" + product + "$" + version);
			final ArrayList<FastenURI[]> arcs = g.graph;

			/*
			 * Pass over arc list, building schemeless generic pairs URIs.
			 * adding products where missing and skipping NULL_FASTEN_URIs).
			 * Source and target URIs are passed to addURI(), updating GenericURI2GID
			 * and its inverse GID2GenericURI.
			 */
			final ObjectArrayList<FastenURI> genericSources = new ObjectArrayList<>();
			final ObjectArrayList<FastenURI> genericTargets = new ObjectArrayList<>();
			final LongLinkedOpenHashSet internalGIDs = new LongLinkedOpenHashSet(); // List of internal GIDs
			final LongLinkedOpenHashSet externalGIDs = new LongLinkedOpenHashSet(); // List of external GIDs

			for(final FastenURI[] arc: arcs) {
				// TODO: this should be a raw product
				final FastenURI sourceSchemelessGenericURI = FastenURI.createSchemeless(null, product, null, arc[0].getRawNamespace(), arc[0].getRawEntity());
				final long sourceGID = addURI(sourceSchemelessGenericURI);
				addGidRev(GIDAppearsIn, sourceGID, index);
				internalGIDs.add(sourceGID);

				if (!FastenURI.NULL_FASTEN_URI.equals(arc[1])) {
					genericSources.add(sourceSchemelessGenericURI);
					// TODO: one should check that forge/version are null
					String targetRawProduct = arc[1].getRawProduct();
					final boolean internal = targetRawProduct == null;
					if (internal) targetRawProduct = product;
					final FastenURI target = FastenURI.createSchemeless(null, targetRawProduct, null, arc[1].getRawNamespace(), arc[1].getRawEntity());
					final long targetGID = addURI(target);

					if (internal) {
						addGidRev(GIDAppearsIn, targetGID, index);
						internalGIDs.add(targetGID);
					}
					else {
						addGidRev(GIDCalledBy, targetGID, index);
						externalGIDs.add(targetGID);
					}

					genericTargets.add(target);
				}
			}
			// Set up local bijection
			nInternal = internalGIDs.size();

			LID2GID = new long[internalGIDs.size() + externalGIDs.size()];
			LongIterators.unwrap(internalGIDs.iterator(), LID2GID);
			LongIterators.unwrap(externalGIDs.iterator(), LID2GID, nInternal, Integer.MAX_VALUE);
			GID2LID.defaultReturnValue(-1);
			for(int i = 0; i < LID2GID.length; i++) GID2LID.put(LID2GID[i], i);

			// Create, store and load compressed versions of the graph and of the transpose.
			final ArrayListMutableGraph mutableGraph = new ArrayListMutableGraph(LID2GID.length);
			for(int i = 0; i < genericSources.size(); i++) {
				assert genericURI2GID.getLong(genericSources.get(i)) != -1;
				assert genericURI2GID.getLong(genericTargets.get(i)) != -1;
				mutableGraph.addArc(GID2LID.get(
						genericURI2GID.getLong(genericSources.get(i))),
						GID2LID.get(
								genericURI2GID.getLong(genericTargets.get(i))));
			}

			final File f = File.createTempFile(InMemoryIndexer.class.getSimpleName(), ".tmpgraph");

			// Compress, load and serialize graph
			BVGraph.store(mutableGraph.immutableView(), f.toString());

			final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
			final ByteBufferOutput bbo = new ByteBufferOutput(fbaos);
			kryo.writeObject(bbo, BVGraph.load(f.toString()));

			// Compress, load and serialize transpose graph
			BVGraph.store(Transform.transpose(mutableGraph.immutableView()), f.toString());

			kryo.writeObject(bbo, BVGraph.load(f.toString()));
			bbo.flush();
			db.put(Longs.toByteArray(index), 0, 8, fbaos.array, 0, fbaos.length);

			new File(f.toString() + BVGraph.PROPERTIES_EXTENSION).delete();
			new File(f.toString() + BVGraph.OFFSETS_EXTENSION).delete();
			new File(f.toString() + BVGraph.GRAPH_EXTENSION).delete();
			f.delete();

			// Store dependencies between products
			for(final Dependency depset: g.depset) addDependency(product, depset.product);
		}

		@SuppressWarnings("null")
		public ImmutableGraph[] graphs() {
			try {
				// TODO: dynamic
				final byte[] buffer = new byte[1000000];
				db.get(Longs.toByteArray(index), buffer);
				final Input input = new Input(buffer);
				return new ImmutableGraph[] { kryo.readObject(input, BVGraph.class),  kryo.readObject(input, BVGraph.class) };
			} catch (final RocksDBException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder();
			for(final NodeIterator nodeIterator = graphs()[0].nodeIterator(); nodeIterator.hasNext(); ) {
				final FastenURI u = GID2GenericURI.get(LID2GID[nodeIterator.nextInt()]);
				final LazyIntIterator successors = nodeIterator.successors();
				for(int s; (s = successors.nextInt()) != -1; )
					b.append(u).append('\t').append(GID2GenericURI.get(LID2GID[s])).append('\n');
			}
			return b.toString();
		}
	}

	protected synchronized ObjectLinkedOpenCustomHashSet<long[]> reaches(final long... start) {
		final ObjectLinkedOpenCustomHashSet<long[]> result = new ObjectLinkedOpenCustomHashSet<>(LongArrays.HASH_STRATEGY);
		// Visit queue
		final ObjectArrayFIFOQueue<long[]> queue = new ObjectArrayFIFOQueue<>();
		queue.enqueue(start);

		while(!queue.isEmpty()) {
			final long[] node = queue.dequeue();
			if (result.add(node)) for(final long[] s: successors(node))
				if (!result.contains(s)) queue.enqueue(s);
		}

		return result;
	}

	public Collection<FastenURI> reaches(final FastenURI fastenURI) {
		final ObjectLinkedOpenCustomHashSet<long[]> reaches = reaches(fastenURI2Node(fastenURI));
		final ObjectArrayList<FastenURI> resultURI = new ObjectArrayList<>();
		for(final long[] node: reaches) resultURI.add(node2FastenURI(node));
		return resultURI;
	}

	public static String toString(final Iterable<long[]> iterable) {
		final StringBuilder b = new StringBuilder("{");
		for(final long[]a : iterable) {
			if (b.length() != 1) b.append(", ");
			b.append(Arrays.toString(a));
		}
		return b.append("}").toString();
	}

	public String toLIDString(final Iterable<long[]> iterable) {
		final StringBuilder b = new StringBuilder("{");
		for(final long[] node : iterable) {
			if (b.length() != 1) b.append(", ");
			b.append(toLIDString(node));
		}
		return b.append("}").toString();
	}

	public String toLIDString(final long... node) {
		return Arrays.toString(new long[] { callGraphs.get(node[1]).GID2LID.get(node[0]), node[1]});
	}

	public synchronized ObjectLinkedOpenCustomHashSet<long[]> coreaches(final long... start) {
		final ObjectLinkedOpenCustomHashSet<long[]> result = new ObjectLinkedOpenCustomHashSet<>(LongArrays.HASH_STRATEGY);
		// Visit queue
		final ObjectArrayFIFOQueue<long[]> queue = new ObjectArrayFIFOQueue<>();
		queue.enqueue(start);

		while(!queue.isEmpty()) {
			final long[] node = queue.dequeue();
			if (result.add(node))
				for(final long[] s: predecessors(node))
					if (!result.contains(s)) queue.enqueue(s);
		}

		return result;
	}

	public synchronized Collection<FastenURI> coreaches(final FastenURI fastenURI) {
		final ObjectLinkedOpenCustomHashSet<long[]> coreaches = coreaches(fastenURI2Node(fastenURI));
		final ObjectArrayList<FastenURI> resultURI = new ObjectArrayList<>();
		for(final long[] node: coreaches) resultURI.add(node2FastenURI(node));
		return resultURI;
	}

	public synchronized void add(final JSONCallGraph g, final long index) throws IOException, RocksDBException {
		callGraphs.put(index, new CallGraph(g, index));
	}

	public static void main(final String[] args) throws JSONException, URISyntaxException, JSAPException, IOException, RocksDBException, InterruptedException, ExecutionException {
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
		Future<?> future = null;
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

			future = Executors.newSingleThreadExecutor().submit(() -> {
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
		} else {// Files
			consumer = null;
			long index = 0;
			for(final String file: jsapResult.getStringArray("filename")) {
				LOGGER.debug("Parsing " + file);
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
			if (future != null && future.isDone()) future.get();

			System.out.print( ">" );
			final String q = br.readLine();
			if (q == null || "$quit".equals(q)) {
				System.err.println("Exiting");
				stop[0] = true;
				consumer.wakeup();
				break; // CTRL-D
			}
			if ( q.length() == 0 ) continue;

			final FastenURI uri = FastenURI.create(q.substring(1));
			final boolean forward = q.startsWith("+");
			final Collection<FastenURI> result = forward ? inMemoryIndexer.reaches(uri) : inMemoryIndexer.coreaches(uri);
			if (result == null) System.out.println("Method not indexed");
			else if (result.size() == 0) System.out.println(forward? "No method called" : "No method calls this method");
			else {
				final Iterator<FastenURI> iterator = result.iterator();
				for(int i = 0; iterator.hasNext() && i < 50; i++) System.out.println(iterator.next());
			}
		}

		db.close();
		options.close();
	}
}
