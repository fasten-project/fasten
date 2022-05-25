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

package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;

import org.jgrapht.alg.scoring.AlphaCentrality;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.scoring.ClosenessCentrality;
import org.jgrapht.alg.scoring.ClusteringCoefficient;
import org.jgrapht.alg.scoring.HarmonicCentrality;
import org.jgrapht.alg.scoring.PageRank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.ArrayImmutableDirectedGraph.Builder;
import it.unimi.dsi.fastutil.longs.Long2DoubleFunction;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.law.rank.DominantEigenvectorParallelPowerMethod;
import it.unimi.dsi.law.rank.KatzParallelGaussSeidel;
import it.unimi.dsi.law.rank.PageRankParallelGaussSeidel;
import it.unimi.dsi.law.rank.PageRankParallelPowerSeries;
import it.unimi.dsi.law.rank.Salsa;
import it.unimi.dsi.law.rank.SpectralRanking;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandomGenerator;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.Transform;
import it.unimi.dsi.webgraph.algo.GeometricCentralities;
import it.unimi.dsi.webgraph.algo.HyperBall;

/** Tests multiple implementation of centrality measures on different adapters. */

public class CentralitiesTest {

	private static ArrayImmutableDirectedGraph directedGraph;
	private static ImmutableGraphAdapter immutableGraph;

	@BeforeAll
	public static void beforeAll() {
        final ArrayImmutableDirectedGraph.Builder builder = new ArrayImmutableDirectedGraph.Builder();
        final XoRoShiRo128PlusPlusRandomGenerator random = new XoRoShiRo128PlusPlusRandomGenerator(0);
		final int n = 50;
		final long[] node = new long[n];
		for (int i = 0; i < n; i++) builder.addInternalNode(node[i] = random.nextLong());

		for (int i = 0; i < 50 * 10; i++) {
			try {
				builder.addArc(node[random.nextInt(n)], node[random.nextInt(n)]);
			} catch (final IllegalArgumentException ignoreDuplicateArcs) {
			}
		}

		directedGraph = builder.build(true);
		immutableGraph = new ImmutableGraphAdapter(directedGraph, false);
	}

	@Test
	public void testLin() throws IOException, InterruptedException {
		final Long2DoubleFunction linExactParallel = Centralities.linExactParallel(directedGraph);
		final Long2DoubleFunction linApproximateParallel = Centralities.linApproximateParallel(directedGraph, 0.01);
		for (final long id : directedGraph.nodes()) {
			final double exact = linExactParallel.get(id);
			if (exact != 0) { // Relative error not meaningful
				final double approx = linApproximateParallel.get(id);
				assertTrue(exact == approx || Math.abs(exact - approx) / exact < 0.01, exact + " != " + approx);
			}
		}
	}

