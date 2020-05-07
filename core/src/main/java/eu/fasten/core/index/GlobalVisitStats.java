package eu.fasten.core.index;

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

import static eu.fasten.core.data.KnowledgeBase.index;
import static eu.fasten.core.data.KnowledgeBase.gid;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.mutable.MutableLong;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.KnowledgeBase;
import eu.fasten.core.data.KnowledgeBase.CallGraph;
import eu.fasten.core.data.KnowledgeBase.CallGraphData;
import eu.fasten.core.data.KnowledgeBase.Node;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.stat.SummaryStats;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandom;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;


public class GlobalVisitStats {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalVisitStats.class);

	public static class Result {
		final long numNodes;
		final long numProducts;
		final long numRevs;

		public Result(final long numNodes, final long numProducts, final long numRevs) {
			this.numRevs = numRevs;
			this.numNodes = numNodes;
			this.numProducts = numProducts;
		}
	}

	public static Result reaches(final KnowledgeBase kb, final long startSig, final int maxRevs, final ProgressLogger pl) {
		final LongOpenHashSet result = new LongOpenHashSet();
		final Object2ObjectOpenHashMap<String, IntOpenHashSet> product2Revs = new Object2ObjectOpenHashMap<>();
		final MutableLong totRevs = new MutableLong();

		// Visit queue
		final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		queue.enqueue(startSig);
		result.add(startSig);

		String p = kb.callGraphs.get(index(startSig)).product;
		IntOpenHashSet revs = new IntOpenHashSet();
		revs.add(index(startSig));
		product2Revs.put(p, revs);
		totRevs.increment();


		pl.itemsName = "nodes";
		pl.info = new Object() {
			@Override
			public String toString() {
				return "[nodes: " + result.size() + " products: " + product2Revs.size() + " revisions: " + totRevs.getValue() + "]";
			}
		};

		pl.start("Visiting reachable nodes...");

		while (!queue.isEmpty()) {
			final long node = queue.dequeueLong();

			for (final long s : kb.successors(node)) if (!result.contains(s)) {
				p = kb.callGraphs.get(index(s)).product;
				revs = product2Revs.get(p);
				if (revs == null) product2Revs.put(p, revs = new IntOpenHashSet());
				if (revs.contains(index(s)) || revs.size() < maxRevs) {
					String targetNameSpace = kb.new Node(gid(s), index(s)).toFastenURI().getRawNamespace();
					if (targetNameSpace.startsWith("java.") || targetNameSpace.startsWith("javax.")) continue; 
					queue.enqueue(s);
					result.add(s);
					if (revs.add(index(s))) totRevs.increment();
				}
			}
			pl.lightUpdate();
		}

		pl.done();
		return new Result(result.size(), product2Revs.size(), totRevs.getValue().longValue());
	}

	public static Result coreaches(final KnowledgeBase kb, final long startSig, final ProgressLogger pl) {
		final LongOpenHashSet result = new LongOpenHashSet();
		final IntOpenHashSet graphs = new IntOpenHashSet();
		// Visit queue
		final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		queue.enqueue(startSig);
		result.add(startSig);

		pl.itemsName = "nodes";
		pl.info = new Object() {
			@Override
			public String toString() {
				return "[nodes: " + result.size() + " graphs: " + graphs.size() + "]";
			}
		};
		pl.start("Visiting coreachable nodes...");
		while (!queue.isEmpty()) {
			final long node = queue.dequeueLong();
			graphs.add(index(node));
			for (final long s : kb.predecessors(node)) if (!result.contains(s)) {
				queue.enqueue(s);
				result.add(s);
			}
			pl.lightUpdate();
		}

		pl.done();
		return new Result(result.size(), graphs.size(), 0);
	}

	public static int reachable(final ImmutableGraph graph, final int startingNode) {
		final int n = graph.numNodes();
		final boolean[] known = new boolean[n];
		final IntArrayFIFOQueue queue = new IntArrayFIFOQueue();

		queue.enqueue(startingNode);
		known[startingNode] = true;
		int visited = 0;

		while (!queue.isEmpty()) {
			final int currentNode = queue.dequeueInt();
			visited++;
			final LazyIntIterator iterator = graph.successors(currentNode);
			for (int succ; (succ = iterator.nextInt()) != -1;) {
				if (!known[succ]) {
					known[succ] = true;
					queue.enqueue(succ);
				}
			}
		}

		return visited;
	}

	public static void main(final String[] args) throws JSAPException, ClassNotFoundException, RocksDBException, IOException {
		final SimpleJSAP jsap = new SimpleJSAP(GlobalVisitStats.class.getName(),
				"Computes (co)reachable set statistics for a prototype knowledge base.",
				new Parameter[] {
						new FlaggedOption("maxRevs", JSAP.INTEGER_PARSER, Integer.toString(Integer.MAX_VALUE), JSAP.NOT_REQUIRED, 'm', "max-revs", "The maximum number of revision per product during the visit."),
						new FlaggedOption("n", JSAP.INTEGER_PARSER, "0", JSAP.NOT_REQUIRED, 'n', "n", "The the number of samples."),
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
						new UnflaggedOption("kbmeta", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The file containing the knowledge base metadata." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final int n = jsapResult.getInt("n");
		final int maxRevs = jsapResult.getInt("maxRevs");
		final String kbDir = jsapResult.getString("kb");
		if (!new File(kbDir).exists()) throw new IllegalArgumentException("No such directory: " + kbDir);
		final String kbMetadataFilename = jsapResult.getString("kbmeta");
		if (!new File(kbMetadataFilename).exists()) throw new IllegalArgumentException("No such file: " + kbMetadataFilename);
		LOGGER.info("Loading KnowledgeBase metadata");
		final KnowledgeBase kb = KnowledgeBase.getInstance(kbDir, kbMetadataFilename);
		LOGGER.info("Number of graphs: " + kb.callGraphs.size());

		final ProgressLogger pl = new ProgressLogger();

		pl.count = kb.callGraphs.size();
		pl.itemsName = "nodes";
		pl.start("Enumerating nodes");

		final SummaryStats reachable = new SummaryStats();
		final SummaryStats coreachable = new SummaryStats();
		final SummaryStats reachableGraphs = new SummaryStats();
		final SummaryStats coreachableGraphs = new SummaryStats();

		final XoRoShiRo128PlusPlusRandom random = new XoRoShiRo128PlusPlusRandom(0);
		final long[] callGraphIndex = kb.callGraphs.keySet().toLongArray();

		final ProgressLogger pl2 = new ProgressLogger(LOGGER);

		for (int i = 0; i < n; i++) {
			pl.update();
			final int index = random.nextInt(callGraphIndex.length);
			final CallGraph callGraph = kb.callGraphs.get(callGraphIndex[index]);
			final CallGraphData callGraphData = callGraph.callGraphData();
			final int startNode = random.nextInt(callGraph.nInternal);
			final Node node = kb.new Node(callGraphData.LID2GID[startNode], index);
			LOGGER.info("Analyzing node " + node.toFastenURI());
			final Result reaches = reaches(kb, node.signature(), maxRevs, pl2);
			reachable.add(reaches.numNodes);
			reachableGraphs.add(reaches.numProducts);
			final Result coreaches = coreaches(kb, node.signature(), pl2);
			coreachable.add(coreaches.numNodes);
			coreachableGraphs.add(coreaches.numProducts);
		}

		pl.done();
		LOGGER.info("Closing KnowledgeBase");
		kb.close();
		System.out.println("Reachable sets stats: " + reachable);
		System.out.println("Coreachable sets stats: " + coreachable);
	}
}
