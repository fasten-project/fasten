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
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.index.LayeredLabelPropagation;
import eu.fasten.core.legacy.KnowledgeBase.CallGraph;
import eu.fasten.core.legacy.KnowledgeBase.CallGraphData;
import it.unimi.dsi.Util;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.Properties;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.EFGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.Transform;


public class RecompressGraphs {

	private static final Logger LOGGER = LoggerFactory.getLogger(RecompressGraphs.class);

	public static void main(final String[] args) throws Exception {
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
						new Switch("llp", 'l', "llp", "Apply Layered Label Propagation before recompression."),
						new Switch("eliasFano", 'e', "elias-fano", "Recompress as Elias-Fano."),
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
		final boolean ef = jsapResult.getBoolean("eliasFano");
		final boolean llp = jsapResult.getBoolean("llp");

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
			final int nInternal = callGraph.nInternal;
			if (nInternal < minNodes) continue;
			final CallGraphData callGraphData = callGraph.callGraphData();

			ImmutableGraph graph = callGraphData.rawGraph();
			ImmutableGraph transpose = callGraphData.rawTranspose();
			final int numNodes = graph.numNodes();

			if (llp) {
				final ImmutableGraph symGraph = new ArrayListMutableGraph(Transform.symmetrize(graph)).immutableView();
				final LayeredLabelPropagation clustering = new LayeredLabelPropagation(symGraph, 0);
				final int[] perm = clustering.computePermutation(LayeredLabelPropagation.DEFAULT_GAMMAS, null);

				Util.invertPermutationInPlace(perm);
				final int[] sorted = new int[numNodes];
				int internal = 0, external = nInternal;
				for (int j = 0; j < numNodes; j++) {
					if (perm[j] < nInternal) sorted[internal++] = perm[j];
					else sorted[external++] = perm[j];
				}
				Util.invertPermutationInPlace(sorted);

				graph = new ArrayListMutableGraph(Transform.map(graph, sorted)).immutableView();
				transpose = new ArrayListMutableGraph(Transform.map(transpose, sorted)).immutableView();
			}

			System.out.print(callGraph.index);
			System.out.print('\t');
			System.out.print(callGraph.product);
			System.out.print('\t');
			System.out.print(callGraph.version);
			System.out.print('\t');
			System.out.print(numNodes);
			System.out.print('\t');
			System.out.print(graph.numArcs());

			if (ef) {
				EFGraph.store(graph, f, null);
			} else {
				BVGraph.store(graph, f, windowSize, maxRefCount, minIntervalLength, zetaK, flags, 1, null);
			}
			Properties properties = new Properties(f + BVGraph.PROPERTIES_EXTENSION);
			System.out.print('\t');
			System.out.print(callGraphData.graphProperties.get("bitsperlink"));
			System.out.print('\t');
			System.out.print(properties.getString("bitsperlink"));

			if (ef) {
				EFGraph.store(transpose, f, null);
			} else {
				BVGraph.store(transpose, f, windowSize, maxRefCount, minIntervalLength, zetaK, flags, 1, null);
			}
			properties = new Properties(f + BVGraph.PROPERTIES_EXTENSION);
			System.out.print('\t');
			System.out.print(callGraphData.transposeProperties.get("bitsperlink"));
			System.out.print('\t');
			System.out.print(properties.getString("bitsperlink"));
			System.out.println();
		}

		LOGGER.info("Closing KnowledgeBase");
		kb.close();
		new File(f.toString());
		new File(f.toString() + BVGraph.PROPERTIES_EXTENSION).delete();
		new File(f.toString() + BVGraph.OFFSETS_EXTENSION).delete();
		new File(f.toString() + BVGraph.GRAPH_EXTENSION).delete();
	}

}
