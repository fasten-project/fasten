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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.longs.Long2DoubleFunction;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.law.rank.PageRankParallelGaussSeidel;
import it.unimi.dsi.law.rank.SpectralRanking;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandomGenerator;
import it.unimi.dsi.webgraph.algo.GeometricCentralities;

/** Tests multiple implementation of centrality measures on different adapters. */

public class QueryDependentCentralitiesTest {

	private final static double QUERY_NODE_DENSITY = 0.1;
	private static ArrayImmutableDirectedGraph directedGraph, specialDirectedGraph;
	private static ImmutableGraphAdapter immutableGraph, specialImmutableGraph;
	private static LongSet queryNodes;
	private static long queryNode;

	/** This is graph test50-.6-7-3-2-10 in law.di.unimi.it/data/it/unimi/dsi/law/rank. Its TRANSPOSE
	 *  is stored in {{@link #specialDirectedGraph} and {@link #specialImmutableGraph}. */
	private final static int[][] SPECIAL_GRAPH = {{},{5,8,14},{22},{3,4,5,19},{1,2,6,12,14,18},{5,6,13,23},{1,2,4,5,19,24},{2,3,4,18,22},{},{6,8,10,15,24},{4,6,20,21,23,24},{11,14,17,19,23},{4,13,17,23},{13,15,17,20,21},{4,8,14,15,23},{5,7,10,12,16},{29,42,46},{31},{31,34,35,38,42,44},{26,30,36,40,41,45,47},{26,35,37,38,40},{31,35,37,41,44},{29,34,45,49},{27,32,33},{26,31,39,44},{},{33,34,40,45},{26,30,32,46,49},{28,35,44,45,49},{35,39,40,45},{},{26,28,30,33,35,44},{32,34,35,38,39,41},{37,39,42,44,46},{},{25,26},{26,29,32},{},{},{},{27,33,36,42,46,48},{},{27,30,31,34,37},{29,34,42},{34,36,38,41,49},{26,38,40,42,48},{38,44,49},{27,30,34,35,36,41},{},{37,40,41,49}};
	private final static double[] SPECIAL_GRAPH_UNIF_W = {0.00698294242383964232911117263742,0.0123420637440532609642719481379,0.0139379567675208165414191407526,0.0108937592981678703571534796852,0.0204935769676434021501871988959,0.0224194444637510503313198956637,0.0173355147044527293921241575778,0.00938760602039738574792466243944,0.0147010220641572235033742483569,0.00698294242383964232911117263742,0.0105747062324501249438735617878,0.00841318364318029196278454534628,0.0122908627574802010525345156164,0.0172998586847605822267231747403,0.0178474198085279492457085748943,0.0141450799797514318753734694236,0.00938760602039738574792466243944,0.0139659679555541336649910696206,0.011482092184390013210868218429,0.0131839720771717677442305821029,0.0114220017831793756747028669299,0.0114220017831793756747028669299,0.0204260986996998919664646348918,0.0203212686780721186206728200619,0.0121239906019536192226597488959,0.0191477246429359549518243846257,0.0444363265697931983723111238674,0.0242236957996042238828206484418,0.01352064213149832154279955233,0.0217937388022952707138760153275,0.0230820827856224481688250067649,0.0299236979668515144805585115046,0.0264356409873340241379948773521,0.0317645951055282630862124920767,0.0396987250083003669125508984359,0.0286230169861089708769722635018,0.0205832765137726237178739570722,0.0293200187536570324501101786826,0.0316606111690100378311240075115,0.0233354902300546562514970871461,0.0377085878695282442991678880245,0.0290566658155855653448672608472,0.0289703808741958944028465572185,0.00698294242383964232911117263742,0.0320078079989234839214277270347,0.0292967973465345269411607406394,0.0245028235316745923514742450202,0.00858385331892478555519631474991,0.0173054479209336798514906160162,0.0382524696799213911456940842769};
	private final static double[] SPECIAL_GRAPH_1stHALF_W = {0.00936728878527018253762845760888,0.0165562979442682515453213101518,0.0186971133647394677591854795334,0.0146134656580843158749112723689,0.0274911694880510123736356466923,0.0300746301416311939564596973003,0.0232547775166412394453727241989,0.0125930318851248012580245257744,0.0197207295657278127241666769941,0.00936728878527018253762845760888,0.0141854709786207322894213635679,0.0112858901027351596838897079625,0.0164876142292653613442895757225,0.0232069466434767476373947929232,0.0239414741338374189252961395023,0.0189749594109095218846827539147,0.0125930318851248012580245257744,0.0187346890558450960679083576005,0.0154026865498819588377576769386,0.0176856783699355857287361559357,0.0153220781032991667103202655779,0.0153220781032991667103202655779,0.027400650565769946346800284594,0.0272600260229409759769339653674,0.0162637630822615595647877911694,0.0138126042642194959714941544703,0.0385570535253510274752056597269,0.0202623192373839933885263456583,0.00943848553275340011899518755047,0.0180903392558543314732102308003,0.0182020514133866572969771032252,0.0315293238135186321962650451154,0.0220355865602386732449641871629,0.0281463798267966339473300108327,0.0331784951381305721452836661392,0.024577212891645443373801639674,0.0154528989485157291580648261999,0.0239789642704780590875694684176,0.0258257851090512031631003569802,0.0185741281987090491972857323004,0.0310262747339360610584812631984,0.0234065566239947889053300757058,0.0234982302176788279448644816159,0.00336728878527018253762845760888,0.0280082719339055827389227414683,0.0249795871962769769144376099265,0.0195601822476071915244491661349,0.00551483544447664651897499082965,0.0120092075292782105963676969162,0.0311651069295309710435720319811};
	private final static double[] SPECIAL_GRAPH_2ndHALF_W = {0.00459859606240910212059388766595,0.00812782954383827038322258612391,0.00917880017030216532365280197167,0.00717405293825142483939568700154,0.0134959844472357919267387510994,0.014764258785870906706180094027,0.0114162518922642193388755909568,0.00618218015566997023782479910449,0.0096813145625866342825818197196,0.00459859606240910212059388766595,0.0069639414862795175983257600077,0.00554047718362542424167938273006,0.00809411128569504076077945551024,0.0113927707260444168160515565574,0.0117533654832184795661210102862,0.00931520054859334186606418493258,0.00618218015566997023782479910449,0.00919724685526317126207378164074,0.00756149781889806758397875991946,0.00868226578440794975972500827009,0.00752192546305958463908546828179,0.00752192546305958463908546828179,0.0134515468336298375861289851896,0.0133825113332032612644116747565,0.00798421812164567888053170662246,0.0244828450216524139321546147811,0.0503155996142353692694165880079,0.0281850723618244543771149512253,0.0176027987302432429666039171095,0.0254971383487362099545417998546,0.0279621141578582390406729103046,0.0283180721201843967648519778939,0.0308356954144293750310255675412,0.0353828103842598922250949733208,0.0462189548784701616798181307327,0.0326688210805724983801428873297,0.0257136540790295182776830879445,0.0346610732368360058126508889476,0.0374954372289688724991476580428,0.0280968522614002633057084419919,0.0443909010051204275398545128505,0.0347067750071763417844044459886,0.0344425315307129608608286328211,0.0105985960624091021205938876659,0.0360073440639413851039327126011,0.0336140074967920769678838713524,0.0294454648157419931784993239055,0.0116528711933729245914176386702,0.0226016883125891491066135351163,0.0453398324303118112478161365728};

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
		queryNode = node[random.nextInt(n)];

