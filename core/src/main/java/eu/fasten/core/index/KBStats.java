package eu.fasten.core.index;

import java.io.BufferedOutputStream;

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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.math.StatsAccumulator;
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
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;


public class KBStats {

	private static final Logger LOGGER = LoggerFactory.getLogger(KBStats.class);

	public static void main(final String[] args) throws JSAPException, ClassNotFoundException, RocksDBException, IOException {
		final SimpleJSAP jsap = new SimpleJSAP(KBStats.class.getName(),
				"Creates or updates a knowledge base (associated to a given database), indexing either a list of JSON files or a Kafka topic where JSON object are published",
				new Parameter[] {
						new FlaggedOption("gsd", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'g', "gsd", "Graph-size distribution: number of nodes (one per graph)." ),
						new FlaggedOption("at", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'a', "at", "Arc type distribution: internal arcs, external arcs, total arcs (tab-separated, one per graph)." ),
						new FlaggedOption("od", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "od", "Outdegree distribution: graph id, internal outdegree, external outdegree, total outdegree (tab-separated, one per node)." ),
						new FlaggedOption("id", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'i', "id", "Indegree distribution: graph id, external?, indegree (tab-separated, one per node)." ),
						new FlaggedOption("min", JSAP.INTEGER_PARSER, "0", JSAP.NOT_REQUIRED, 'm', "min", "Consider only graphs with at least this number of nodes." ),
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
		final KnowledgeBase kb = KnowledgeBase.getInstance(kbDir, kbMetadataFilename);
		LOGGER.info("Number of graphs: " + kb.callGraphs.size());

		final ProgressLogger pl = new ProgressLogger();

		pl.count = kb.callGraphs.size();
		pl.itemsName = "graphs";
		pl.start("Enumerating graphs");

		final boolean gsdFlag = jsapResult.userSpecified("gsd");
		final PrintStream gsdStream = gsdFlag? new PrintStream(new BufferedOutputStream(new FileOutputStream(jsapResult.getString("gsd")))) : null;

		final boolean atFlag = jsapResult.userSpecified("at");
		final PrintStream atStream = atFlag? new PrintStream(new BufferedOutputStream(new FileOutputStream(jsapResult.getString("at")))) : null;

		final boolean odFlag = jsapResult.userSpecified("od");
		final PrintStream odStream = odFlag? new PrintStream(new BufferedOutputStream(new FileOutputStream(jsapResult.getString("od")))) : null;

		final boolean idFlag = jsapResult.userSpecified("id");
		final PrintStream idStream = idFlag? new PrintStream(new BufferedOutputStream(new FileOutputStream(jsapResult.getString("id")))) : null;

		final StatsAccumulator nodes = new StatsAccumulator();
		final StatsAccumulator internalNodes = new StatsAccumulator();
		final StatsAccumulator internalNodeRatio = new StatsAccumulator();
		final StatsAccumulator arcs = new StatsAccumulator();
		final StatsAccumulator bitsPerLink = new StatsAccumulator();
		final StatsAccumulator bitsPerLinkt = new StatsAccumulator();
		int totGraphs = 0, statGraphs = 0;
		for(final CallGraph callGraph: kb.callGraphs.values()) {
			pl.update();
			final CallGraphData callGraphData = callGraph.callGraphData();
			totGraphs++;
			final ImmutableGraph graph = callGraphData.graph;
			if (graph.numNodes() < minNodes) continue;
			if (gsdFlag) gsdStream.println(graph.numNodes());
			statGraphs++;
			nodes.add(graph.numNodes());
			arcs.add(graph.numArcs());
			internalNodes.add(callGraph.nInternal);
			internalNodeRatio.add(((double)callGraph.nInternal)/graph.numNodes());
			final double bpl = Double.parseDouble((callGraphData.graphProperties.getProperty("bitsperlink")));
			if (! Double.isNaN(bpl)) bitsPerLink.add(bpl);
			final double bplt = Double.parseDouble((callGraphData.transposeProperties.getProperty("bitsperlink")));
			if (! Double.isNaN(bplt)) bitsPerLinkt.add(bplt);
			int internalArcs = 0, externalArcs = 0, totalArcs = 0;
			if (atFlag || odFlag) {
				final NodeIterator nodeIterator = graph.nodeIterator();
				while (nodeIterator.hasNext()) {
					int internalOut = 0, externalOut = 0, totalOut = 0;
					if (nodeIterator.nextInt() >= callGraph.nInternal) break;
					final LazyIntIterator successors = nodeIterator.successors();
					int called;
					while ((called = successors.nextInt()) >= 0) {
						if (called < callGraph.nInternal) internalOut++;
						else externalOut++;
						totalOut++;
					}
					if (odFlag) odStream.printf("%d\t%d\t%d\t%d\n", totGraphs, internalOut, externalOut, totalOut);
					internalArcs += internalOut;
					externalArcs += externalOut;
					totalArcs += totalOut;
				}
				if (atFlag) atStream.printf("%d\t%d\t%d\n", internalArcs, externalArcs, totalArcs);
			}
			if (idFlag) {
				ImmutableGraph transpose = callGraphData.transpose;
				final NodeIterator nodeIterator = transpose.nodeIterator();
				while (nodeIterator.hasNext()) {
					int external = nodeIterator.nextInt() < callGraph.nInternal? 0 : 1; 
					idStream.printf("%d\t%d\t%d\n", totGraphs, external, nodeIterator.outdegree());
				}
				
			}
			
		}

		if (gsdFlag) gsdStream.close();
		if (atFlag) atStream.close();
		if (odFlag) odStream.close();
		if (idFlag) idStream.close();
		pl.done();
		LOGGER.info("Closing KnowledgeBase");
		kb.close();
		System.out.println("Graphs in the kb: " + totGraphs);
		System.out.println("Graphs considered for the stats: " + statGraphs);
		System.out.println("Nodes: " + nodes.snapshot());
		System.out.println("Internal nodes: " + internalNodes.snapshot());
		System.out.println("Internal node ratio: " + internalNodeRatio.snapshot());
		System.out.println("Arcs: " + arcs.snapshot());
		System.out.println("Bits/link: " + bitsPerLink.snapshot());
		System.out.println("Transpose bits/link: " + bitsPerLinkt.snapshot());
	}

}
