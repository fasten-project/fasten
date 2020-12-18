package eu.fasten.core.legacy;

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

import eu.fasten.core.legacy.KnowledgeBase.CallGraph;
import eu.fasten.core.legacy.KnowledgeBase.CallGraphData;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.stat.SummaryStats;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;


public class VisitStats {

	private static final Logger LOGGER = LoggerFactory.getLogger(VisitStats.class);

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
		final SimpleJSAP jsap = new SimpleJSAP(VisitStats.class.getName(),
				"Computes (co)reachable set statistics for revisions call graphs of a prototype knowledge base.",
				new Parameter[] {
						new FlaggedOption("min", JSAP.INTEGER_PARSER, "0", JSAP.NOT_REQUIRED, 'm', "min", "Consider only graphs with at least this number of internal nodes." ),
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
						new UnflaggedOption("kbmeta", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The file containing the knowledge base metadata." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final int minNodes = jsapResult.getInt("min");
		final String kbDir = jsapResult.getString("kb");
		if (!new File(kbDir).exists()) throw new IllegalArgumentException("No such directory: " + kbDir);
		final String kbMetadataFilename = jsapResult.getString("kbmeta");
		if (!new File(kbMetadataFilename).exists()) throw new IllegalArgumentException("No such file: " + kbMetadataFilename);
		LOGGER.info("Loading KnowledgeBase metadata");
		final KnowledgeBase kb = KnowledgeBase.getInstance(kbDir, kbMetadataFilename, true);
		LOGGER.info("Number of graphs: " + kb.callGraphs.size());

		final ProgressLogger pl = new ProgressLogger();

		pl.count = kb.callGraphs.size();
		pl.itemsName = "graphs";
		pl.start("Enumerating graphs");

		final SummaryStats reachable = new SummaryStats();
		final SummaryStats coreachable = new SummaryStats();
		final SummaryStats size = new SummaryStats();

		long totGraphs = 0;
		long statGraphs = 0;

		for(final CallGraph callGraph: kb.callGraphs.values()) {
			pl.update();
			totGraphs++;
			if (callGraph.nInternal < minNodes) continue;
			statGraphs++;
			final CallGraphData callGraphData = callGraph.callGraphData();
			size.add(callGraphData.numNodes());
			final ImmutableGraph graph = new ArrayListMutableGraph(callGraphData.rawGraph()).immutableView();
			final ImmutableGraph transpose = new ArrayListMutableGraph(callGraphData.rawTranspose()).immutableView();

			for (int startNode = 0; startNode < callGraphData.numNodes(); startNode++) {
				reachable.add(reachable(graph, startNode));
				coreachable.add(reachable(transpose, startNode));
			}

			System.out.println("Size stats: " + size);
			System.out.println("Reachable sets stats: " + reachable);
			System.out.println("Coreachable sets stats: " + coreachable);

		}

		pl.done();
		LOGGER.info("Closing KnowledgeBase");
		kb.close();
		System.out.println("Graphs in the kb: " + totGraphs);
		System.out.println("Graphs considered for the stats: " + statGraphs);
		System.out.println("Size stats: " + size);
		System.out.println("Reachable sets stats: " + reachable);
		System.out.println("Coreachable sets stats: " + coreachable);
	}
}
