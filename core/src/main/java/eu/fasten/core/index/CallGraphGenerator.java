package eu.fasten.core.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

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

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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
	 *  Then a dependency DAG is generated between the call graphs, once more usin {@link #preferentialAttachmentDAG(int, int, IntegerDistribution, RandomGenerator)} (this
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
		callGraphGenerator.generate(jsapResult.getInt("n"),
				new BinomialDistribution(500, 0.2), // Graph size distribution
				new EnumeratedIntegerDistribution(new int[] { 1 }), // Initial graph size distribution
				new BinomialDistribution(4, 0.5),  // Internal outdegree distribution
				new GeometricDistribution(.5),  // External outdegree distribution
				new EnumeratedIntegerDistribution(new int[] { 5 }), // Dependency outdegree distribution
				new XoRoShiRo128PlusPlusRandomGenerator(0));
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
				producer.send(new ProducerRecord<>(topic, "fasten://f!graph-" + i + "$1.0", graph2String(callGraphGenerator, i)));

			producer.close();
		}
		else {
			final int np = callGraphGenerator.rcgs.length;
			for(int i = 0; i < np; i++) Files.writeString(Paths.get(basename + "-" + i + ".json"), graph2String(callGraphGenerator, i));
		}
	}

	private static String graph2String(final CallGraphGenerator callGraphGenerator, final int i) {
		final StringBuilder sb = new StringBuilder();
		sb.append("{\n");
		sb.append("\"forge\": \"f\",\n");
		sb.append("\"product\": \"graph-" + i + "\",\n");
		sb.append("\"version\": \"1.0\",\n");
		sb.append("\"timestamp\": \"0\",\n");
		sb.append("\"depset\": [\n");
		// All generated DNFs are singletons
		for(final IntIterator d = callGraphGenerator.deps[i].iterator(); d.hasNext(); ) {
			sb.append( "[{ \"forge\": \"f\", \"product\": \"graph-" + d.nextInt() + "\", \"constraints\": [\"[1.0]\"] }]");
			if (d.hasNext()) sb.append(", ");
		}
		sb.append("],\n");
		sb.append("\"graph\": [\n");
		final ArrayListMutableGraph g = callGraphGenerator.rcgs[i];
		final IntOpenHashSet callsExternal = new IntOpenHashSet();
		for(final int[] t: callGraphGenerator.source2Targets[i]) {
			sb.append("[ \"/p" + i + "/A.f" + t[0] + "()v\", \"//graph-" + t[1] + "/p" + t[1] + "/A.f" + t[2] +"()v\" ],\n");
			callsExternal.add(t[0]);
		}

		for(int j = 0; j < g.numNodes(); j++) {
			if (!callsExternal.contains(j) && g.outdegree(j) == 0)
				sb.append("[ \"/p" + i + "/A.f" + j + "()v\", \"//-\" ],\n");
			for(final IntIterator s = g.successors(j); s.hasNext();)
				sb.append("[ \"/p" + i + "/A.f" + j + "()v\", \"/p" + i + "/A.f" + s.nextInt() +"()v\" ],\n");
		}
		sb.append("]\n");
		sb.append("}\n");
		return sb.toString();
	}
}
