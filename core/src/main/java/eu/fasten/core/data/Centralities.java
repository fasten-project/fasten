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

import java.io.IOException;
import java.util.Map;

import org.jgrapht.alg.scoring.AlphaCentrality;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.scoring.ClosenessCentrality;
import org.jgrapht.alg.scoring.ClusteringCoefficient;
import org.jgrapht.alg.scoring.HarmonicCentrality;
import org.jgrapht.alg.scoring.PageRank;

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.fastutil.longs.Long2DoubleFunction;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.law.rank.DominantEigenvectorParallelPowerMethod;
import it.unimi.dsi.law.rank.KatzParallelGaussSeidel;
import it.unimi.dsi.law.rank.LeftSingularVectorParallelPowerMethod;
import it.unimi.dsi.law.rank.PageRankParallelGaussSeidel;
import it.unimi.dsi.law.rank.Salsa;
import it.unimi.dsi.law.rank.SpectralRanking;
import it.unimi.dsi.law.rank.SpectralRanking.StoppingCriterion;
import it.unimi.dsi.law.util.Norm;
import it.unimi.dsi.webgraph.algo.GeometricCentralities;
import it.unimi.dsi.webgraph.algo.HyperBall;

/**
 * A containers for static utility methods computing centrality measures on instances of
 * {@link DirectedGraph}.
 *
 * <p>
 * We provide a method for each implementation part of D5.2. Many implementation provide a wide
 * number of options, that should be explored, if necessary, by looking at the code in this class
 * and at the related Javadoc documentation.
 *
 * <p>
 * All centralities are implemented in their <em>negative</em> form, which is the most commonly
 * used: geometric centralities use <em>incoming paths</em>, PageRank is based on <em>incoming
 * arcs</em>, etc. To obtain the positive version, it is sufficient the pass the transpose.
 *
 * <p>
 * Iterative processes stop with a threshold of 10<sup>&minus;7</sup> or after a thousand
 * iterations. The second condition is useful in case damping / attenuation factors out of range or
 * provided to {@link #pageRank(DirectedGraph, double)}, {@link #katz(DirectedGraph, double)}, etc.
 *
 * <p>
 * All methods return uniformly a {@link Long2DoubleFunction} mapping node identifiers to the
 * associated centrality score.
 *
 * <p>
 * Javadoc documentation consistently uses &ldquo;compute&rdquo; for exact computations, and
 * &ldquo;approximates&rdquo; for approximated computations; the latter can be of iterative type
 * (e.g., {@link #pageRank(DirectedGraph, double)}) or of statistical type (e.g.,
 * {@link #harmonicApproximateParallel(DirectedGraph, double)}).
 */

public class Centralities {


	private static final StoppingCriterion DEFAULT_STOPPING_CRITERION = SpectralRanking.or(new SpectralRanking.IterationNumberStoppingCriterion(1000), new SpectralRanking.NormStoppingCriterion(1E-7));

