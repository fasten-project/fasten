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

import org.apache.commons.configuration.ConfigurationException;
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
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.Properties;
import it.unimi.dsi.webgraph.BVGraph;


public class RecompressGraphs {

	private static final Logger LOGGER = LoggerFactory.getLogger(RecompressGraphs.class);

	public static void main(final String[] args) throws JSAPException, ClassNotFoundException, RocksDBException, IOException, ConfigurationException {
		final SimpleJSAP jsap = new SimpleJSAP(RecompressGraphs.class.getName(),
				"Extract properties files from a knowledge base.",
				new Parameter[] {
						new FlaggedOption("comp", JSAP.STRING_PARSER, null, JSAP.NOT_REQUIRED, 'c', "comp", "A compression flag (may be specified several times).").setAllowMultipleDeclarations(true),
						new FlaggedOption("windowSize", JSAP.INTEGER_PARSER, String.valueOf(BVGraph.DEFAULT_WINDOW_SIZE), JSAP.NOT_REQUIRED, 'w', "window-size", "Reference window size (0 to disable)."),
						new FlaggedOption("maxRefCount", JSAP.INTEGER_PARSER, String.valueOf(BVGraph.DEFAULT_MAX_REF_COUNT), JSAP.NOT_REQUIRED, 'm', "max-ref-count", "Maximum number of backward references (-1 for ∞)."),
						new FlaggedOption("minIntervalLength", JSAP.INTEGER_PARSER, String.valueOf(BVGraph.DEFAULT_MIN_INTERVAL_LENGTH), JSAP.NOT_REQUIRED, 'i', "min-interval-length", "Minimum length of an interval (0 to disable)."),
						new FlaggedOption("zetaK", JSAP.INTEGER_PARSER, String.valueOf(BVGraph.DEFAULT_ZETA_K), JSAP.NOT_REQUIRED, 'k', "zeta-k", "The k parameter for zeta-k codes."),
						new FlaggedOption("min", JSAP.INTEGER_PARSER, "0", JSAP.NOT_REQUIRED, 'M', "min", "Consider only graphs with at least this number of internal nodes."),
						new FlaggedOption("n", JSAP.LONG_PARSER, Long.toString(Long.MAX_VALUE), JSAP.NOT_REQUIRED, 'n', "n", "Analyze just this number of graphs."),
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
						new UnflaggedOption("kbmeta", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The file containing the knowledge base metadata." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		int flags = 0;
		for (final String compressionFlag : jsapResult.getStringArray("comp")) try {
			flags |= BVGraph.class.getField(compressionFlag).getInt(BVGraph.class);
		} catch (final Exception notFound) {
			throw new JSAPException("Compression method " + compressionFlag + " unknown.");
		}

		final int windowSize = jsapResult.getInt("windowSize");
		final int zetaK = jsapResult.getInt("zetaK");
		int maxRefCount = jsapResult.getInt("maxRefCount");
		if (maxRefCount == -1) maxRefCount = Integer.MAX_VALUE;
		final int minIntervalLength = jsapResult.getInt("minIntervalLength");

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
		final String f = File.createTempFile(RecompressGraphs.class.getSimpleName(), ".tmpgraph").toString();

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

			BVGraph.store(callGraphData.rawGraph(), f);
			BVGraph.store(callGraphData.rawGraph(), f, windowSize, maxRefCount, minIntervalLength, zetaK, flags, 1, null);
			Properties properties = new Properties(f + BVGraph.PROPERTIES_EXTENSION);
			System.out.print('\t');
			System.out.print(callGraphData.graphProperties.get("bitsperlink"));
			System.out.print('\t');
			System.out.print(properties.getString("bitsperlink"));

			BVGraph.store(callGraphData.rawTranspose(), f, windowSize, maxRefCount, minIntervalLength, zetaK, flags, 1, null);
			properties = new Properties(f + BVGraph.PROPERTIES_EXTENSION);
			System.out.print('\t');
			System.out.print(callGraphData.transposeProperties.get("bitsperlink"));
			System.out.print('\t');
			System.out.print(properties.getString("bitsperlink"));
			System.out.println();
		}

		LOGGER.info("Closing KnowledgeBase");
		kb.close();
		new File(f.toString() + BVGraph.PROPERTIES_EXTENSION).delete();
		new File(f.toString() + BVGraph.OFFSETS_EXTENSION).delete();
		new File(f.toString() + BVGraph.GRAPH_EXTENSION).delete();
	}

}
