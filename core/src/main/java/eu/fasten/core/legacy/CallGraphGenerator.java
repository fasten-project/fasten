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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Properties;
import java.util.Random;

import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.apache.commons.math3.distribution.GeometricDistribution;
import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.MathArrays;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandomGenerator;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;


/** Generates a list of call graphs. See {@link #generate(int, IntegerDistribution, IntegerDistribution, IntegerDistribution, IntegerDistribution, RandomGenerator)}.
 */

public class CallGraphGenerator {

	private ArrayListMutableGraph[] rcgs;
	/** Each revision call graph <code>rcgs[i]</code> has an associated permutation of nodes <code>nodePermutation[i]</code>,
	 *  that gives the output name of its functions (e.g., node <code>j</code> of <code>rcgs[i]</code> will
	 *  be called <code>fk</code> where <code>k=nodePermutation[i][j]</code>.
	 */
	private int[][] nodePermutation;  
	private IntOpenHashSet[] deps;
	private ObjectOpenCustomHashSet<int[]>[] source2Targets;

	/** Generate a random DAG using preferential attachment. First an independent set of <code>n0</code> nodes is generated.
	 *  Then <code>n-n0</code> more nodes are generated: for each node, the outdegree is determined using <code>outdegreeDistribution.nextInt()</code>
	 *  minimized with the number of existing nodes. For each arc, the target is the existing node <code>i</code> with probability proportional to
	 *  <code>k+1</code> where <code>k</code> is <code>i</code>'s current outdegree.
	 *
	 * @param n number of nodes.
	 * @param n0 number of initial nodes.
	 * @param outdegreeDistribution distribution from which outdegrees are sampled.
	 * @param random generator used to produce the arcs.
	 * @return the generated DAG.
	 */
	public static ArrayListMutableGraph preferentialAttachmentDAG(final int n, final int n0, final IntegerDistribution outdegreeDistribution, final RandomGenerator random) {
		final ArrayListMutableGraph g = new ArrayListMutableGraph(n);
		final FenwickTree ft = new FenwickTree(n);
		// Initial independent set
		for (int source = 0; source < n0; source++) ft.incrementCount(source + 1);
		// Rest of the graph
		final IntOpenHashSet s = new IntOpenHashSet();
		for (int source = n0; source < n; source++) {
			final int m = Math.min(outdegreeDistribution.sample(), source - 1); // Outdegree
			s.clear();
			while(s.size() < m) {
				final int t = ft.sample(random);
				if (s.add(t)) {
					ft.incrementCount(t);
					g.addArc(source, t - 1);
				}
			}
			ft.incrementCount(source + 1);
		}
		return g;
	}

	/** Given <code>g</code>, returns a Fenwick tree for the nodes of <code>g</code> whose counter number <code>i+1</code> corresponds to node <code>i</code>
	 *  and it is initialized to <code>d</code> or to <code>D-d+1</code> (the latter iff <code>reverse</code> is true),
	 *  where <code>D</code> is the maximum indegree in <code>g</code>, and <code>d</code> is <code>i</code>'s indegree.
	 *
	 * @param g a graph.
	 * @param reverse if true, we generate the <em>reverse</em> distribution.
	 * @return a Fenwick tree as above.
	 */
	public static FenwickTree getPreferentialDistribution(final ImmutableGraph g, final boolean reverse) {
		final int n = g.numNodes();
		final int[] indegree = new int[n];
		int maxIndegree = 0;
		final NodeIterator nodeIterator = g.nodeIterator();
		while (nodeIterator.hasNext()) {
			nodeIterator.nextInt();
			final LazyIntIterator successors = nodeIterator.successors();
			int target;
			while ((target = successors.nextInt()) >= 0) {
				indegree[target]++;
				if (indegree[target] > maxIndegree) maxIndegree = indegree[target];
			}
		}
		final FenwickTree t = new FenwickTree(n);
		for (int i = 0; i < indegree.length; i++)
			t.incrementCount(i + 1, reverse? maxIndegree - indegree[i] + 1 : indegree[i]);

		return t;
	}

