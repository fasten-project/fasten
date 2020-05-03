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

import java.io.File;
import java.io.IOException;

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
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.stat.SummaryStats;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandom;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;


public class GlobalVisitStats {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalVisitStats.class);

	public static int reaches(final KnowledgeBase kb, final Node start, final ProgressLogger pl) {
		final ObjectOpenHashSet<Node> result = new ObjectOpenHashSet<>();
		// Visit queue
		final ObjectArrayFIFOQueue<Node> queue = new ObjectArrayFIFOQueue<>();
		queue.enqueue(start);
		pl.start("Visiting reachable nodes...");

		while (!queue.isEmpty()) {
			final Node node = queue.dequeue();
			if (result.add(node)) for (final Node s : kb.successors(node))
				if (!result.contains(s)) queue.enqueue(s);
			pl.lightUpdate();
		}

		pl.done();
		return result.size();
	}

	public static int coreaches(final KnowledgeBase kb, final Node start, final ProgressLogger pl) {
		final ObjectOpenHashSet<Node> result = new ObjectOpenHashSet<>();
		// Visit queue
		final ObjectArrayFIFOQueue<Node> queue = new ObjectArrayFIFOQueue<>();
		queue.enqueue(start);

		pl.start("Visiting coreachable nodes...");
		while (!queue.isEmpty()) {
			final Node node = queue.dequeue();
			if (result.add(node)) for (final Node s : kb.predecessors(node)) if (!result.contains(s)) queue.enqueue(s);
			pl.lightUpdate();
		}

		pl.done();
		return result.size();
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
						new FlaggedOption("n", JSAP.INTEGER_PARSER, "0", JSAP.NOT_REQUIRED, 'n', "n", "The the number of samples."),
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
						new UnflaggedOption("kbmeta", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The file containing the knowledge base metadata." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final int n = jsapResult.getInt("n");
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
			reachable.add(reaches(kb, node, pl2));
			coreachable.add(coreaches(kb, node, pl2));
		}

		pl.done();
		LOGGER.info("Closing KnowledgeBase");
		kb.close();
		System.out.println("Reachable sets stats: " + reachable);
		System.out.println("Coreachable sets stats: " + coreachable);
	}
}
