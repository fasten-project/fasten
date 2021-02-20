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

package eu.fasten.core.examples;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;

import org.jgrapht.alg.scoring.HarmonicCentrality;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.ClosestFirstIterator;
import org.jooq.DSLContext;
import org.rocksdb.RocksDBException;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandom;

public class CallGraphAnalysisExample {

	private static String getCallableName(final long id, final DSLContext context) {
		return FastenURI.create(context.select(Callables.CALLABLES.FASTEN_URI).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(id)).fetchOne().component1()).getPath();
	}

	public static void main(final String args[]) throws JSAPException, IllegalArgumentException, SQLException, RocksDBException {
		final SimpleJSAP jsap = new SimpleJSAP(CallGraphAnalysisExample.class.getName(), "Analyzes a revision call graph and print some statistics.", new Parameter[] {
				new FlaggedOption("postgres", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'p', "postgres", "The URI of the Postgres server."),
				new FlaggedOption("db", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'd', "db", "The Postgres database."),
				new FlaggedOption("rocksdb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'r', "rocksdb", "The path to the RocksDB graph database."),
				new UnflaggedOption("group", JSAP.STRING_PARSER, JSAP.NOT_REQUIRED, "The Maven group of the revision."),
				new UnflaggedOption("product", JSAP.STRING_PARSER, JSAP.NOT_REQUIRED, "The product associated with the revision."),
				new UnflaggedOption("version", JSAP.STRING_PARSER, JSAP.NOT_REQUIRED, "The version of the revision."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String group = jsapResult.getString("group");
		final String product = jsapResult.getString("product");
		final String version = jsapResult.getString("version");

		// Connect to the Postgres database (you'll need to set the password as a system variable)
		final var context = PostgresConnector.getDSLContext(jsapResult.getString("postgres"), jsapResult.getString("db"), true);
		// Connect to the graph database
		final var rocksDao = new eu.fasten.core.data.graphdb.RocksDao(jsapResult.getString("rocksdb"), true);

		// Retrieve the ID of the requested revision
		final var packageName = group + Constants.mvnCoordinateSeparator + product;
		final var result = context.select(PackageVersions.PACKAGE_VERSIONS.ID).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName)).and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version)).fetchOne();
		if (result == null) throw new IllegalArgumentException("The requested revision (group=" + group + ", product=" + product + ", version=" + version + ") is not in the Postgres database");
		final long id = result.component1();

		// Retrieve the associated revision call graph
		final DirectedGraph graph = rocksDao.getGraphData(id);
		if (graph == null) throw new IllegalArgumentException("The requested revision (group=" + group + ", product=" + product + ", version=" + version + ", id=" + id + ") is not in the RocksDB database");

		// Find node with highest outdegree
		long bestDegree = -1;
		long bestDNode = -1;
		for (final long v : graph.nodes()) {
			final long d = graph.outDegreeOf(v);
			if (d > bestDegree) {
				bestDegree = d;
				bestDNode = v;
			}
		}

		System.out.println("The callable with highest outdegree (" + bestDegree + ") is " + getCallableName(bestDNode, context) + " (id=" + bestDNode + ")");

		// Find node with highest indegre
		bestDegree = -1;
		bestDNode = -1;
		for (final long v : graph.nodes()) {
			final long d = graph.inDegreeOf(v);
			if (d > bestDegree) {
				bestDegree = d;
				bestDNode = v;
			}
		}

		System.out.println("The callable with highest indegree (" + bestDegree + ") is " + getCallableName(bestDNode, context) + " (id=" + bestDNode + ")");

		// Now we compute PageRank
		final PageRank<Long, LongLongPair> pageRank = new PageRank<>(graph);

		// Find node with highest PageRank
		double bestPR = -1;
		long bestPRNode = -1;
		for (final long v : graph.nodes()) {
			final double pr = pageRank.getVertexScore(v);
			if (pr > bestPR) {
				bestPR = pr;
				bestPRNode = v;
			}
		}

		System.out.println("The callable with highest PageRank (" + bestPR + ") is " + getCallableName(bestPRNode, context) + " (id=" + bestPRNode + ")");

		// We will need the transpose now. This is just a lightweight view.
		final EdgeReversedGraph<Long, LongLongPair> transpose = new EdgeReversedGraph<>(graph);

		// Now we compute PageRank on the transpose
		final PageRank<Long, LongLongPair> transposePageRank = new PageRank<>(transpose);

		// Find node with highest transpose PageRank
		bestPR = -1;
		bestPRNode = -1;
		for (final long v : graph.nodes()) {
			final double pr = transposePageRank.getVertexScore(v);
			if (pr > bestPR) {
				bestPR = pr;
				bestPRNode = v;
			}
		}

		System.out.println("The callable with highest transpose PageRank (" + bestPR + ") is " + getCallableName(bestPRNode, context) + " (id=" + bestPRNode + ")");

		// Now we compute positive harmonic centrality (outgoing paths)
		final HarmonicCentrality<Long, LongLongPair> positiveHarmonicCentrality = new HarmonicCentrality<>(graph, false, false);
		final Map<Long, Double> positiveHarmonicCentralityScores = positiveHarmonicCentrality.getScores();

		// Find node with highest positive harmonic centrality
		double bestH = -1;
		long bestHNode = -1;
		for (final long v : graph.nodes()) {
			final double h = positiveHarmonicCentralityScores.get(v);
			if (h > bestH) {
				bestH = h;
				bestHNode = v;
			}
		}

		System.out.println("The callable with highest positive harmonic centrality (" + bestH + ") is " + getCallableName(bestHNode, context) + " (id=" + bestHNode + ")");

		// Now we compute negative harmonic centrality (incoming paths)
		final HarmonicCentrality<Long, LongLongPair> negativeHarmonicCentrality = new HarmonicCentrality<>(graph, true, false);
		final Map<Long, Double> negativeHarmonicCentralityScores = negativeHarmonicCentrality.getScores();

		// Find node with highest harmonic centrality
		bestH = -1;
		bestHNode = -1;
		for (final long v : graph.nodes()) {
			final double h = negativeHarmonicCentralityScores.get(v);
			if (h > bestH) {
				bestH = h;
				bestHNode = v;
			}
		}

		System.out.println("The callable with highest negative harmonic centrality (" + bestH + ") is " + getCallableName(bestHNode, context) + " (id=" + bestHNode + ")");

		// We now explore balls around node using a radius-based limit.

		// We choose 10 random nodes (very inefficient, should be a Knuth-Yates shuffle)
		final long[] node = Arrays.copyOf(LongArrays.shuffle(graph.nodes().toLongArray(), new XoRoShiRo128PlusPlusRandom(0)), 10);

		for (final long v : node) {
			System.out.println();
			System.out.println();
			System.out.println("========= " + getCallableName(v, context) + " (id=" + v + ")" + " =========");
			System.out.println();
			// Now we find reachable nodes in a radius of 3
			System.out.println("Finding nodes reachable within distance 3 from " + getCallableName(v, context) + " (id=" + v + ")");
			final ClosestFirstIterator<Long, LongLongPair> reachable = new ClosestFirstIterator<>(graph, v, 3);
			reachable.forEachRemaining((x) -> {
				System.out.println("\tFound node " + getCallableName(x, context) + " (id=" + x + ") at distance " + reachable.getShortestPathLength(x));
			});

			System.out.println();
			// Now we find coreachable nodes in a radius of 3
			// Note that we're using JGraphT methods here.
			System.out.println("Finding nodes coreachable within distance 3 from " + getCallableName(v, context) + " (id=" + v + ")");
			final ClosestFirstIterator<Long, LongLongPair> coreachable = new ClosestFirstIterator<>(transpose, v, 3);
			coreachable.forEachRemaining((x) -> {
				System.out.println("\tFound node " + getCallableName(x, context) + " (id=" + x + ") at distance " + coreachable.getShortestPathLength(x));
			});
		}

		rocksDao.close();
	}
}
