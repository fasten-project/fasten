package eu.fasten.core.legacy;

import static eu.fasten.core.legacy.KnowledgeBase.gid;
import static eu.fasten.core.legacy.KnowledgeBase.index;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.mutable.MutableLong;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.legacy.KnowledgeBase.CallGraph;
import eu.fasten.core.legacy.KnowledgeBase.CallGraphData;
import eu.fasten.core.legacy.KnowledgeBase.Node;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.stat.SummaryStats;
import it.unimi.dsi.util.XoRoShiRo128PlusPlusRandom;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;


public class GlobalVisitStats {

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalVisitStats.class);

	private static final LongOpenHashSet badGIDs = new LongOpenHashSet();

	public static class Result {
		final LongOpenHashSet nodes;
		final long numProducts;
		final long numRevs;

		public Result(final LongOpenHashSet result, final long numProducts, final long numRevs) {
			this.numRevs = numRevs;
			this.nodes = result;
			this.numProducts = numProducts;
		}
	}

	public static Result reaches(final KnowledgeBase kb, final long startSig, final int maxRevs, final ProgressLogger pl) {
		final LongOpenHashSet result = new LongOpenHashSet();
		final Object2ObjectOpenHashMap<String, IntOpenHashSet> product2Revs = new Object2ObjectOpenHashMap<>();
		final MutableLong totRevs = new MutableLong();

		// Visit queue
		final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		queue.enqueue(startSig);
		result.add(startSig);

		String p = kb.callGraphs.get(index(startSig)).product;
		IntOpenHashSet revs = new IntOpenHashSet();
		revs.add(index(startSig));
		product2Revs.put(p, revs);
		totRevs.increment();


		pl.itemsName = "nodes";
		pl.info = new Object() {
			@Override
			public String toString() {
				return "[nodes: " + result.size() + " products: " + product2Revs.size() + " revisions: " + totRevs.getValue() + "]";
			}
		};

		pl.start("Visiting reachable nodes...");

		while (!queue.isEmpty()) {
			final long node = queue.dequeueLong();

			for (final long s : kb.successors(node)) if (!result.contains(s)) {
				p = kb.callGraphs.get(index(s)).product;
				final long gid = gid(s);
				if (badGIDs.contains(gid)) continue;
				final String targetNameSpace = kb.new Node(gid, index(s)).toFastenURI().getRawNamespace();
				if (targetNameSpace.startsWith("java.") || targetNameSpace.startsWith("javax.") || targetNameSpace.startsWith("jdk.")) {
					badGIDs.add(gid);
					continue;
				}
				revs = product2Revs.get(p);
				if (revs == null) product2Revs.put(p, revs = new IntOpenHashSet());
				if (revs.contains(index(s)) || revs.size() < maxRevs) {
					queue.enqueue(s);
					result.add(s);
					//System.out.println(kb.new Node(gid(node), index(node)).toFastenURI() + " -> " + kb.new Node(gid(s), index(s)).toFastenURI());
					if (revs.add(index(s))) totRevs.increment();
				}
			}
			pl.lightUpdate();
		}

		pl.done();
		return new Result(result, product2Revs.size(), totRevs.getValue().longValue());
	}

	public static Result coreaches(final KnowledgeBase kb, final long startSig, final int maxRevs, final ProgressLogger pl) {
		final LongOpenHashSet result = new LongOpenHashSet();
		final Object2ObjectOpenHashMap<String, IntOpenHashSet> product2Revs = new Object2ObjectOpenHashMap<>();
		final MutableLong totRevs = new MutableLong();

		// Visit queue
		final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		queue.enqueue(startSig);
		result.add(startSig);

		String p = kb.callGraphs.get(index(startSig)).product;
		IntOpenHashSet revs = new IntOpenHashSet();
		revs.add(index(startSig));
		product2Revs.put(p, revs);
		totRevs.increment();


		pl.itemsName = "nodes";
		pl.info = new Object() {
			@Override
			public String toString() {
				return "[nodes: " + result.size() + " products: " + product2Revs.size() + " revisions: " + totRevs.getValue() + "]";
			}
		};
		pl.start("Visiting coreachable nodes...");
		while (!queue.isEmpty()) {
			final long node = queue.dequeueLong();

			for (final long s : kb.predecessors(node)) if (!result.contains(s)) {
				p = kb.callGraphs.get(index(s)).product;
				final String targetNameSpace = kb.new Node(gid(s), index(s)).toFastenURI().getRawNamespace();
				if (targetNameSpace.startsWith("java.") || targetNameSpace.startsWith("javax.") || targetNameSpace.startsWith("jdk.")) continue;
				revs = product2Revs.get(p);
				if (revs == null) product2Revs.put(p, revs = new IntOpenHashSet());
				if (revs.contains(index(s)) || revs.size() < maxRevs) {
					queue.enqueue(s);
					result.add(s);
					//System.out.println(kb.new Node(gid(node), index(node)).toFastenURI() + " -> " + kb.new Node(gid(s), index(s)).toFastenURI());
					if (revs.add(index(s))) totRevs.increment();
				}
			}
			pl.lightUpdate();
		}

		pl.done();
		return new Result(result, product2Revs.size(), totRevs.getValue().longValue());
	}

	public static int reachable(final ImmutableGraph graph, final int startingNode) {
		final int n = graph.numNodes();
		final boolean[] known = new boolean[n];
		final IntArrayFIFOQueue queue = new IntArrayFIFOQueue();

		queue.enqueue(startingNode);
		known[startingNode] = true;
		int visited = 0;

		while (!queue.isEmpty()) {
			final int currentNode = queue.dequeueInt();
			visited++;
			final LazyIntIterator iterator = graph.successors(currentNode);
			for (int succ; (succ = iterator.nextInt()) != -1;) {
				if (!known[succ]) {
					known[succ] = true;
					queue.enqueue(succ);
				}
			}
		}

		return visited;
	}

	public static void main(final String[] args) throws JSAPException, ClassNotFoundException, RocksDBException, IOException {
		final SimpleJSAP jsap = new SimpleJSAP(GlobalVisitStats.class.getName(),
				"Computes (co)reachable set statistics for a prototype knowledge base.",
				new Parameter[] {
						new FlaggedOption("maxRevsF", JSAP.INTEGER_PARSER, Integer.toString(Integer.MAX_VALUE), JSAP.NOT_REQUIRED, 'f', "max-revs-f", "The maximum number of revision per product during the forward visits."),
						new FlaggedOption("maxRevsB", JSAP.INTEGER_PARSER, Integer.toString(Integer.MAX_VALUE), JSAP.NOT_REQUIRED, 'b', "max-revs-b", "The maximum number of revision per product during the backward visits."),
						new FlaggedOption("n", JSAP.INTEGER_PARSER, "1", JSAP.NOT_REQUIRED, 'n', "n", "The the number of starting nodes for visits."),
						new FlaggedOption("p", JSAP.INTEGER_PARSER, "1", JSAP.NOT_REQUIRED, 'p', "p", "The the number of starting pairs for visits."),
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
						new UnflaggedOption("kbmeta", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The file containing the knowledge base metadata." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final int n = jsapResult.getInt("n");
		final int p = jsapResult.getInt("p");
		final int maxRevsF = jsapResult.getInt("maxRevsF");
		final int maxRevsB = jsapResult.getInt("maxRevsB");
		final String kbDir = jsapResult.getString("kb");
		if (!new File(kbDir).exists()) throw new IllegalArgumentException("No such directory: " + kbDir);
		final String kbMetadataFilename = jsapResult.getString("kbmeta");
		if (!new File(kbMetadataFilename).exists()) throw new IllegalArgumentException("No such file: " + kbMetadataFilename);
		LOGGER.info("Loading KnowledgeBase metadata");
		final KnowledgeBase kb = KnowledgeBase.getInstance(kbDir, kbMetadataFilename, true);
		LOGGER.info("Number of graphs: " + kb.callGraphs.size());

		final ProgressLogger pl = new ProgressLogger();

		final SummaryStats reachable = new SummaryStats();
		final SummaryStats coreachable = new SummaryStats();
		final SummaryStats reachableProducts = new SummaryStats();
		final SummaryStats coreachableProducts = new SummaryStats();
		final SummaryStats reachableRevs = new SummaryStats();
		final SummaryStats coreachableRevs = new SummaryStats();

		final XoRoShiRo128PlusPlusRandom random = new XoRoShiRo128PlusPlusRandom(0);
		final long[] callGraphIndex = kb.callGraphs.keySet().toLongArray();

		final ProgressLogger pl2 = new ProgressLogger(LOGGER);

		pl.expectedUpdates = n;
		pl.itemsName = "nodes";
		pl.start("Enumerating nodes");
		for (int i = 0; i < n; i++) {
			pl.update();
			final int index = random.nextInt(callGraphIndex.length);
			final CallGraph callGraph = kb.callGraphs.get(callGraphIndex[index]);
			final CallGraphData callGraphData = callGraph.callGraphData();
			final int startNode = random.nextInt(callGraph.nInternal);
			final Node node = kb.new Node(callGraphData.LID2GID[startNode], index);
			LOGGER.info("Analyzing node " + node.toFastenURI());
			final Result reaches = reaches(kb, node.signature(), maxRevsF, pl2);
			reachable.add(reaches.nodes.size());
			reachableProducts.add(reaches.numProducts);
			reachableRevs.add(reaches.numRevs);
			final Result coreaches = coreaches(kb, node.signature(), maxRevsB, pl2);
			coreachable.add(coreaches.nodes.size());
			coreachableProducts.add(coreaches.numProducts);
			coreachableRevs.add(coreaches.numRevs);
		}
		pl.done();

		final SummaryStats reachableIntersection = new SummaryStats();
		final SummaryStats coreachableIntersection = new SummaryStats();

		pl.expectedUpdates = p;
		pl.itemsName = "pairs";
		pl.start("Enumerating pairs");
		for (int i = 0; i < p; i++) {
			pl.update();
			final int index0 = random.nextInt(callGraphIndex.length);
			final int index1 = random.nextInt(callGraphIndex.length);
			final CallGraph callGraph0 = kb.callGraphs.get(callGraphIndex[index0]);
			final CallGraphData callGraphData0 = callGraph0.callGraphData();
			final CallGraph callGraph1 = kb.callGraphs.get(callGraphIndex[index1]);
			final CallGraphData callGraphData1 = callGraph1.callGraphData();
			final int startNode0 = random.nextInt(callGraph0.nInternal);
			final int startNode1 = random.nextInt(callGraph1.nInternal);
			final Node node0 = kb.new Node(callGraphData0.LID2GID[startNode0], index0);
			final Node node1 = kb.new Node(callGraphData1.LID2GID[startNode1], index1);
			LOGGER.info("Analyzing pair (" + node0.toFastenURI() + ", " + node1.toFastenURI() + ")");
			final Result reaches0 = reaches(kb, node0.signature(), maxRevsF, pl2);
			final Result reaches1 = reaches(kb, node1.signature(), maxRevsF, pl2);
			final Result coreaches0 = coreaches(kb, node0.signature(), maxRevsB, pl2);
			final Result coreaches1 = coreaches(kb, node1.signature(), maxRevsB, pl2);
			reaches0.nodes.retainAll(reaches1.nodes);
			reachableIntersection.add(reaches0.nodes.size());
			coreaches0.nodes.retainAll(coreaches1.nodes);
			coreachableIntersection.add(coreaches0.nodes.size());
		}
		pl.done();

		LOGGER.info("Closing KnowledgeBase");
		kb.close();
		System.out.println("Forward visit nodes:         \t" + reachable);
		System.out.println("Forward visit products:      \t" + reachableProducts);
		System.out.println("Forward visit revisions:     \t" + reachableRevs);
		System.out.println("Forward visit intersection:  \t" + reachableIntersection);
		System.out.println("Backward visit nodes:        \t" + coreachable);
		System.out.println("Backward visit products:     \t" + coreachableProducts);
		System.out.println("Backward visit revisions:    \t" + coreachableRevs);
		System.out.println("Backward visit intersection: \t" + coreachableIntersection);
	}
}