	@Test
	public void testHarmonic() throws IOException, InterruptedException {
		final HyperBall hyperBall = new HyperBall(immutableGraph.transpose(), immutableGraph, 12, null, 0, 0, 0, false, false, true, null, 0);
		hyperBall.run();
		final HarmonicCentrality<Long, LongLongPair> harmonicCentrality = new HarmonicCentrality<>(directedGraph.transpose(), false, false);
		final Map<Long, Double> scores = harmonicCentrality.getScores();
		for (final long id : directedGraph.nodes()) {
			final double exact = scores.get(id);
			final double approx = hyperBall.sumOfInverseDistances[immutableGraph.id2Node(id)];
			assertTrue(exact == approx || Math.abs(exact - approx) / exact < 0.05);
		}

		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraph.transpose());
		geometricCentralities.compute();
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), geometricCentralities.harmonic[immutableGraph.id2Node(id)], 1E-7);

		final Long2DoubleFunction harmonicExact = Centralities.harmonicExact(directedGraph);
		for (final long id : directedGraph.nodes()) {
			if (scores.get(id) != 0) { // Relative error not meaningful
				assertEquals(harmonicExact.get(id), geometricCentralities.harmonic[immutableGraph.id2Node(id)], 1E-7);
			}
		}
		final Long2DoubleFunction harmonicExactParallel = Centralities.harmonicExactParallel(directedGraph);
		for (final long id : directedGraph.nodes()) {
			if (scores.get(id) != 0) { // Relative error not meaningful
				assertEquals(harmonicExactParallel.get(id), geometricCentralities.harmonic[immutableGraph.id2Node(id)], 1E-7);
			}
		}
		final Long2DoubleFunction harmonicApproximateParallel = Centralities.harmonicApproximateParallel(directedGraph, 0.01);
		for (final long id : directedGraph.nodes()) {
			final double exact = scores.get(id);
			if (exact != 0) { // Relative error not meaningful
				final double approx = harmonicApproximateParallel.get(id);
				assertTrue(exact == approx || Math.abs(exact - approx) / exact < 0.01, exact + " != " + approx);
			}
		}
	}

	@Test
	public void testCloseness() throws IOException, InterruptedException {
		final HyperBall hyperBall = new HyperBall(immutableGraph.transpose(), immutableGraph, 12, null, 0, 0, 0, false, true, false, null, 0);
		hyperBall.run();
		final ClosenessCentrality<Long, LongLongPair> harmonicCentrality = new ClosenessCentrality<>(directedGraph.transpose(), false, false);
		final Map<Long, Double> scores = harmonicCentrality.getScores();
		for (final long id : directedGraph.nodes()) {
			final double exact = scores.get(id);
			if (exact != 0) { // Relative error not meaningful
				final double approx = hyperBall.sumOfDistances[immutableGraph.id2Node(id)] == 0 ? 0 : 1. / hyperBall.sumOfDistances[immutableGraph.id2Node(id)];
				assertTrue(exact == approx || Math.abs(exact - approx) / exact < 0.05);
			}
		}

		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraph.transpose());
		geometricCentralities.compute();
		for (final long id : directedGraph.nodes()) {
			if (scores.get(id) != 0) { // Relative error not meaningful
				assertEquals(scores.get(id), geometricCentralities.closeness[immutableGraph.id2Node(id)], 1E-7);
			}
		}

		final Long2DoubleFunction closenessExact = Centralities.closenessExact(directedGraph);
		for (final long id : directedGraph.nodes()) {
			if (scores.get(id) != 0) { // Relative error not meaningful
				assertEquals(closenessExact.get(id), geometricCentralities.closeness[immutableGraph.id2Node(id)], 1E-7);
			}
		}
		final Long2DoubleFunction closenessExactParallel = Centralities.closenessExactParallel(directedGraph);
		for (final long id : directedGraph.nodes()) {
			if (scores.get(id) != 0) { // Relative error not meaningful
				assertEquals(closenessExactParallel.get(id), geometricCentralities.closeness[immutableGraph.id2Node(id)], 1E-7);
			}
		}
		final Long2DoubleFunction closenessApproximateParallel = Centralities.closenessApproximateParallel(directedGraph, 0.01);
		for (final long id : directedGraph.nodes()) {
			final double exact = scores.get(id);
			if (exact != 0) { // Relative error not meaningful
				final double approx = closenessApproximateParallel.get(id);
				assertTrue(exact == approx || Math.abs(exact - approx) / exact < 0.01);
			}
		}

	}

	@Test
	public void testPageRank() throws IOException {
		final PageRank<Long, LongLongPair> pageRank = new PageRank<>(directedGraph, 0.5, Integer.MAX_VALUE, 1E-7);
		final Map<Long, Double> scores = pageRank.getScores();
		final PageRankParallelPowerSeries pageRankParallelPowerSeries = new PageRankParallelPowerSeries(immutableGraph.transpose());
		pageRankParallelPowerSeries.alpha = 0.5;
		pageRankParallelPowerSeries.init();
		pageRankParallelPowerSeries.stepUntil(new SpectralRanking.NormStoppingCriterion(1E-7));
		for (final long id : directedGraph.nodes())
			assertEquals(scores.get(id), pageRankParallelPowerSeries.rank[immutableGraph.id2Node(id)], 1E-6);

		final PageRankParallelGaussSeidel pageRankParallelGaussSeidel = new PageRankParallelGaussSeidel(immutableGraph.transpose());
		pageRankParallelGaussSeidel.alpha = 0.5;
		pageRankParallelGaussSeidel.init();
		pageRankParallelGaussSeidel.stepUntil(new SpectralRanking.NormStoppingCriterion(1E-7));
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), pageRankParallelGaussSeidel.rank[immutableGraph.id2Node(id)], 1E-6);

		final Long2DoubleFunction pr = Centralities.pageRank(directedGraph, 0.5);
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), pr.get(id), 1E-6);
		final Long2DoubleFunction prp = Centralities.pageRankParallel(directedGraph, 0.5);
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), prp.get(id), 1E-6);
	}

	@Test
	public void testKatz() throws IOException {
		final AlphaCentrality<Long, LongLongPair> alpha = new AlphaCentrality<>(directedGraph, 0.0001, 1, Integer.MAX_VALUE, 1E-7);
		final Map<Long, Double> scores = alpha.getScores();
		final KatzParallelGaussSeidel katzParallelGaussSeidel = new KatzParallelGaussSeidel(immutableGraph.transpose());
		katzParallelGaussSeidel.alpha = 0.0001;
		katzParallelGaussSeidel.init();
		katzParallelGaussSeidel.stepUntil(new SpectralRanking.NormStoppingCriterion(1E-7));
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), katzParallelGaussSeidel.rank[immutableGraph.id2Node(id)], 1E-6);

		final Long2DoubleFunction pr = Centralities.katz(directedGraph, 0.0001);
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), pr.get(id), 1E-6);
		final Long2DoubleFunction prp = Centralities.katzParallel(directedGraph, 0.0001);
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), prp.get(id), 1E-6);
	}

	@Test
	public void testEigenvector() throws IOException {
		// We exploit the fact that the left singular vector and the left dominant eigenvector of a
		// symmetric matrix are the same
		final ImmutableGraph symmetric = Transform.symmetrize(immutableGraph);
		final Builder builder = new ArrayImmutableDirectedGraph.Builder();
		for (int x = 0; x < symmetric.numNodes(); x++) builder.addInternalNode(x);
		for(int x = 0; x < symmetric.numNodes(); x++) {
			final LazyIntIterator successors = symmetric.successors(x);
			for(int s; (s = successors.nextInt()) != -1; ) builder.addArc(x, s);
		}
		final ArrayImmutableDirectedGraph graph = builder.build();

		final Long2DoubleFunction eigenvectorCentralityParallel = Centralities.eigenvectorCentralityParallel(graph);
		final Long2DoubleFunction hits = Centralities.hitsParallel(graph);
		for (final long id : graph.nodes()) assertEquals(hits.get(id), eigenvectorCentralityParallel.get(id), 1E-6);

		final double[] salsa0 = Salsa.rank(immutableGraph, null);
		final Long2DoubleFunction salsa1 = Centralities.salsa(directedGraph);
		for (final long id : directedGraph.nodes()) assertEquals(salsa0[immutableGraph.id2Node(id)], salsa1.get(id), 1E-6);
	}

	@Test
	public void testSeeley() throws IOException {
		final DominantEigenvectorParallelPowerMethod dominantEigenvectorParallelPowerMethod = new DominantEigenvectorParallelPowerMethod(immutableGraph.transpose());
		dominantEigenvectorParallelPowerMethod.markovian = true;
		dominantEigenvectorParallelPowerMethod.stepUntil(new SpectralRanking.NormStoppingCriterion(1E-7));
		final Long2DoubleFunction seeley = Centralities.seeleyCentralityParallel(directedGraph);
		for (final long id : directedGraph.nodes()) assertEquals(dominantEigenvectorParallelPowerMethod.rank[immutableGraph.id2Node(id)], seeley.get(id));
	}

	@Test
	public void testBetweenness() throws InterruptedException {
		final BetweennessCentrality<Long, LongLongPair> betweennessCentrality0 = new BetweennessCentrality<>(directedGraph);
		final Map<Long, Double> scores = betweennessCentrality0.getScores();
		final it.unimi.dsi.webgraph.algo.BetweennessCentrality betweennessCentrality1 = new it.unimi.dsi.webgraph.algo.BetweennessCentrality(immutableGraph);
		betweennessCentrality1.compute();
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), betweennessCentrality1.betweenness[immutableGraph.id2Node(id)], 1E-6);

		final Long2DoubleFunction b = Centralities.betweenness(directedGraph);
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), b.get(id), 1E-6);
		final Long2DoubleFunction bp = Centralities.betweennessParallel(directedGraph);
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), bp.get(id), 1E-6);
	}

	@Test
	public void testClustering() {
		final ClusteringCoefficient<Long, LongLongPair> clustering = new ClusteringCoefficient<>(directedGraph);
		final Map<Long, Double> clustering0 = clustering.getScores();
		final Long2DoubleFunction clustering1 = Centralities.localClusteringCoefficient(directedGraph);
		for (final long id : directedGraph.nodes()) assertEquals(clustering0.get(id), clustering1.get(id));
	}

}
