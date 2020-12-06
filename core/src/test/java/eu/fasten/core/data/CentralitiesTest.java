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
import org.jgrapht.alg.scoring.HarmonicCentrality;
import org.jgrapht.alg.scoring.PageRank;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.law.rank.KatzParallelGaussSeidel;
import it.unimi.dsi.law.rank.PageRankParallelGaussSeidel;
import it.unimi.dsi.law.rank.PageRankParallelPowerSeries;
import it.unimi.dsi.law.rank.SpectralRanking;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandomGenerator;
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
		final int n = 200;
		final long[] node = new long[n];
		for (int i = 0; i < n; i++) builder.addInternalNode(node[i] = random.nextLong());

		for (int i = 0; i < 200 * 10; i++) {
			try {
				builder.addArc(node[random.nextInt(n)], node[random.nextInt(n)]);
			} catch (final IllegalArgumentException ignoreDuplicateArcs) {
			}
		}

		directedGraph = builder.build(true);
		immutableGraph = new ImmutableGraphAdapter(directedGraph, false);
	}

	@Test
	public void testHarmonic() throws IOException, InterruptedException {
		final HyperBall hyperBall = new HyperBall(immutableGraph, immutableGraph.transpose(), 12, null, 0, 0, 0, false, false, true, null, 0);
		hyperBall.run();
		final HarmonicCentrality<Long, LongLongPair> harmonicCentrality = new HarmonicCentrality<>(directedGraph, false, false);
		final Map<Long, Double> scores = harmonicCentrality.getScores();
		for (final long id : directedGraph.nodes()) {
			final double exact = scores.get(id);
			final double approx = hyperBall.sumOfInverseDistances[immutableGraph.id2Node(id)];
			assertTrue(exact == approx || Math.abs(exact - approx) / exact < 0.01);
		}

		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraph);
		geometricCentralities.compute();
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), geometricCentralities.harmonic[immutableGraph.id2Node(id)], 1E-9);
	}

	@Test
	public void testCloseness() throws IOException, InterruptedException {
		final HyperBall hyperBall = new HyperBall(immutableGraph, immutableGraph.transpose(), 13, null, 0, 0, 0, false, true, false, null, 0);
		hyperBall.run();
		final ClosenessCentrality<Long, LongLongPair> harmonicCentrality = new ClosenessCentrality<>(directedGraph, false, false);
		final Map<Long, Double> scores = harmonicCentrality.getScores();
		for (final long id : directedGraph.nodes()) {
			final double exact = scores.get(id);
			if (exact != 0) { // Relative error not meaningful
				final double approx = hyperBall.sumOfDistances[immutableGraph.id2Node(id)] == 0 ? 0 : 1. / hyperBall.sumOfDistances[immutableGraph.id2Node(id)];
				assertTrue(exact == approx || Math.abs(exact - approx) / exact < 0.01);
			}
		}

		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraph);
		geometricCentralities.compute();
		for (final long id : directedGraph.nodes()) {
			if (scores.get(id) != 0) { // Relative error not meaningful
				assertEquals(scores.get(id), geometricCentralities.closeness[immutableGraph.id2Node(id)], 1E-9);
			}
		}
	}

	@Test
	public void testPageRank() throws IOException {
		final PageRank<Long, LongLongPair> pageRank = new PageRank<>(directedGraph, 0.5, Integer.MAX_VALUE, 1E-9);
		final Map<Long, Double> scores = pageRank.getScores();
		final PageRankParallelPowerSeries pageRankParallelPowerSeries = new PageRankParallelPowerSeries(immutableGraph.transpose());
		pageRankParallelPowerSeries.alpha = 0.5;
		pageRankParallelPowerSeries.init();
		pageRankParallelPowerSeries.stepUntil(new SpectralRanking.NormStoppingCriterion(1E-9));
		for (final long id : directedGraph.nodes())
			assertEquals(scores.get(id), pageRankParallelPowerSeries.rank[immutableGraph.id2Node(id)], 1E-8);

		final PageRankParallelGaussSeidel pageRankParallelGaussSeidel = new PageRankParallelGaussSeidel(immutableGraph.transpose());
		pageRankParallelGaussSeidel.alpha = 0.5;
		pageRankParallelGaussSeidel.init();
		pageRankParallelGaussSeidel.stepUntil(new SpectralRanking.NormStoppingCriterion(1E-9));
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), pageRankParallelGaussSeidel.rank[immutableGraph.id2Node(id)], 1E-8);
	}

	@Test
	public void testKatz() throws IOException {
		final AlphaCentrality<Long, LongLongPair> alpha = new AlphaCentrality<>(directedGraph, 0.0001, 1, Integer.MAX_VALUE, 1E-9);
		final Map<Long, Double> scores = alpha.getScores();
		final KatzParallelGaussSeidel katzParallelGaussSeidel = new KatzParallelGaussSeidel(immutableGraph.transpose());
		katzParallelGaussSeidel.alpha = 0.0001;
		katzParallelGaussSeidel.init();
		katzParallelGaussSeidel.stepUntil(new SpectralRanking.NormStoppingCriterion(1E-9));
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), katzParallelGaussSeidel.rank[immutableGraph.id2Node(id)], 1E-8);
	}

	@Test
	public void testBetweenness() throws IOException, InterruptedException {
		final BetweennessCentrality<Long, LongLongPair> betweennessCentrality0 = new BetweennessCentrality<>(directedGraph);
		final Map<Long, Double> scores = betweennessCentrality0.getScores();
		final it.unimi.dsi.webgraph.algo.BetweennessCentrality betweennessCentrality1 = new it.unimi.dsi.webgraph.algo.BetweennessCentrality(immutableGraph);
		betweennessCentrality1.compute();
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), betweennessCentrality1.betweenness[immutableGraph.id2Node(id)], 1E-8);
	}

}
