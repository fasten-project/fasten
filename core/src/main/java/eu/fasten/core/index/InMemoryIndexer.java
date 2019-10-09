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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;
import it.unimi.dsi.webgraph.Transform;

/** A sample in-memory indexer that reads, compresses and stores in memory
 * graphs stored in JSON format and answers to impact queries.
 *
 */
public class InMemoryIndexer {

	public static class KafkaConsumerMonster {

	    private final static String BROKER = "localhost:30001,localhost:30002,localhost:30003";

	    public static Consumer<String, String> createConsumer(final String topic) {
	        final Properties props = new Properties();

	        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BROKER);
	        props.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString()); // We want to have a random consumer group.
	        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
	                StringDeserializer.class.getName());
	        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
	                StringDeserializer.class.getName());
	        props.put("auto.offset.reset", "earliest");
	        props.put("max.poll.records", Integer.toString(Integer.MAX_VALUE));

	        final Consumer<String, String> consumer = new KafkaConsumer<>(props);
	        consumer.subscribe(Collections.singletonList(topic));

	        return consumer;
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
		public final BVGraph graph;
		public final BVGraph transpose;
		private final String product;
		private final String version;
		private final String forge;

		protected CallGraph(final JSONCallGraph g) throws IOException {
			product = g.product;
			version = g.version;
			forge = g.forge;

			LOGGER.info("Analyzing " + forge + "/" + product + "/" + version);
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
			for(int i = 0; i < normalizedSources.size(); i++) {
				mutableGraph.addArc(GID2LID.get(URI2GID.getLong(normalizedSources.get(i))), GID2LID.get(URI2GID.getLong(normalizedTargets.get(i))));
			}

			final File f = File.createTempFile(InMemoryIndexer.class.getSimpleName(), ".tmpgraph");
			BVGraph.store(mutableGraph.immutableView(), f.toString());
			graph = BVGraph.load(f.toString());
			BVGraph.store(Transform.transpose(mutableGraph.immutableView()), f.toString());
			transpose = BVGraph.load(f.toString());
			new File(f.toString() + BVGraph.PROPERTIES_EXTENSION).delete();
			new File(f.toString() + BVGraph.OFFSETS_EXTENSION).delete();
			new File(f.toString() + BVGraph.GRAPH_EXTENSION).delete();
			f.delete();

			// Store dependencies between products
			for(final Dependency depset: g.depset) addDependency(product, depset.product);
		}

		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder();
			for(final NodeIterator nodeIterator = graph.nodeIterator(); nodeIterator.hasNext(); ) {
				final FastenURI u = GID2URI.get(LID2GID[nodeIterator.nextInt()]);
				final LazyIntIterator successors = nodeIterator.successors();
				for(int s; (s = successors.nextInt()) != -1; )
					b.append(u).append('\t').append(GID2URI.get(LID2GID[s])).append('\n');
			}
			return b.toString();
		}
	}

	public Collection<FastenURI> reaches(final FastenURI uri) {
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
			final LazyIntIterator successors = callGraph.graph.successors(lid);
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
					for(final CallGraph g: callGraphs) {
						if (g.product.equals(succProduct)) {
							// TODO: should be a raw version
							final FastenURI succVersionedURI = FastenURI.create(null, succURI.getRawProduct(), g.version, succURI.getRawNamespace(), succURI.getRawEntity());
							final long succVersionedGID = URI2GID.getLong(succVersionedURI);
							if (resultGID.add(succVersionedGID)) {
								queue.enqueue(Pair.of(g, Long.valueOf(succVersionedGID)));
								added = true;
							}
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

	public void add(final JSONCallGraph g) throws IOException {
		callGraphs.add(new CallGraph(g));
	}

	public static void main(final String[] args) throws JSONException, URISyntaxException, JSAPException, IOException {
		final SimpleJSAP jsap = new SimpleJSAP( JSONCallGraph.class.getName(),
				"Creates a searchable in-memory index from a list of JSON files",
				new Parameter[] {
					new FlaggedOption( "input", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'I', "input", "A file containing the input." ),
					new FlaggedOption( "topic", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 't', "topic", "A kafka topic containing the input." ),
					new UnflaggedOption( "filename", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.GREEDY, "The name of the file containing the JSON object." ),
			}
		);

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final InMemoryIndexer inMemoryIndexer = new InMemoryIndexer();
		if (jsapResult.userSpecified("topic")) {
			// Kafka consumer
			final String kafka = jsapResult.getString("topic");
			final Consumer<String, String> consumer = KafkaConsumerMonster.createConsumer(kafka);
			final ConsumerRecords<String, String> records = consumer.poll(Long.MAX_VALUE);

			for (final ConsumerRecord<String, String> record : records) {
				final JSONObject json = new JSONObject(record.value());
				try {
					inMemoryIndexer.add(new JSONCallGraph(json, false));
				} catch(final IllegalArgumentException e) {
					e.printStackTrace(System.err);
				}
			}
		}
		else {
			// Files
			for(final String file: jsapResult.getStringArray("filename")) {
				LOGGER.info("Parsing " + file);
				final FileReader reader = new FileReader(file);
				final JSONObject json = new JSONObject(new JSONTokener(reader));
				inMemoryIndexer.add(new JSONCallGraph(json, false));
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

		System.out.println("Indexing complete.");
		final BufferedReader br = new BufferedReader( new InputStreamReader( jsapResult.userSpecified( "input" ) ? new FileInputStream( jsapResult.getString( "input") ) : System.in ) );

		for ( ;; ) {
			System.out.print( ">" );
			final String q = br.readLine();
			if ( q == null ) {
				System.err.println();
				break; // CTRL-D
			}
			if ( q.length() == 0 ) continue;

			final FastenURI uri = FastenURI.create(q);
			final Collection<FastenURI> result = inMemoryIndexer.reaches(uri);
			if (result == null) {
				System.out.println("Method not indexed");
			}
			else if (result.size() == 0) {
				System.out.println("No method called");
			}
			else {
				final Iterator<FastenURI> iterator = result.iterator();
				for(int i = 0; iterator.hasNext() && i < 50; i++) System.out.println(iterator.next());
			}
		}
	}
}