		for (int i = 0; i < 50 * 10; i++) {
			try {
				int source, target;
				do {
					source = random.nextInt(n);
					target = random.nextInt(n);
				} while (source == target);  // Avoid loops
				builder.addArc(node[source], node[target]);
			} catch (final IllegalArgumentException ignoreDuplicateArcs) {
			}
		}

		directedGraph = builder.build(true);
		immutableGraph = new ImmutableGraphAdapter(directedGraph, false);

		builder = new ArrayImmutableDirectedGraph.Builder();
		n = SPECIAL_GRAPH.length;
		node = new long[n];
		// Nodes are numbered randomly but in increasing order to keep the adapter from re-permuting nodes
		node[0] = random.nextInt(100);
		builder.addInternalNode(node[0]);
		for (int i = 1; i < n; i++) builder.addInternalNode(node[i] = node[i-1] + 1 + random.nextInt(100));
		for (int source = 0; source < n; source++)
			for (final int target: SPECIAL_GRAPH[source])
				builder.addArc(node[source], node[target]);

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
		final LongSet queryNodes = new LongOpenHashSet();
		final Long2DoubleFunction pr;
		final int n = specialImmutableGraph.numNodes();

		// Uniform
		for (int i = 0; i < n; i++) queryNodes.add(specialImmutableGraph.node2Id(i));
		pr = QueryDependentCentralities.pageRankParallel(specialDirectedGraph, queryNodes, 0.85);
		for (final long id : specialDirectedGraph.nodes()) assertEquals(SPECIAL_GRAPH_UNIF_W[specialImmutableGraph.id2Node(id)], pr.get(id), 1E-6);
	}

	@Test
	public void testPageRank1stHalf() throws IOException {
		final LongSet queryNodes = new LongOpenHashSet();
		Long2DoubleFunction pr;
		final int n = specialImmutableGraph.numNodes();

		// 1st half of nodes
		for (int i = 0; i < n / 2; i++) queryNodes.add(specialImmutableGraph.node2Id(i));
		pr = QueryDependentCentralities.pageRankParallel(specialDirectedGraph, queryNodes, 0.85);
		for (final long id : specialDirectedGraph.nodes()) assertEquals(SPECIAL_GRAPH_1stHALF_W[specialImmutableGraph.id2Node(id)], pr.get(id), 1E-6);
	}

	@Test
	public void testPageRank2ndHalf() throws IOException {
		final LongSet queryNodes = new LongOpenHashSet();
		Long2DoubleFunction pr;
		final int n = specialImmutableGraph.numNodes();

		// 2nd half of nodes
		for (int i = n / 2; i < n; i++) queryNodes.add(specialImmutableGraph.node2Id(i));
		pr = QueryDependentCentralities.pageRankParallel(specialDirectedGraph, queryNodes, 0.85);
		for (final long id : specialDirectedGraph.nodes()) assertEquals(SPECIAL_GRAPH_2ndHALF_W[specialImmutableGraph.id2Node(id)], pr.get(id), 1E-6);
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
	public void testPageRankPush() throws IOException {
		Long2DoubleFunction pageRankPush;
		final int n = directedGraph.numNodes();

		pageRankPush = QueryDependentCentralities.pageRankPush(directedGraph, queryNode, 0.85);
		final Long2DoubleFunction prGS = QueryDependentCentralities.pageRankParallel(directedGraph, LongSets.singleton(queryNode), 0.85);

		final PageRankParallelGaussSeidel prPGS = new PageRankParallelGaussSeidel(immutableGraph.transpose());
		final double[] pref = new double[n];
		pref[immutableGraph.id2Node(queryNode)] = 1.;
		prPGS.preference = new DoubleArrayList(pref);
		prPGS.alpha = 0.85;
		prPGS.stepUntil(new SpectralRanking.NormStoppingCriterion(QueryDependentCentralities.DEFAULT_L1_THRESHOLD));

		for (final long id : directedGraph.nodes()) assertEquals(prGS.get(id), pageRankPush.get(id), 1E-6);
	}



}
