package eu.fasten.core.index;

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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;

import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;
import org.apache.commons.math3.distribution.GeometricDistribution;
import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.json.JSONObject;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.JSONCallGraph;
import eu.fasten.core.index.InMemoryIndexer.KafkaConsumerMonster;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandomGenerator;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;


/** Creates a {@link BufferedImage} from an {@link ImmutableGraph}.
 *
 * TODO: move to WebGraph
 */

public class CallGraphGenerator {

	private ArrayListMutableGraph[] rcgs;
	private IntOpenHashSet[] deps;
	private ObjectOpenCustomHashSet<int[]>[] source2Targets;

	/** Generate a random DAG using preferential attachment. First an independent set of <code>n0</code> nodes is generated.
	 *  Then <code>n-n0</code> more nodes are generated: for each node, the outdegree is determined using <code>outdegreeDistribution.nextInt()</code>
	 *  minimized with the number of existing nodes. For each arc, the target is existing node <code>i</code> with probability proportional to
	 *  <code>k+1</code> where <code>k</code> is <code>i</code>'s current outdegree. 
	 * 
	 * @param n number of nodes.
	 * @param n0 number of initial nodes.
	 * @param outdegreeDistribution distribution from which outdegrees are sampled.
	 * @param random generator used to produce the arcs.
	 * @return the generated DAG.
	 */
	public static ArrayListMutableGraph preferentialAttachmentDAG(final int n, final int n0, final IntegerDistribution outdegreeDistribution, final RandomGenerator random) {
		ArrayListMutableGraph g = new ArrayListMutableGraph(n);
		FenwickTree ft = new FenwickTree(n);
		// Initial independent set
		for (int source = 0; source < n0; source++) ft.incrementCount(source + 1);
		// Rest of the graph
		IntOpenHashSet s = new IntOpenHashSet();
		for (int source = n0; source < n; source++) {			
			int m = Math.min(outdegreeDistribution.sample(), source - 1); // Outdegree
			s.clear();
			while(s.size() < m) {
				int t = ft.sample(random);
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
	 * @param g
	 * @param reverse
	 * @return
	 */
	public static FenwickTree getPreferentialDistribution(final ImmutableGraph g, final boolean reverse) {
		int n = g.numNodes();
		int[] indegree = new int[n];
		int maxIndegree = 0;
		NodeIterator nodeIterator = g.nodeIterator();
		while (nodeIterator.hasNext()) {
			nodeIterator.nextInt();
			LazyIntIterator successors = nodeIterator.successors();
			int target;
			while ((target = successors.nextInt()) >= 0) {
				indegree[target]++;
				if (indegree[target] > maxIndegree) maxIndegree = indegree[target];
			}
		}
		FenwickTree t = new FenwickTree(n);
		for (int i = 0; i < indegree.length; i++) 
			t.incrementCount(i + 1, reverse? maxIndegree - indegree[i] + 1 : indegree[i]);

		return t;
	}
	
	public void generate(final int np, final IntegerDistribution graphSizeDistribution, final IntegerDistribution initialGraphSizeDistribution, 
			final IntegerDistribution outdegreeDistribution, final IntegerDistribution externalOutdegreeDistribution, final RandomGenerator random) {
		rcgs = new ArrayListMutableGraph[np];
		FenwickTree[] td = new FenwickTree[np];
		deps = new IntOpenHashSet[np];
		source2Targets = new ObjectOpenCustomHashSet[np];

		// Generate rcg of the np revisions, and the corresponding reverse preferential distribution; cumsize[i] is the sum of all nodes in packages <i
		for (int i = 0; i < np; i++) {
			deps[i] = new IntOpenHashSet();
			int n = graphSizeDistribution.sample();
			int n0 = Math.min(initialGraphSizeDistribution.sample(), n);
			rcgs[i] = preferentialAttachmentDAG(n, n0, outdegreeDistribution, random);
			td[i] = getPreferentialDistribution(rcgs[i].immutableView(), true);
		}
		// Generate the dependency DAG between revisions and the corresponding PA distribution
		ImmutableGraph dependencyDAG = preferentialAttachmentDAG(np, 1, outdegreeDistribution, random).immutableView();
		FenwickTree tdDep = getPreferentialDistribution(dependencyDAG, false);
		for (int sourcePackage = 0; sourcePackage < np; sourcePackage++) {
			source2Targets[sourcePackage] = new ObjectOpenCustomHashSet<>(IntArrays.HASH_STRATEGY);

			for (int sourceNode = 0; sourceNode < rcgs[sourcePackage].numNodes(); sourceNode++) {
				int externalOutdegree = Math.min(externalOutdegreeDistribution.sample(), sourcePackage - 1);
				for (int t = 0; t < externalOutdegree; t++) {
					int targetPackage;
					do targetPackage = tdDep.sample(random) - 1; while(targetPackage == sourcePackage);
					deps[sourcePackage].add(targetPackage);
					int targetNode = td[targetPackage].sample(random) - 1;
					source2Targets[sourcePackage].add(new int[] { sourceNode, targetPackage, targetNode });
				}
			}
		}
		/*
		// Now produce the actual immutable graph
		ArrayListMutableGraph result = new ArrayListMutableGraph(cumsize[np - 1] + rcgs[np - 1].numNodes());
		for (int i = 0; i < np; i++) {
			ImmutableGraph graph = rcgs[i].immutableView();
			NodeIterator nodeIterator = graph.nodeIterator();
			while (nodeIterator.hasNext()) {
				int source = nodeIterator.nextInt() + cumsize[i];
				// Internal arcs
				int s;
				LazyIntIterator successors = nodeIterator.successors();
				while ((s = successors.nextInt()) >= 0) {
					int target = s + cumsize[i];
					result.addArc(source, target);
				}
				// External arcs
				for (int target: source2Targets.get(source)) result.addArc(source, target);
			}
		}
		return result.immutableView();
		*/
	}
	
	public static void main(String[] args) throws IOException, JSAPException, ClassNotFoundException {
		SimpleJSAP jsap = new SimpleJSAP(CallGraphGenerator.class.getName(), "Generates a PNG rendition of the .", new Parameter[] {
				new UnflaggedOption("n", JSAP.INTEGER_PARSER, JSAP.REQUIRED, "The number of graphs."),
				new UnflaggedOption("basename", JSAP.STRING_PARSER, JSAP.REQUIRED, "The basename of the resulting graphs."),
		});

		JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);
		String basename = jsapResult.getString("basename");

		/*if (jsapResult.userSpecified("topic")) {
			// Kafka consumer
			final String kafka = jsapResult.getString("topic");
			final Producer<String, String> consumer = KafkaProducer<String, String>KafkaConsumerMonster.createConsumer(kafka);
			final ConsumerRecords<String, String> records = consumer.poll(Long.MAX_VALUE);

			for (final ConsumerRecord<String, String> record : records) {
				final JSONObject json = new JSONObject(record.value());
				try {
					inMemoryIndexer.add(new JSONCallGraph(json, false));
				} catch(final IllegalArgumentException e) {
					e.printStackTrace(System.err);
				}
			}
		}*/

		CallGraphGenerator callGraphGenerator = new CallGraphGenerator();
		callGraphGenerator.generate(jsapResult.getInt("n"), new EnumeratedIntegerDistribution(new int[] { 100 }), new EnumeratedIntegerDistribution(new int[] { 10 }), new BinomialDistribution(4, 0.5), new GeometricDistribution(.5), new XoRoShiRo128PlusPlusRandomGenerator(0));
		final int np = callGraphGenerator.rcgs.length;
		for(int i = 0; i < np; i++) {
			printGraph(new FileOutputStream(basename + "-" + i + ".json"), callGraphGenerator, i);
			
		}
	}

	private static void printGraph(OutputStream stream, CallGraphGenerator callGraphGenerator, int i) throws FileNotFoundException {
		PrintStream ps = new PrintStream(stream);
		ps.println("{");
		ps.println("\"forge\": \"f\",");
		ps.println("\"product\": \"graph" + i + "\",");
		ps.println("\"version\": \"1.0\",");
		ps.println("\"timestamp\": \"0\",");
		ps.println("\"depset\": [");
		for(IntIterator d = callGraphGenerator.deps[i].iterator(); d.hasNext(); ) {
			ps.print( "{ \"forge\": \"f\", \"product\": \"graph" + d.nextInt() + "\", \"constraints\": [\"[1.0]\"] }");
			if (d.hasNext()) ps.print(", ");
		}
		ps.println("],");
		ps.println("\"graph\": [");
		ArrayListMutableGraph g = callGraphGenerator.rcgs[i];
		IntOpenHashSet callsExternal = new IntOpenHashSet();
		for(int[] t: callGraphGenerator.source2Targets[i]) {
			ps.println("[ \"/p" + i + "/A.f" + t[0] + "()v\", \"//graph" + t[1] + "/p" + t[1] + "/A.f" + t[2] +"()v\" ],");
			callsExternal.add(t[0]);
		}

		for(int j = 0; j < g.numNodes(); j++) {
			if (!callsExternal.contains(j) && g.outdegree(j) == 0)
				ps.println("[ \"/p" + i + "/A.f" + j + "()v\", \"//-\" ],");
			for(IntIterator s = g.successors(j); s.hasNext();)
				ps.println("[ \"/p" + i + "/A.f" + j + "()v\", \"/p" + i + "/A.f" + s.nextInt() +"()v\" ],");
		}
		ps.println("]");
		ps.println("}");
		ps.close();
	}
}
