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

import java.io.IOException;
import java.util.Map;

import org.jgrapht.alg.scoring.AlphaCentrality;
import org.jgrapht.alg.scoring.BetweennessCentrality;
import org.jgrapht.alg.scoring.ClusteringCoefficient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.ArrayImmutableDirectedGraph.Builder;
import it.unimi.dsi.fastutil.longs.Long2DoubleFunction;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.law.rank.DominantEigenvectorParallelPowerMethod;
import it.unimi.dsi.law.rank.Salsa;
import it.unimi.dsi.law.rank.SpectralRanking;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandomGenerator;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.Transform;
import it.unimi.dsi.webgraph.algo.GeometricCentralities;

/** Tests multiple implementation of centrality measures on different adapters. */

public class QueryDependentCentralitiesTest {

	private final static double QUERY_NODE_DENSITY = 0.1;
	private static ArrayImmutableDirectedGraph directedGraph, specialDirectedGraph;
	private static ImmutableGraphAdapter immutableGraph, specialImmutableGraph;
	private static LongCollection queryNodes;
	
	/** This is graph test50-.6-7-3-2-10 in law.di.unimi.it/data/it/unimi/dsi/law/rank. Its TRANSPOSE 
	 *  is stored in {{@link #specialDirectedGraph} and {@link #specialImmutableGraph}. */
	private final static int[][] SPECIAL_GRAPH = {{},{5,8,14},{22},{3,4,5,19},{1,2,6,12,14,18},{5,6,13,23},{1,2,4,5,19,24},{2,3,4,18,22},{},{6,8,10,15,24},{4,6,20,21,23,24},{11,14,17,19,23},{4,13,17,23},{13,15,17,20,21},{4,8,14,15,23},{5,7,10,12,16},{29,42,46},{31},{31,34,35,38,42,44},{26,30,36,40,41,45,47},{26,35,37,38,40},{31,35,37,41,44},{29,34,45,49},{27,32,33},{26,31,39,44},{},{33,34,40,45},{26,30,32,46,49},{28,35,44,45,49},{35,39,40,45},{},{26,28,30,33,35,44},{32,34,35,38,39,41},{37,39,42,44,46},{},{25,26},{26,29,32},{},{},{},{27,33,36,42,46,48},{},{27,30,31,34,37},{29,34,42},{34,36,38,41,49},{26,38,40,42,48},{38,44,49},{27,30,34,35,36,41},{},{37,40,41,49}};
	private final static double[] SPECIAL_GRAPH_UNIF_W = {0.00698294242383964232911117263742,0.0123420637440532609642719481379,0.0139379567675208165414191407526,0.0108937592981678703571534796852,0.0204935769676434021501871988959,0.0224194444637510503313198956637,0.0173355147044527293921241575778,0.00938760602039738574792466243944,0.0147010220641572235033742483569,0.00698294242383964232911117263742,0.0105747062324501249438735617878,0.00841318364318029196278454534628,0.0122908627574802010525345156164,0.0172998586847605822267231747403,0.0178474198085279492457085748943,0.0141450799797514318753734694236,0.00938760602039738574792466243944,0.0139659679555541336649910696206,0.011482092184390013210868218429,0.0131839720771717677442305821029,0.0114220017831793756747028669299,0.0114220017831793756747028669299,0.0204260986996998919664646348918,0.0203212686780721186206728200619,0.0121239906019536192226597488959,0.0191477246429359549518243846257,0.0444363265697931983723111238674,0.0242236957996042238828206484418,0.01352064213149832154279955233,0.0217937388022952707138760153275,0.0230820827856224481688250067649,0.0299236979668515144805585115046,0.0264356409873340241379948773521,0.0317645951055282630862124920767,0.0396987250083003669125508984359,0.0286230169861089708769722635018,0.0205832765137726237178739570722,0.0293200187536570324501101786826,0.0316606111690100378311240075115,0.0233354902300546562514970871461,0.0377085878695282442991678880245,0.0290566658155855653448672608472,0.0289703808741958944028465572185,0.00698294242383964232911117263742,0.0320078079989234839214277270347,0.0292967973465345269411607406394,0.0245028235316745923514742450202,0.00858385331892478555519631474991,0.0173054479209336798514906160162,0.0382524696799213911456940842769};

	@BeforeAll
	public static void beforeAll() {
        ArrayImmutableDirectedGraph.Builder builder = new ArrayImmutableDirectedGraph.Builder();
        final XoRoShiRo128PlusPlusRandomGenerator random = new XoRoShiRo128PlusPlusRandomGenerator(0);
        queryNodes = new LongOpenHashSet();
		int n = 50;
		long[] node = new long[n];
		for (int i = 0; i < n; i++) {
			builder.addInternalNode(node[i] = random.nextLong());
			if (random.nextDouble() < QUERY_NODE_DENSITY) queryNodes.add(node[i]);
		}

		for (int i = 0; i < 50 * 10; i++) {
			try {
				builder.addArc(node[random.nextInt(n)], node[random.nextInt(n)]);
			} catch (final IllegalArgumentException ignoreDuplicateArcs) {
			}
		}

		directedGraph = builder.build(true);
		immutableGraph = new ImmutableGraphAdapter(directedGraph, false);
		
		builder = new ArrayImmutableDirectedGraph.Builder();
		n = SPECIAL_GRAPH.length;
		node = new long[n];
		for (int i = 0; i < n; i++) builder.addInternalNode(node[i] = random.nextLong());
		for (int source = 0; source < n; source++)
			for (int target: SPECIAL_GRAPH[source])
				builder.addArc(node[target], node[source]);
		
		specialDirectedGraph = builder.build(true);
		specialImmutableGraph = new ImmutableGraphAdapter(specialDirectedGraph, false);
	}