	/** Generates <code>np</code> call graphs. Each call graph is obtained using {@link #preferentialAttachmentDAG(int, int, IntegerDistribution, RandomGenerator)} (with
	 *  specified initial graph size (<code>initialGraphSizeDistribution</code>), graph size (<code>graphSizeDistribution</code>), outdegree distribution (<code>outdegreeDistribution</code>).
	 *  Then a dependency DAG is generated between the call graphs, once more using {@link #preferentialAttachmentDAG(int, int, IntegerDistribution, RandomGenerator)} (this
	 *  time the initial graph size is 1, whereas the outdegree distribution is <code>outdegreeDistribution</code>).
	 *  Then to each node of each call graph a new set of outgoing arcs is generated (their number is chosen using <code>externalOutdegreeDistribution</code>): the target
	 *  call graph is generated using the indegree distribution of the dependency DAG; the target node is chosen according to the reverse indegree distribution within the revision call graph.
	 *
	 * @param np number of revision call graphs to be generated.
	 * @param graphSizeDistribution the distribution of the graph sizes (number of functions per call graph).
	 * @param initialGraphSizeDistribution the distribution of the initial graph sizes (the initial independent set from which the preferential attachment starts).
	 * @param outdegreeDistribution the distribution of internal outdegrees (number of internal calls per function).
	 * @param externalOutdegreeDistribution the distribution of external outdegrees (number of external calls per function).
	 * @param depExponent exponent of the Zipf distribution used to establish the dependencies between call graphs.
	 * @param random the random object used for the generation.
	 */
	public void generate(final int np, final IntegerDistribution graphSizeDistribution, final IntegerDistribution initialGraphSizeDistribution,
			final IntegerDistribution outdegreeDistribution, final IntegerDistribution externalOutdegreeDistribution, final IntegerDistribution dependencyOutdegreeDistribution, final RandomGenerator random) {
		rcgs = new ArrayListMutableGraph[np];
		nodePermutation = new int[np][];
		final FenwickTree[] td = new FenwickTree[np];
		deps = new IntOpenHashSet[np];
		source2Targets = new ObjectOpenCustomHashSet[np];

		// Generate rcg of the np revisions, and the corresponding reverse preferential distribution; cumsize[i] is the sum of all nodes in packages <i
		for ( int i = 0; i < np; i++) {
			deps[i] = new IntOpenHashSet();
			final int n = graphSizeDistribution.sample();
			final int n0 = Math.min(initialGraphSizeDistribution.sample(), n);
			rcgs[i] = preferentialAttachmentDAG(n, n0, outdegreeDistribution, random);
			td[i] = getPreferentialDistribution(rcgs[i].immutableView(), true);
			nodePermutation[i] = Util.identity(n);
			Collections.shuffle(IntArrayList.wrap(nodePermutation[i]), new Random(random.nextLong()));
		}

		// Generate the dependency DAG between revisions using preferential attachment starting from 1 node
		final ArrayListMutableGraph depDAG = preferentialAttachmentDAG(np, 1, dependencyOutdegreeDistribution, random);

		// For each source package, generate function calls so to cover all dependencies
		for (int sourcePackage = 0; sourcePackage < np; sourcePackage++) {
			source2Targets[sourcePackage] = new ObjectOpenCustomHashSet<>(IntArrays.HASH_STRATEGY);
			final int outdegree = depDAG.outdegree(sourcePackage);
			if (outdegree == 0) continue; // No calls needed (I'm kinda busy)

			final int numFuncs = rcgs[sourcePackage].numNodes();
			final int[] externalArcs = new int[numFuncs];
			int allExternalArcs = 0;
			// We decide how many calls to dispatch from each function
			for (int sourceNode = 0; sourceNode < numFuncs; sourceNode++) allExternalArcs += (externalArcs[sourceNode] = externalOutdegreeDistribution.sample());
			// We create a global list of external successors by shuffling
			final int[] targetPackage = new int[allExternalArcs];
			final int[] succ = depDAG.successorArray(sourcePackage);
			for(int i = 0; i < outdegree; i++) deps[sourcePackage].add(succ[i]);
			for(int i = 0; i < allExternalArcs; i++) targetPackage[i] = succ[i % outdegree];
			MathArrays.shuffle(targetPackage, random);

			for (int sourceNode = allExternalArcs = 0; sourceNode < numFuncs; sourceNode++) {
				final int externalOutdegree = externalArcs[sourceNode];
				for (int t = 0; t < externalOutdegree; t++) {
					final int targetNode = td[targetPackage[allExternalArcs + t]].sample(random) - 1;
					source2Targets[sourcePackage].add(new int[] { sourceNode, targetPackage[allExternalArcs + t], targetNode });
				}
				allExternalArcs += externalOutdegree;
			}
		}
	}

