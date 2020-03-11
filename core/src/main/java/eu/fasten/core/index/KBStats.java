package eu.fasten.core.index;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;

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
import java.io.ObjectOutputStream;
import java.util.Properties;

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
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.webgraph.ImmutableGraph;


public class KBStats {

	private static final Logger LOGGER = LoggerFactory.getLogger(KBStats.class);

	private static ImmutableGraph[] graph;
	private static Properties[] property;

	public static void main(final String[] args) throws JSAPException, ClassNotFoundException, RocksDBException, IOException {
		final SimpleJSAP jsap = new SimpleJSAP(KBStats.class.getName(),
				"Creates or updates a knowledge base (associated to a given database), indexing either a list of JSON files or a Kafka topic where JSON object are published",
				new Parameter[] {
						new FlaggedOption("gsd", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'g', "gsd", "Graph-size distribution (number of nodes  [int], one per graph, written in binary)." ),
						new FlaggedOption("min", JSAP.INTEGER_PARSER, "0", JSAP.NOT_REQUIRED, 'm', "min", "Consider only graphs with at least this number of nodes." ),
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final int minNodes = jsapResult.getInt("min");
		final String kbDir = jsapResult.getString("kb");
		if (!new File(kbDir).exists()) throw new IllegalArgumentException("No such directory: " + kbDir);
		LOGGER.info("Loading KnowledgeBase metadata");
		final KnowledgeBase kb = KnowledgeBase.getInstance(kbDir);
		LOGGER.info("Number of graphs: " + kb.callGraphs.size());
		
		ProgressLogger pl = new ProgressLogger();
		
		pl.count = kb.callGraphs.size();
		pl.itemsName = "graphs";
		pl.start("Enumerating graphs");
		
		final boolean gsdFlag = jsapResult.userSpecified("gsd");
		DataOutputStream gsdStream = gsdFlag? new DataOutputStream(new BufferedOutputStream(new FileOutputStream(jsapResult.getString("gsd")))) : null;
		
		final StatsAccumulator nodes = new StatsAccumulator();
		final StatsAccumulator arcs = new StatsAccumulator();
		final StatsAccumulator bitsPerLink = new StatsAccumulator();
		final StatsAccumulator bitsPerLinkt = new StatsAccumulator();
		int totGraphs = 0, statGraphs = 0;
		for(final CallGraph callGraph: kb.callGraphs.values()) {
			pl.update();
			graph = callGraph.graphs();
			totGraphs++;
			if (graph[0].numNodes() < minNodes) continue;
			if (gsdFlag) gsdStream.writeInt(graph[0].numNodes());
			statGraphs++;
			nodes.add(graph[0].numNodes());
			arcs.add(graph[0].numArcs());
			property = callGraph.graphProperties();
			final double bpl = Double.parseDouble((property[0].getProperty("bitsperlink")));
			if (! Double.isNaN(bpl)) bitsPerLink.add(bpl);
			final double bplt = Double.parseDouble((property[1].getProperty("bitsperlink")));
			if (! Double.isNaN(bplt)) bitsPerLinkt.add(bplt);
		}

		if (gsdFlag) gsdStream.close();
		pl.done();
		LOGGER.info("Closing KnowledgeBase");
		kb.close();
		System.out.println("Graphs in the kb: " + totGraphs);
		System.out.println("Graphs considered for the stats: " + statGraphs);
		System.out.println("Nodes: " + nodes.snapshot());
		System.out.println("Arcs: " + arcs.snapshot());
		System.out.println("Bits/link: " + bitsPerLink.snapshot());
		System.out.println("Transpose bits/link: " + bitsPerLinkt.snapshot());
	}

}