	@Test
	public void testPath() throws InterruptedException {
		final ArrayImmutableDirectedGraph.Builder builder = new ArrayImmutableDirectedGraph.Builder();
		builder.addInternalNode(0);
		builder.addInternalNode(1);
		builder.addInternalNode(2);
		builder.addArc(0, 1);
		builder.addArc(1, 2);
		final ArrayImmutableDirectedGraph graph = builder.build();

		Long2DoubleFunction closeness = QueryDependentCentralities.closeness(graph, LongSet.of(0, 1, 2));
		assertEquals(0., closeness.get(0), 1E-9);
		assertEquals(1., closeness.get(1), 1E-9);
		assertEquals(1. / 3, closeness.get(2), 1E-9);

		closeness = QueryDependentCentralities.closeness(graph.transpose(), LongSet.of(0, 1, 2));
		assertEquals(1. / 3, closeness.get(0), 1E-9);
		assertEquals(1., closeness.get(1), 1E-9);
		assertEquals(0., closeness.get(2), 1E-9);

		closeness = QueryDependentCentralities.closeness(graph, LongSet.of(0));
		assertEquals(0, closeness.get(0), 1E-9);
		assertEquals(1., closeness.get(1), 1E-9);
		assertEquals(1. / 2, closeness.get(2), 1E-9);

		closeness = QueryDependentCentralities.closeness(graph, LongSet.of(2));
		assertEquals(0, closeness.get(0), 1E-9);
		assertEquals(0, closeness.get(1), 1E-9);
		assertEquals(0, closeness.get(2), 1E-9);

		closeness = QueryDependentCentralities.closeness(graph.transpose(), LongSet.of(2));
		assertEquals(1. / 2, closeness.get(0), 1E-9);
		assertEquals(1., closeness.get(1), 1E-9);
		assertEquals(0, closeness.get(2), 1E-9);

		Long2DoubleFunction harmonic = QueryDependentCentralities.harmonic(graph, LongSet.of(0, 1, 2));
		assertEquals(0., harmonic.get(0), 1E-9);
		assertEquals(1., harmonic.get(1), 1E-9);
		assertEquals(3. / 2, harmonic.get(2), 1E-9);

		harmonic = QueryDependentCentralities.harmonic(graph.transpose(), LongSet.of(0, 1, 2));
		assertEquals(3. / 2, harmonic.get(0), 1E-9);
		assertEquals(1., harmonic.get(1), 1E-9);
		assertEquals(0., harmonic.get(2), 1E-9);

		harmonic = QueryDependentCentralities.harmonic(graph, LongSet.of(0));
		assertEquals(0, harmonic.get(0), 1E-9);
		assertEquals(1., harmonic.get(1), 1E-9);
		assertEquals(1. / 2, harmonic.get(2), 1E-9);

		harmonic = QueryDependentCentralities.harmonic(graph, LongSet.of(2));
		assertEquals(0, harmonic.get(0), 1E-9);
		assertEquals(0, harmonic.get(1), 1E-9);
		assertEquals(0, harmonic.get(2), 1E-9);

		harmonic = QueryDependentCentralities.harmonic(graph.transpose(), LongSet.of(2));
		assertEquals(1. / 2, harmonic.get(0), 1E-9);
		assertEquals(1., harmonic.get(1), 1E-9);
		assertEquals(0, harmonic.get(2), 1E-9);
	}

	@Test
	public void testCloseness() throws InterruptedException {
		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraph.transpose());
		geometricCentralities.compute();

		final Long2DoubleFunction closeness = QueryDependentCentralities.closeness(directedGraph, directedGraph.nodes());

		for (final long id : directedGraph.nodes()) {
			assertEquals(geometricCentralities.closeness[immutableGraph.id2Node(id)], closeness.get(id), 1E-9);
		}
	}

	@Test
	public void testHarmonic() throws InterruptedException {
		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraph.transpose());
		geometricCentralities.compute();

		final Long2DoubleFunction harmonic = QueryDependentCentralities.harmonic(directedGraph, directedGraph.nodes());

		for (final long id : directedGraph.nodes()) {
			assertEquals(geometricCentralities.harmonic[immutableGraph.id2Node(id)], harmonic.get(id), 1E-9);
		}
	}

	@Test
	public void testPageRankUniform() throws IOException {
		LongCollection queryNodes = new LongOpenHashSet();
		Long2DoubleFunction pr;
		int n = specialImmutableGraph.numNodes();

		// Uniform
		for (int i = 0; i < n; i++) queryNodes.add(specialImmutableGraph.node2Id(i));
		pr = Centralities.pageRankParallel(specialDirectedGraph.transpose()/*, queryNodes*/, 0.85);
		for (final long id : specialDirectedGraph.nodes()) {
			//assertEquals(SPECIAL_GRAPH_UNIF_W[specialImmutableGraph.id2Node(id)], pr.get(id), 1E-6);
			System.out.println(id + "\t" + SPECIAL_GRAPH_UNIF_W[specialImmutableGraph.id2Node(id)] + "\t" + pr.get(id));
		}
	}

	@Test
	public void testKatz() throws IOException {
		@SuppressWarnings("deprecation")
		final AlphaCentrality<Long, LongLongPair> alpha = new AlphaCentrality<>(directedGraph, 0.0001, x -> queryNodes.contains(x) ? 1./queryNodes.size() : 0., Integer.MAX_VALUE, 1E-7);
		final Map<Long, Double> scores = alpha.getScores();
		final Long2DoubleFunction pr = QueryDependentCentralities.katzParallel(directedGraph, queryNodes, 0.0001);
		for (final long id : directedGraph.nodes()) assertEquals(scores.get(id), pr.get(id), 1E-6);
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