	public static void main(final String[] args) throws IOException, JSAPException {
		final SimpleJSAP jsap = new SimpleJSAP(CallGraphGenerator.class.getName(), "Generates pseudorandom call graphs", new Parameter[] {
				new UnflaggedOption("n", JSAP.INTEGER_PARSER, JSAP.REQUIRED, "The number of graphs."),
				new FlaggedOption( "host", JSAP.STRING_PARSER, "localhost", JSAP.NOT_REQUIRED, 'h', "host", "The host of the Kafka server." ),
				new FlaggedOption( "port", JSAP.INTEGER_PARSER, "3000", JSAP.NOT_REQUIRED, 'p', "port", "The port of the Kafka server." ),
				new FlaggedOption( "topic", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 't', "topic", "A kafka topic ." ),
				new UnflaggedOption("basename", JSAP.STRING_PARSER, JSAP.NOT_REQUIRED, "The basename of the resulting graphs."),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);
		final String basename = jsapResult.getString("basename");

		final CallGraphGenerator callGraphGenerator = new CallGraphGenerator();
		XoRoShiRo128PlusPlusRandomGenerator randomGenerator = new XoRoShiRo128PlusPlusRandomGenerator(0);
		callGraphGenerator.generate(jsapResult.getInt("n"),
				new BinomialDistribution(5000, 0.2), // Graph size distribution
				new EnumeratedIntegerDistribution(new int[] { 1 }), // Initial graph size distribution
				new BinomialDistribution(4, 0.5),  // Internal outdegree distribution
				new GeometricDistribution(.5),  // External outdegree distribution
				new EnumeratedIntegerDistribution(new int[] { 5 }), // Dependency outdegree distribution
				randomGenerator);
		if (jsapResult.userSpecified("topic")) {
			final Properties properties = new Properties();
			properties.put("bootstrap.servers", jsapResult.getString("host") + ":" + Integer.toString(jsapResult.getInt("port")));
			properties.put("client.id", CallGraphGenerator.class.getSimpleName());
			properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

			final AdminClient adminClient = AdminClient.create(properties);
			final String topic = jsapResult.getString("topic");
			adminClient.createTopics(ObjectLists.singleton(new NewTopic(topic, 1, (short)1)));
			adminClient.close();

			final KafkaProducer<String, String> producer = new KafkaProducer<>(properties);
			final int np = callGraphGenerator.rcgs.length;
			for(int i = 0; i < np; i++)
				producer.send(new ProducerRecord<>(topic, "fasten://f!graph-" + i + "$1.0", graph2String(callGraphGenerator, i, randomGenerator)));

			producer.close();
		}
		else {
			final int np = callGraphGenerator.rcgs.length;
			for(int i = 0; i < np; i++) Files.writeString(Paths.get(basename + "-" + i + ".json"), graph2String(callGraphGenerator, i, randomGenerator));
		}
	}

	private static String graph2String(final CallGraphGenerator callGraphGenerator, final int i, final RandomGenerator randomGenerator) {
		final ArrayListMutableGraph g = callGraphGenerator.rcgs[i];
		final StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("\t\"product\": \"graph-" + i + "\",\n");
		sb.append("\t\"forge\": \"f\",\n");
		sb.append("\t\"generator\": \"OPAL\",\n");
		sb.append("\t\"version\": \"1.0\",\n");
		sb.append("\t\"timestamp\": \"0\",\n");
		sb.append("\t\"depset\": [\n\t\t");
		// All generated DNFs are singletons
		for(final IntIterator d = callGraphGenerator.deps[i].iterator(); d.hasNext(); ) {
			sb.append( "[{ \"forge\": \"f\", \"product\": \"graph-" + d.nextInt() + "\", \"constraints\": [\"[1.0]\"] }]");
			if (d.hasNext()) sb.append(", ");
		}
		sb.append("\n\t],\n");
		sb.append("\t\"cha\": {\n");
		for (int jj = 0; jj < g.numNodes() / 3; jj++) {			
			sb.append("\t\t\"/p" + i + "/A" + jj + "\": {\n");
			sb.append("\t\t\t\"methods\": {\n");
			for (int j = 3 * jj; j < 3 * jj + 3 && j < g.numNodes(); j++) {
				sb.append("\t\t\t\t\"" + j + "\": \"/p" + i + "/A" + jj + ".f" + j + "()v\"");
				if (j < 3 * jj + 2 && j < g.numNodes() + 1) sb.append(",");
				sb.append("\n");
			}
			sb.append("\t\t\t},\n");
			sb.append("\t\t\t\"superInterfaces\": [],\n");
			sb.append("\t\t\t\"sourceFile\": \"A" + jj + ".java\",\n");
			sb.append("\t\t\t\"superClasses\": [\"/java.lang/Object\"]\n");
			sb.append("\t\t}");
			if (jj < g.numNodes() / 3 - 1) sb.append(",");
			sb.append("\n");
		}
		sb.append("\t},\n");
		sb.append("\t\"graph\": {\n");
		
		// Internal calls
		sb.append("\t\t\"internalCalls\": [\n");
		final ObjectArrayList<String> lines = new ObjectArrayList<>(); // Graph lines
		for(int j = 0; j < g.numNodes(); j++) {
			for(final IntIterator s = g.successors(j); s.hasNext();)
				lines.add("\t\t\t[\n\t\t\t\t" + callGraphGenerator.nodePermutation[i][j] + ",\n\t\t\t\t" + callGraphGenerator.nodePermutation[i][s.nextInt()] + "\n\t\t\t]");
		}
		Collections.shuffle(lines, new Random(randomGenerator.nextLong())); // Permute graph lines
		for (int j = 0; j < lines.size(); j++) {
			sb.append(lines.get(j));
			if (j < lines.size() - 1) sb.append(",");
			sb.append("\n");
		}
		sb.append("\t\t],\n");
		
		// External calls
		sb.append("\t\t\"externalCalls\": [\n");
		lines.clear();
		for(final int[] t: callGraphGenerator.source2Targets[i]) {
			lines.add("\t\t\t[\n\t\t\t\t\"" + callGraphGenerator.nodePermutation[i][t[0]] + "\",\n\t\t\t\t\"/p" + t[1] + "/A"+ callGraphGenerator.nodePermutation[t[1]][t[2]] / 3 + ".f" + callGraphGenerator.nodePermutation[t[1]][t[2]] +"()v\",\n"
					+ "\t\t\t\t{\"invokevirtual\": \"1\"}\n"
					+ "\t\t\t]");
		}
		Collections.shuffle(lines, new Random(randomGenerator.nextLong())); // Permute graph lines
		for (int j = 0; j < lines.size(); j++) {
			sb.append(lines.get(j));
			if (j < lines.size() - 1) sb.append(",");
			sb.append("\n");
		}
		sb.append("\t\t]\n");
		sb.append("\t}\n");
		sb.append("}");
		
		return sb.toString();
	}
}
