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
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jgrapht.alg.scoring.AlphaCentrality;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.scoring.ClusteringCoefficient;

import it.unimi.dsi.fastutil.longs.Long2DoubleFunction;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.law.rank.DominantEigenvectorParallelPowerMethod;
import it.unimi.dsi.law.rank.KatzParallelGaussSeidel;
import it.unimi.dsi.law.rank.LeftSingularVectorParallelPowerMethod;
import it.unimi.dsi.law.rank.PageRank;
import it.unimi.dsi.law.rank.PageRankParallelGaussSeidel;
import it.unimi.dsi.law.rank.Salsa;
import it.unimi.dsi.law.rank.SpectralRanking;
import it.unimi.dsi.law.rank.SpectralRanking.StoppingCriterion;
import it.unimi.dsi.law.util.Norm;
import it.unimi.dsi.webgraph.algo.HyperBall;

/**
 * A containers for static utility methods computing query-dependent centrality measures on instances of
 * {@link DirectedGraph}.
 *
 * <p>
 * {@linkplain Centralities Query-<em>independent</em>} centrality measure associate a score
 * with each node of a graph. Query-<em>dependent</em> centrality measure use additional
 * information specific to a query to make the centrality dependent on a set of <em>query nodes</em>.
 *
 * <p>
 * The influence of the query nodes on the final result very depending on the type of centrality
 * considered. For example, in the case of PageRank it is customary to set the {@link PageRank#preference <em>preference vector</em>}
 * to a probability distribution concentrated on the query nodes (in the methods provided
 * by this class, the preference vector is set to the uniform distribution on the query nodes).
 *
 * <p>
 * In the case of <em>geometric centralities</em> such as closeness of harmonic centrality,
 * we consider a weight on the node that is zero outside of the query nodes and one on the
 * query nodes. The methods of this class assume that the query nodes are in relatively
 * small number, and thus perform a number breadth-first visit from the query nodes,
 * accumulating the results, rather than use the {@link HyperBall} approximation algorithm.
 *
 * <p>
 * We provide a method for each implementation part of D5.3. Many implementation provide a wide
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

public class QueryDependentCentralities {


	private static final StoppingCriterion DEFAULT_STOPPING_CRITERION = SpectralRanking.or(new SpectralRanking.IterationNumberStoppingCriterion(1000), new SpectralRanking.NormStoppingCriterion(1E-7));

	/**
	 * Computes query-dependent closeness centrality using parallel breadth-first visits.
	 *
	 * @param graph a directed graph.
	 * @param queryNodes the query nodes (breadth-first visits will start form these nodes).
	 * the query nodes) or the <em>positive</em> version of closeness (distances <em>from</em> the query nodes).
	 * @return a function mapping node identifiers to their query-dependent closeness score.
	 */
	public static Long2DoubleFunction closeness(final DirectedGraph graph, final LongCollection queryNodes) throws InterruptedException {
		final Long2LongOpenHashMap sumOfDistances = new Long2LongOpenHashMap();
		final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		final ExecutorCompletionService<Void> executorCompletionService = new ExecutorCompletionService<>(executorService);
		for(final long node: queryNodes) executorCompletionService.submit(() ->{
			final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
			queue.enqueue(node);
			final LongOpenHashSet seen = new LongOpenHashSet();
			seen.add(node);
			int d = -1;
			long sentinel = queue.firstLong();

			while (!queue.isEmpty()) {
				final long gid = queue.dequeueLong();
				if (gid == sentinel) {
					d++;
					sentinel = -1;
				}

				synchronized(sumOfDistances) {
					sumOfDistances.addTo(gid, d);
				}

				// Note that we are reversing the computation
				final LongIterator iterator = graph.successors(gid).iterator();

				while (iterator.hasNext()) {
					final long x = iterator.nextLong();
					if (seen.add(x)) {
						if (sentinel == -1) sentinel = x;
						queue.enqueue(x);
					}
				}
			}

			return null;
		});

		for (final Long queryNode : queryNodes) executorCompletionService.take();

		final Long2DoubleOpenHashMap result = new Long2DoubleOpenHashMap();
		for(final Entry e: sumOfDistances.long2LongEntrySet()) {
			final long s = e.getLongValue();
			if (s != 0) result.put(e.getLongKey(), 1. / s);
		}
		return result;
	}

	/**
	 * Computes query-dependent harmonic centrality using parallel breadth-first visits.
	 *
	 * @param graph a directed graph.
	 * @param queryNodes the query nodes (breadth-first visits will start form these nodes). the query
	 *            nodes) or the <em>positive</em> version of harmonic centrality (distances
	 *            <em>from</em> the query nodes).
	 * @return a function mapping node identifiers to their query-dependent harmonic score.
	 */
	public static Long2DoubleFunction harmonic(final DirectedGraph graph, final LongCollection queryNodes) throws InterruptedException {
		final Long2DoubleOpenHashMap result = new Long2DoubleOpenHashMap();
		final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		final ExecutorCompletionService<Void> executorCompletionService = new ExecutorCompletionService<>(executorService);
		for (final long node : queryNodes) executorCompletionService.submit(() -> {
			final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
			queue.enqueue(node);
			final LongOpenHashSet seen = new LongOpenHashSet();
			seen.add(node);
			int d = -1;
			long sentinel = queue.firstLong();

			while (!queue.isEmpty()) {
				final long gid = queue.dequeueLong();
				if (gid == sentinel) {
					d++;
					sentinel = -1;
				}

				synchronized (result) {
					if (gid != node) result.addTo(gid, 1. / d);
				}

				// Note that we are reversing the computation
				final LongIterator iterator = graph.successors(gid).iterator();

				while (iterator.hasNext()) {
					final long x = iterator.nextLong();
					if (seen.add(x)) {
						if (sentinel == -1) sentinel = x;
						queue.enqueue(x);
					}
				}
			}

			return null;
		});

		for (final Long queryNode : queryNodes) executorCompletionService.take();
		return result;
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
	 * Approximates Seely's centrality using a parallel implementation of the power method.
	 *
	 * @param directedGraph a directed graph.
	 * @return a function mapping node identifiers to their centrality score.
	 */
	public static Long2DoubleFunction seelyCentralityParallel(final DirectedGraph directedGraph) throws IOException {
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
		final org.jgrapht.alg.scoring.PageRank<Long, LongLongPair> pageRank = new org.jgrapht.alg.scoring.PageRank<>(directedGraph, alpha, 1000, 1E-7);
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
