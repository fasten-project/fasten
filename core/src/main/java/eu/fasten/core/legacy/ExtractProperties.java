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
import it.unimi.dsi.logging.ProgressLogger;


public class ExtractProperties {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtractProperties.class);

	public static void main(final String[] args) throws JSAPException, ClassNotFoundException, RocksDBException, IOException {
		final SimpleJSAP jsap = new SimpleJSAP(ExtractProperties.class.getName(),
				"Extract properties files from a knowledge base.",
				new Parameter[] {
						new FlaggedOption("min", JSAP.INTEGER_PARSER, "0", JSAP.NOT_REQUIRED, 'm', "min", "Consider only graphs with at least this number of internal nodes."),
						new FlaggedOption("n", JSAP.LONG_PARSER, Long.toString(Long.MAX_VALUE), JSAP.NOT_REQUIRED, 'n', "n", "Analyze just this number of graphs."),
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
						new UnflaggedOption("kbmeta", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The file containing the knowledge base metadata." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final int minNodes = jsapResult.getInt("min");
		final long n = jsapResult.getLong("n");
		final String kbDir = jsapResult.getString("kb");
		if (!new File(kbDir).exists()) throw new IllegalArgumentException("No such directory: " + kbDir);
		final String kbMetadataFilename = jsapResult.getString("kbmeta");
		if (!new File(kbMetadataFilename).exists()) throw new IllegalArgumentException("No such file: " + kbMetadataFilename);
		LOGGER.info("Loading KnowledgeBase metadata");
		final KnowledgeBase kb = KnowledgeBase.getInstance(kbDir, kbMetadataFilename, true);

		final ProgressLogger pl = new ProgressLogger();

		pl.count = kb.callGraphs.size();
		pl.itemsName = "graphs";
		pl.start("Enumerating graphs");

		long i = 0;
		for(final CallGraph callGraph: kb.callGraphs.values()) {
			if (i++ >= n) break;
			pl.update();
			if (callGraph.nInternal < minNodes) continue;
			final CallGraphData callGraphData = callGraph.callGraphData();
			System.out.print(callGraph.index);
			System.out.print('\t');
			System.out.print(callGraph.product);
			System.out.print('\t');
			System.out.print(callGraph.version);
			System.out.print('\t');
			System.out.print(callGraphData.graphProperties);
			System.out.print('\t');
			System.out.print(callGraphData.transposeProperties);
			System.out.println();
		}

		LOGGER.info("Closing KnowledgeBase");
		kb.close();
	}

}