	/**
	 * Computes closeness centrality using all-pairs shortest-paths.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction closenessExact(final DirectedGraph directedGraph) throws IOException, InterruptedException {
		final ClosenessCentrality<Long, LongLongPair> harmonicCentrality = new ClosenessCentrality<>(directedGraph.transpose(), false, false);
		return id -> harmonicCentrality.getVertexScore(id);
	}

	/**
	 * Computes closeness centrality using a parallel implementation of all-pairs shortest-paths.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction closenessExactParallel(final DirectedGraph directedGraph) throws IOException, InterruptedException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraphAdapter.transpose());
		geometricCentralities.compute();
		return id -> geometricCentralities.closeness[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Approximates closeness centrality using {@linkplain HyperBall HyperBall}.
	 *
	 * @param directedGraph a directed graph.
	 * @param eps the desired relative error with probability 90%; the number of counters per register
	 *            will be approximately 3.16 / {@code eps}.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction closenessApproximateParallel(final DirectedGraph directedGraph, final double eps) throws IOException, InterruptedException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final HyperBall hyperBall = new HyperBall(immutableGraphAdapter.transpose(), immutableGraphAdapter, Fast.approximateLog2((3 * 1.06 / eps) * (3 * 1.06 / eps)), null, 0, 0, 0, false, true, false, null, 0);
		hyperBall.run();
		hyperBall.close();
		return id -> 1. / hyperBall.sumOfDistances[immutableGraphAdapter.id2Node(id)];
	}


	/**
	 * Computes Lin's centrality using a parallel implementation of all-pairs shortest-paths.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction linExactParallel(final DirectedGraph directedGraph) throws IOException, InterruptedException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraphAdapter.transpose());
		geometricCentralities.compute();
		return id -> geometricCentralities.lin[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Approximates Lin's centrality using {@linkplain HyperBall HyperBall}.
	 *
	 * @param directedGraph a directed graph.
	 * @param eps the desired relative error with probability 90%; the number of counters per register
	 *            will be approximately 3.16 / {@code eps}.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction linApproximateParallel(final DirectedGraph directedGraph, final double eps) throws IOException, InterruptedException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final HyperBall hyperBall = new HyperBall(immutableGraphAdapter.transpose(), immutableGraphAdapter, Fast.approximateLog2((3 * 1.06 / eps) * (3 * 1.06 / eps)), null, 0, 0, 0, false, true, false, null, 0);
		hyperBall.run();
		hyperBall.close();

		return id -> {
			final int x = immutableGraphAdapter.id2Node(id);
			if (hyperBall.sumOfDistances[x] == 0) return 1;
			final double count = hyperBall.count(x);
			return count * count / hyperBall.sumOfDistances[x];
		};
	}

	/**
	 * Computes harmonic centrality using all-pairs shortest-paths.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction harmonicExact(final DirectedGraph directedGraph) throws IOException, InterruptedException {
		final ClosenessCentrality<Long, LongLongPair> harmonicCentrality = new HarmonicCentrality<>(directedGraph.transpose(), false, false);
		return id -> harmonicCentrality.getVertexScore(id);
	}

	/**
	 * Computes harmonic centrality using a parallel implementation of all-pairs shortest-paths.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction harmonicExactParallel(final DirectedGraph directedGraph) throws IOException, InterruptedException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraphAdapter.transpose());
		geometricCentralities.compute();
		return id -> geometricCentralities.harmonic[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Approximates harmonic centrality using {@linkplain HyperBall HyperBall}.
	 *
	 * @param directedGraph a directed graph.
	 * @param eps the desired relative error with probability 90%; the number of counters per register
	 *            will be approximately 3.16 / {@code eps}.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction harmonicApproximateParallel(final DirectedGraph directedGraph, final double eps) throws IOException, InterruptedException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final HyperBall hyperBall = new HyperBall(immutableGraphAdapter.transpose(), immutableGraphAdapter, Fast.approximateLog2((3 * 1.06 / eps) * (3 * 1.06 / eps)), null, 0, 0, 0, false, false, true, null, 0);
		hyperBall.run();
		hyperBall.close();
		return id -> hyperBall.sumOfInverseDistances[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Approximates left dominant eigenvector centrality using a parallel implementation of the power
	 * method.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction eigenvectorCentralityParallel(final DirectedGraph directedGraph) throws IOException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final DominantEigenvectorParallelPowerMethod dominantEigenvectorParallelPowerMethod = new DominantEigenvectorParallelPowerMethod(immutableGraphAdapter.transpose());
		dominantEigenvectorParallelPowerMethod.stepUntil(DEFAULT_STOPPING_CRITERION);
		return id -> dominantEigenvectorParallelPowerMethod.rank[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Approximates Seeley's centrality using a parallel implementation of the power method.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction seeleyCentralityParallel(final DirectedGraph directedGraph) throws IOException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final DominantEigenvectorParallelPowerMethod dominantEigenvectorParallelPowerMethod = new DominantEigenvectorParallelPowerMethod(immutableGraphAdapter.transpose());
		dominantEigenvectorParallelPowerMethod.markovian = true;
		dominantEigenvectorParallelPowerMethod.stepUntil(DEFAULT_STOPPING_CRITERION);
		return id -> dominantEigenvectorParallelPowerMethod.rank[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Approximates Katz centrality.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction katz(final DirectedGraph directedGraph, final double alpha) {
		final AlphaCentrality<Long, LongLongPair> katz = new AlphaCentrality<>(directedGraph, alpha, 1, 1000, 1E-7);
		final Map<Long, Double> scores = katz.getScores();
		return id -> scores.get(id);
	}

	/**
	 * Approximates Katz centrality using a parallel implementation of the Gauss&ndash;Seidel method.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction katzParallel(final DirectedGraph directedGraph, final double alpha) throws IOException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final KatzParallelGaussSeidel katzParallelGaussSeidel = new KatzParallelGaussSeidel(immutableGraphAdapter.transpose());
		katzParallelGaussSeidel.alpha = alpha;
		katzParallelGaussSeidel.stepUntil(DEFAULT_STOPPING_CRITERION);
		return id -> katzParallelGaussSeidel.rank[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Approximates PageRank.
	 *
	 * @param directedGraph a directed graph.
	 * @param alpha the damping factor.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction pageRank(final DirectedGraph directedGraph, final double alpha) {
		final PageRank<Long, LongLongPair> pageRank = new PageRank<>(directedGraph, alpha, 1000, 1E-7);
		final Map<Long, Double> scores = pageRank.getScores();
		return id -> scores.get(id);
	}

	/**
	 * Approximates PageRank using a parallel implementation of the Gauss&ndash;Seidel method.
	 *
	 * @param directedGraph a directed graph.
	 * @param alpha the damping factor.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction pageRankParallel(final DirectedGraph directedGraph, final double alpha) throws IOException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final PageRankParallelGaussSeidel pageRankParallelGaussSeidel = new PageRankParallelGaussSeidel(immutableGraphAdapter.transpose());
		pageRankParallelGaussSeidel.alpha = alpha;
		pageRankParallelGaussSeidel.stepUntil(DEFAULT_STOPPING_CRITERION);
		return id -> pageRankParallelGaussSeidel.rank[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Approximates HITS using a parallel implementation of the power method.
	 *
	 * <p>
	 * Note that the returned score is the authoritativeness score. To obtain the hubbiness score, pass
	 * the transpose.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction hitsParallel(final DirectedGraph directedGraph) throws IOException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final LeftSingularVectorParallelPowerMethod leftSingularVectorParallelPowerMethod = new LeftSingularVectorParallelPowerMethod(immutableGraphAdapter, immutableGraphAdapter.transpose());
		leftSingularVectorParallelPowerMethod.norm = Norm.L_2;
		leftSingularVectorParallelPowerMethod.stepUntil(DEFAULT_STOPPING_CRITERION);
		return id -> leftSingularVectorParallelPowerMethod.rank[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Computes SALSA using the a non-iterative algorithm.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction salsa(final DirectedGraph directedGraph) throws IOException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final double[] salsa = Salsa.rank(immutableGraphAdapter, null);
		return id -> salsa[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Computes betweenness centrality.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction betweenness(final DirectedGraph directedGraph) {
		final BetweennessCentrality<Long, LongLongPair> betweennessCentrality = new BetweennessCentrality<>(directedGraph);
		final Map<Long, Double> scores = betweennessCentrality.getScores();
		return id -> scores.get(id);
	}

	/**
	 * Computes betweenness centrality using a parallel breadth-first visit implementation.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction betweennessParallel(final DirectedGraph directedGraph) throws InterruptedException {
		final ImmutableGraphAdapter immutableGraphAdapter = new ImmutableGraphAdapter(directedGraph);
		final it.unimi.dsi.webgraph.algo.BetweennessCentrality betweennessCentrality = new it.unimi.dsi.webgraph.algo.BetweennessCentrality(immutableGraphAdapter);
		betweennessCentrality.compute();
		return id -> betweennessCentrality.betweenness[immutableGraphAdapter.id2Node(id)];
	}

	/**
	 * Computes the local clustering coefficient.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their local clustering coefficient.
	 */
	public static Long2DoubleFunction localClusteringCoefficient(final DirectedGraph directedGraph) {
		final ClusteringCoefficient<Long, LongLongPair> clusteringCoefficient = new ClusteringCoefficient<>(directedGraph);
		final Map<Long, Double> scores = clusteringCoefficient.getScores();
		return id -> scores.get(id);
	}

}
