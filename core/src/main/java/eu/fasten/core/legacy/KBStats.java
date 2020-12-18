package eu.fasten.core.legacy;

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

import eu.fasten.core.legacy.KnowledgeBase.CallGraph;
import eu.fasten.core.legacy.KnowledgeBase.CallGraphData;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.logging.ProgressLogger;


public class KBStats {

	private static final Logger LOGGER = LoggerFactory.getLogger(KBStats.class);

	public static void main(final String[] args) throws JSAPException, ClassNotFoundException, RocksDBException, IOException {
		final SimpleJSAP jsap = new SimpleJSAP(KBStats.class.getName(),
				"Compute statistics about an instance of a prototype knowledge base.",
				new Parameter[] {
						new FlaggedOption("gsd", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'g', "gsd", "Graph-size distribution: number of nodes (one per graph)." ),
						new FlaggedOption("at", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'a', "at", "Arc type distribution: internal arcs, external arcs, total arcs (tab-separated, one per graph)." ),
						new FlaggedOption("od", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "od", "Outdegree distribution: graph id, internal outdegree, external outdegree, total outdegree (tab-separated, one per node)." ),
						new FlaggedOption("id", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'i', "id", "Indegree distribution: graph id, external?, indegree (tab-separated, one per node)." ),
						new FlaggedOption("min", JSAP.INTEGER_PARSER, "0", JSAP.NOT_REQUIRED, 'm', "min", "Consider only graphs with at least this number of nodes." ),
						new FlaggedOption("n", JSAP.INTEGER_PARSER, Integer.toString(Integer.MAX_VALUE), JSAP.NOT_REQUIRED, 'n', "n", "Analyze just this number of graphs."),
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
						new UnflaggedOption("kbmeta", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The file containing the knowledge base metadata." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final int minNodes = jsapResult.getInt("min");
		final int n = jsapResult.getInt("n");
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
		final StatsAccumulator bytes = new StatsAccumulator();
		final StatsAccumulator rawBytes = new StatsAccumulator();
		final StatsAccumulator deflation = new StatsAccumulator();

		final StatsAccumulator[] deflationBySize = new StatsAccumulator[30];
		for (int i = 0; i < deflationBySize.length; i++) deflationBySize[i] = new StatsAccumulator();

		int totGraphs = 0, statGraphs = 0;
		long totSize = 0;
		for(final CallGraph callGraph: kb.callGraphs.values()) {
			if (totGraphs++ == n) break;

			if (totGraphs % 10000 == 0) {
				System.out.println("Nodes: " + nodes.snapshot());
				System.out.println("Internal nodes: " + internalNodes.snapshot());
				System.out.println("Internal node ratio: " + internalNodeRatio.snapshot());
				System.out.println("Arcs: " + arcs.snapshot());
				System.out.println("Bytes: " + bytes.snapshot());
				System.out.println("Raw bytes: " + rawBytes.snapshot());
				System.out.println("Deflation: " + deflation.snapshot());
				System.out.println("Bits/link: " + bitsPerLink.snapshot());
				System.out.println("Transpose bits/link: " + bitsPerLinkt.snapshot());
				for (int i = 0; i < deflationBySize.length; i++) if (deflationBySize[i].count() != 0) System.out.println("Deflation by size [" + ((1L << i) - 1) + ".." + ((1L << i + 1) - 1) + "):" + deflationBySize[i].snapshot());
			}

			pl.update();
			final CallGraphData callGraphData = callGraph.callGraphData();
			if (callGraphData.numNodes() < minNodes) continue;
			totSize += callGraphData.size;
			if (gsdFlag) gsdStream.println(callGraphData.numNodes());
			statGraphs++;
			nodes.add(callGraphData.numNodes());
			arcs.add(callGraphData.numArcs());
			final long b = callGraphData.size;// + callGraphData.numNodes() * 6;
			bytes.add(b);
			final long r = callGraphData.numArcs() * 16 + callGraphData.numNodes() * 2 * 8 + callGraphData.numNodes() * (8 + 8) * 3 / 2;
			rawBytes.add(r);
			deflation.add((double)b / r);
			deflationBySize[Fast.ceilLog2(callGraphData.numNodes() + 1)].add((double)b / r);
			internalNodes.add(callGraph.nInternal);
			internalNodeRatio.add(((double)callGraph.nInternal)/callGraphData.numNodes());
			final double bpl = Double.parseDouble((callGraphData.graphProperties.getProperty("bitsperlink")));
			if (! Double.isNaN(bpl)) bitsPerLink.add(bpl);
			final double bplt = Double.parseDouble((callGraphData.transposeProperties.getProperty("bitsperlink")));
			if (! Double.isNaN(bplt)) bitsPerLinkt.add(bplt);
			int internalArcs = 0, externalArcs = 0, totalArcs = 0;
			if (atFlag || odFlag) {
				for (final long node: callGraphData.nodes()) {
					int internalOut = 0, externalOut = 0, totalOut = 0;
					if (callGraphData.isExternal(node)) continue;
					for (final long successor: callGraphData.successors(node)) {
						if (callGraphData.isExternal(successor)) externalOut++;
						else internalOut++;
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
				for (final long node: callGraphData.nodes()) {
					final int external = callGraphData.isExternal(node) ? 1 : 0;
					idStream.printf("%d\t%d\t%d\n", totGraphs, external, callGraphData.predecessors(node).size());
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
		System.out.println("Graphs in the kb: " + kb.callGraphs.size());
		System.out.println("Graphs examined: " + totGraphs);
		System.out.println("Graphs considered for the stats: " + statGraphs);
		System.out.println("Storage size of graphs considered for the stats: " + totSize);
		System.out.println("Nodes: " + nodes.snapshot());
		System.out.println("Internal nodes: " + internalNodes.snapshot());
		System.out.println("Internal node ratio: " + internalNodeRatio.snapshot());
		System.out.println("Arcs: " + arcs.snapshot());
		System.out.println("Bytes: " + bytes.snapshot());
		System.out.println("Raw bytes: " + rawBytes.snapshot());
		System.out.println("deflation: " + deflation.snapshot());
		System.out.println("Bits/link: " + bitsPerLink.snapshot());
		System.out.println("Transpose bits/link: " + bitsPerLinkt.snapshot());
		for (int i = 0; i < deflationBySize.length; i++) if (deflationBySize[i].count() != 0) System.out.println("Deflation by size [" + ((1L << i) - 1) + ".." + ((1L << i + 1) - 1) + "): " + deflationBySize[i].snapshot());
	}
}
