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
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.law.rank.PageRankParallelGaussSeidel;
import it.unimi.dsi.law.rank.SpectralRanking;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandomGenerator;
import it.unimi.dsi.webgraph.algo.GeometricCentralities;

/** Tests implementations of query-dependent centrality measures. */

public class QueryDependentCentralitiesTest {

	private final static double QUERY_NODE_DENSITY = 0.1;
	private static ArrayImmutableDirectedGraph directedGraph, specialDirectedGraph;
	private static ImmutableGraphAdapter immutableGraph, specialImmutableGraph;
	private static LongSet queryNodes;
	private static long queryNode;

	/** This is graph test50-.6-7-3-2-10 in law.di.unimi.it/data/it/unimi/dsi/law/rank. Its TRANSPOSE
	 *  is stored in {{@link #specialDirectedGraph} and {@link #specialImmutableGraph}. */
	private final static int[][] SPECIAL_GRAPH = {{},{5,8,14},{22},{3,4,5,19},{1,2,6,12,14,18},{5,6,13,23},{1,2,4,5,19,24},{2,3,4,18,22},{},{6,8,10,15,24},{4,6,20,21,23,24},{11,14,17,19,23},{4,13,17,23},{13,15,17,20,21},{4,8,14,15,23},{5,7,10,12,16},{29,42,46},{31},{31,34,35,38,42,44},{26,30,36,40,41,45,47},{26,35,37,38,40},{31,35,37,41,44},{29,34,45,49},{27,32,33},{26,31,39,44},{},{33,34,40,45},{26,30,32,46,49},{28,35,44,45,49},{35,39,40,45},{},{26,28,30,33,35,44},{32,34,35,38,39,41},{37,39,42,44,46},{},{25,26},{26,29,32},{},{},{},{27,33,36,42,46,48},{},{27,30,31,34,37},{29,34,42},{34,36,38,41,49},{26,38,40,42,48},{38,44,49},{27,30,34,35,36,41},{},{37,40,41,49}};
	private final static double[] SPECIAL_GRAPH_UNIF = {0.00698294242383964232911117263742,0.0123420637440532609642719481379,0.0139379567675208165414191407526,0.0108937592981678703571534796852,0.0204935769676434021501871988959,0.0224194444637510503313198956637,0.0173355147044527293921241575778,0.00938760602039738574792466243944,0.0147010220641572235033742483569,0.00698294242383964232911117263742,0.0105747062324501249438735617878,0.00841318364318029196278454534628,0.0122908627574802010525345156164,0.0172998586847605822267231747403,0.0178474198085279492457085748943,0.0141450799797514318753734694236,0.00938760602039738574792466243944,0.0139659679555541336649910696206,0.011482092184390013210868218429,0.0131839720771717677442305821029,0.0114220017831793756747028669299,0.0114220017831793756747028669299,0.0204260986996998919664646348918,0.0203212686780721186206728200619,0.0121239906019536192226597488959,0.0191477246429359549518243846257,0.0444363265697931983723111238674,0.0242236957996042238828206484418,0.01352064213149832154279955233,0.0217937388022952707138760153275,0.0230820827856224481688250067649,0.0299236979668515144805585115046,0.0264356409873340241379948773521,0.0317645951055282630862124920767,0.0396987250083003669125508984359,0.0286230169861089708769722635018,0.0205832765137726237178739570722,0.0293200187536570324501101786826,0.0316606111690100378311240075115,0.0233354902300546562514970871461,0.0377085878695282442991678880245,0.0290566658155855653448672608472,0.0289703808741958944028465572185,0.00698294242383964232911117263742,0.0320078079989234839214277270347,0.0292967973465345269411607406394,0.0245028235316745923514742450202,0.00858385331892478555519631474991,0.0173054479209336798514906160162,0.0382524696799213911456940842769};
	private final static double[] SPECIAL_GRAPH_1stHALF_S = { 0.0115878506989996808239301388505,
			0.0204810498645048675429689092548, 0.0231293561178089982015411934276, 0.0180776596219730939460348228867,
			0.0340080865307967795549584469812, 0.0372039692484836782967728825343, 0.0287674369904163031676681676372,
			0.0155782720782595921092487304282, 0.0243956255776320057916224493048, 0.0115878506989996808239301388505,
			0.0175482066970895378493168540328, 0.0139612659024092540047351070488, 0.0203960843367891358795345104172,
			0.0287082675733375347426451323526, 0.0296169184207411770026450738875, 0.0234730669368230075606975975159,
			0.0155782720782595921092487304282, 0.0231758393114443262853858630124, 0.0190539692108333552527882030123,
			0.0218781554790541794170204973275, 0.0189542521352214129254996990051, 0.0189542521352214129254996990051,
			0.0338961096524414599538124374367, 0.0337221493695600783029699782665, 0.020119168173559620708071140525,
			0.00884396157464312810594608964072, 0.0330816368601849997770049498558, 0.0165730557720167821247128180257,
			0.00563673859879957198999426258196, 0.0146413319651451475962671516815, 0.0136572369609719680258372802445,
			0.0330246567317904335413781501861, 0.0179377786925584404724320870036, 0.0247767053643979908024332335372,
			0.0271061499651860911723476337656, 0.0208093213521014778963437403311, 0.0106749349711568926769230650761,
			0.0190047950992735582027033629289, 0.0203917622961041151840851490647, 0.0141398315062015341013467491207,
			0.0248029800463271393808208228773, 0.018144562191385615293667653551, 0.0184019700725037525893437549395, 0,
			0.0242834702011414640699275898304, 0.0209589329036361155816569781321, 0.014957058655260407240851585628,
			0.00265663316531372178635248896119, 0.00707677410018115106116463619008, 0.0245645861130587361479123634172 };
	private final static double[] SPECIAL_GRAPH_2ndHALF_S = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0.0347724841290027082358275091314, 0.0616547247972807832924172501163,
			0.0358252252168887293600002972506, 0.025475895342709792270067712819, 0.0326397409702669080667077461841,
			0.0373740408040071885224715063402, 0.0252213640178616608865476015284, 0.0393219092003688256539595051548,
			0.0423611214228209874881179522823, 0.0587942685543639347248827871052, 0.0404718060323544688876836495094,
			0.0356084136877415261290114116541, 0.044962157220592121694817470942, 0.0487488392422863792049469839885,
			0.037279905966698928430028335939, 0.0572788183690566596775747175965, 0.0456039207830283092442890740839,
			0.0449964556035858936940331966967, 0.0175719665652520589585619580899, 0.0437210936674258746882162225996,
			0.041940442749116608058287530983, 0.0389781447629524042770651455034, 0.0175719665652520589585619580899,
			0.0328163411015515757827939233499, 0.05900895322753361381312855306 };

	// The following are computed using nodes with numbers 1,2,...,10 weighted as x->2.0*x/110 
	private final static double[] KATZ_WEIGHTED_RANK = {0.0,0.018199999999999997,0.03639454545454545,0.05456363818200002,0.07277454727290911,0.09093637000081828,0.10914182563718192,0.12727272727272726,0.14547272909090908,0.16363636363636364,0.18183454545454547,0.0,7.277454727290911E-6,9.095274272981856E-6,9.098364563747287E-6,1.636545572752004E-5,1.6365455727520039E-9,1.6372729000272769E-9,2.000472745456364E-5,1.6370546381918195E-5,1.8184364072881845E-5,1.8184364072881845E-5,1.6366727272727273E-5,2.727872912746548E-5,4.5461273472809106E-5,0.0,8.001618392760916E-9,2.727872912746548E-9,0.0,1.6368363818300024E-9,1.6373274254830943E-9,8.365200227315461E-9,2.7284185418920124E-9,2.7295095946085558E-9,3.6382184764225565E-9,5.638618605547837E-9,1.6370546381918196E-9,3.6371457655358303E-9,3.8191819945987375E-9,4.546836823732744E-9,3.4564548909574633E-9,3.4557638873341936E-9,2.001254996462196E-9,0.0,8.366145971007651E-9,3.274691210942006E-9,1.0550382971064573E-12,1.6370546381918196E-9,6.73114610189947E-13,1.6379460292678596E-9};
	private final static double[] PAGE_RANK_WEIGHTED_RANK = {0.0,0.022814818862197436,0.034406397596202495,0.027693057500947877,0.05636543737110773,0.06347970217658808,0.06862140377970624,0.03813663178204099,0.05810628869507699,0.04597516821596129,0.0612774664507415,0.0,0.010363271241644694,0.018905580543824654,0.017408797958067712,0.01398922294203512,0.002378167900145971,0.005416143831299689,0.014468331030520561,0.015606140254409808,0.011894923106305237,0.011894923106305237,0.03572866535971909,0.02733210191810102,0.02621811854602685,0.0056737403002356255,0.024779812447556074,0.01258110045999267,0.0027617358568639634,0.010486036049427022,0.00852815754417549,0.016180525255929674,0.014100306458764351,0.01793695329484897,0.02214384667307366,0.01334997907182677,0.007834873688778611,0.01324872833673221,0.01447990303961116,0.01284645832664989,0.018599343646554786,0.013441184273049647,0.011374329371119601,0.0,0.01786160911612365,0.017450860606822993,0.00849679039329303,0.0018950313166069051,0.00560155331975517,0.019866058485477223};
	
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
	public void testWeighedPath() throws InterruptedException {
		final ArrayImmutableDirectedGraph.Builder builder = new ArrayImmutableDirectedGraph.Builder();
		builder.addInternalNode(0);
		builder.addInternalNode(1);
		builder.addInternalNode(2);
		builder.addArc(0, 1);
		builder.addArc(1, 2);
		final ArrayImmutableDirectedGraph graph = builder.build();

		final Long2DoubleFunction closeness = QueryDependentCentralities.closeness(graph, new Long2DoubleOpenHashMap(new long[] {
				0, 1 }, new double[] { 3, 2 }));
		assertEquals(0., closeness.get(0), 1E-9);
		assertEquals(3, closeness.get(1), 1E-9);
		assertEquals(1. / (2. / 3 + 1. / 2), closeness.get(2), 1E-9);

		final Long2DoubleFunction harmonic = QueryDependentCentralities.harmonic(graph, new Long2DoubleOpenHashMap(new long[] {
				0, 1 }, new double[] { 3, 2 }));
		assertEquals(0., harmonic.get(0), 1E-9);
		assertEquals(3, harmonic.get(1), 1E-9);
		assertEquals(3. / 2 + 2, harmonic.get(2), 1E-9);

	}

	@Test
	public void testCloseness() throws InterruptedException {
		final GeometricCentralities geometricCentralities = new GeometricCentralities(immutableGraph.transpose());
		geometricCentralities.compute();

		final Long2DoubleFunction closeness = QueryDependentCentralities.closeness(directedGraph, directedGraph.nodes());

		for (final long id : directedGraph.nodes()) {
			assertEquals(geometricCentralities.closeness[immutableGraph.id2Node(id)], closeness.get(id), 1E-9);
		}

		final Long2DoubleOpenHashMap trivialWeight = new Long2DoubleOpenHashMap();
		directedGraph.nodes().forEach(x -> trivialWeight.put(x, 1 / 2.));
		final Long2DoubleFunction closenessWeighed = QueryDependentCentralities.closeness(directedGraph, trivialWeight);

		for (final long id : directedGraph.nodes()) {
			assertEquals(geometricCentralities.closeness[immutableGraph.id2Node(id)] / 2, closenessWeighed.get(id), 1E-9);
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

		final Long2DoubleOpenHashMap trivialWeight = new Long2DoubleOpenHashMap();
		directedGraph.nodes().forEach(x -> trivialWeight.put(x, 1 / 2.));
		final Long2DoubleFunction harmonicWeighed = QueryDependentCentralities.harmonic(directedGraph, trivialWeight);

		for (final long id : directedGraph.nodes()) {
			assertEquals(geometricCentralities.harmonic[immutableGraph.id2Node(id)] / 2, harmonicWeighed.get(id), 1E-9);
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
		for (final long id : specialDirectedGraph.nodes()) assertEquals(SPECIAL_GRAPH_UNIF[specialImmutableGraph.id2Node(id)], pr.get(id), 1E-6);
	}

	@Test
	public void testPageRank1stHalf() throws IOException {
		final LongSet queryNodes = new LongOpenHashSet();
		Long2DoubleFunction pr;
		final int n = specialImmutableGraph.numNodes();

		// 1st half of nodes
		for (int i = 0; i < n / 2; i++) queryNodes.add(specialImmutableGraph.node2Id(i));
		pr = QueryDependentCentralities.pageRankParallel(specialDirectedGraph, queryNodes, 0.85);
		for (final long id : specialDirectedGraph.nodes()) assertEquals(SPECIAL_GRAPH_1stHALF_S[specialImmutableGraph.id2Node(id)], pr.get(id), 1E-6);
	}

	@Test
	public void testPageRank2ndHalf() throws IOException {
		final LongSet queryNodes = new LongOpenHashSet();
		Long2DoubleFunction pr;
		final int n = specialImmutableGraph.numNodes();

		// 2nd half of nodes
		for (int i = n / 2; i < n; i++) queryNodes.add(specialImmutableGraph.node2Id(i));
		pr = QueryDependentCentralities.pageRankParallel(specialDirectedGraph, queryNodes, 0.85);
		for (final long id : specialDirectedGraph.nodes()) assertEquals(SPECIAL_GRAPH_2ndHALF_S[specialImmutableGraph.id2Node(id)], pr.get(id), 1E-6);
	}
	
	@Test
	public void testWeightedPageRank()  throws IOException {
		final Long2DoubleMap queryNodeWeights = new Long2DoubleOpenHashMap();
		for (int x = 1; x <= 10; x++) queryNodeWeights.put(specialImmutableGraph.node2Id(x), 2.0 * x / 110.0);
		Long2DoubleFunction katz = QueryDependentCentralities.pageRankParallel(specialDirectedGraph, queryNodeWeights, 0.85);
		for (final long id : specialDirectedGraph.nodes()) assertEquals(PAGE_RANK_WEIGHTED_RANK[specialImmutableGraph.id2Node(id)], katz.get(id), 1E-6);
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
	public void testWeightedKatz()  throws IOException {
		final Long2DoubleMap queryNodeWeights = new Long2DoubleOpenHashMap();
		for (int x = 1; x <= 10; x++) queryNodeWeights.put(specialImmutableGraph.node2Id(x), 2.0 * x / 110.0);
		Long2DoubleFunction katz = QueryDependentCentralities.katzParallel(specialDirectedGraph, queryNodeWeights, 0.0001);
		for (final long id : specialDirectedGraph.nodes()) assertEquals(KATZ_WEIGHTED_RANK[specialImmutableGraph.id2Node(id)], katz.get(id), 1E-6);
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
