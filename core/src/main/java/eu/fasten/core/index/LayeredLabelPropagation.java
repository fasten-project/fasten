package eu.fasten.core.index;

/*
 * Copyright (C) 2010-2020 Paolo Boldi, Massimo Santini and Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.apache.commons.lang.mutable.MutableDouble;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.fastutil.ints.AbstractInt2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.law.graph.DFS;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;
import it.unimi.dsi.webgraph.Transform;
import it.unimi.dsi.webgraph.algo.EliasFanoCumulativeOutdegreeList;


// RELEASE-STATUS: DIST

/** An implementation of the <em>layered label propagation</em> algorithm described by
 * by Paolo Boldi, Sebastiano Vigna, Marco Rosa, Massimo Santini, and Sebastiano Vigna in &ldquo;Layered label propagation:
 * A multiresolution coordinate-free ordering for compressing social networks&rdquo;,
 * <i>Proceedings of the 20th international conference on World Wide Web</i>, pages 587&minus;596, ACM, 2011.
 *
 * <p>The method {@link #computePermutation(double[], String, int)} returns a permutation of the original
 * <em>symmetric</em> graph provided with the {@linkplain #LayeredLabelPropagation(ImmutableGraph, int[], long, boolean) constructor}
 * which will (hopefully) increase locality (see the paper). Usually, the permutation is fed to
 * {@link Transform#mapOffline(ImmutableGraph, int[], int, File, ProgressLogger)} to permute the original graph.
 *
 * <p>Note that the graph provided must be <em>symmetric</em> and <em>loopless</em>. If this is not the case,
 * please use {@link Transform#symmetrizeOffline(ImmutableGraph, int, File, ProgressLogger)} and possibly
 * {@link Transform#filterArcs(ImmutableGraph, it.unimi.dsi.webgraph.Transform.ArcFilter, ProgressLogger)} with
 * filter {@link Transform#NO_LOOPS} to generate a suitable graph.
 *
 * <p>This class can also be used to run just label propagation over a given graph to
 * get the {@linkplain #computeLabels(double, int) labels assigned to the nodes} for a fixed &gamma;.
 *
 * <h2>Memory requirements</h2>
 *
 * <p>This class requires 13 bytes per node (three integers and a boolean), plus the memory
 * that is necessary to load the graph, which however can be just
 * {@link ImmutableGraph#loadMapped(CharSequence, ProgressLogger) memory-mapped}.
 *
 * <p>Note that the main method will warm up the algorithm by performing a {@linkplain DFS depth-first visit}
 * if the graph is not mapped. The visit will require storing an additional array of integers.
 *
 * @author Paolo Boldi
 * @author Marco Rosa
 * @author Massimo Santini
 * @author Sebastiano Vigna
 */

public class LayeredLabelPropagation {

	private final static Logger LOGGER = LoggerFactory.getLogger(LayeredLabelPropagation.class);

	/** The list of default &gamma; values. It must be kept in sync with the {@link #main(String[])} default parameters. */
	public static final double[] DEFAULT_GAMMAS = { 1., 1./2, 1./4, 1./8, 1./16, 1./32, 1./64, 1./128, 1./256, 1./512, 1./1024, 0 };

	/** The format used to print &gamma;'s. */
	private static final DecimalFormat GAMMA_FORMAT = new java.text.DecimalFormat("0.############");

	/** The default maximum number of updates. */
	public static final int MAX_UPDATES = 100;

	/** The minimum gain in the Hamiltonian. Under this threshold we stop. */
	private final static double GAIN_TRESHOLD = 0.001;

	/** The update list will be shuffled by blocks of this size, to ensure some locality. */
	private static final int SHUFFLE_GRANULARITY = 100000;

	/** A symmetric, loopless graph. */
	private final ImmutableGraph symGraph;

	/** The number of nodes of {@link #symGraph}. */
	private final int n;

	/** The label of each node. After a call to {@link #computePermutation(int, double[], String)}
	 * this field contains the final list of labels. */
	private AtomicIntegerArray label;

	/** Volume of each current cluster, indexed by label (many will be zeroes). */
	private final AtomicIntegerArray volume;

	/** The chosen update order. */
	private final int[] updateList;

	/** The objective function (Hamiltonian of the potts model). */
	private final double[] objectiveFunction;

	/** The objective function (Hamiltonian of the potts model). */
	private final MutableDouble gapCost;

	/** The random-number generator. */
	private final XoRoShiRo128PlusRandom r;

	/** The basename of temporary files containing labellings for various &gamma;'s. */
	private File labelling;

	/** If true, the user has set a basename for label files, and such files must not be deleted. */
	private boolean labelBasenameSet;

	/** A virtual permutation applied to the graph, or {@code null} for no permutation. */
	private final int[] startPerm;

	/** Whether to perform an exactly reproducible run in case {@link #startPerm} is not {@code null} (slower). */
	private final boolean exact;

	/** The number of threads used in the computation. */
	private final int numberOfThreads;

	/** The random seed. */
	private final long seed;

	/** For each note, true iff at least one of the successors changed its label. */
	private final boolean[] canChange;

	/** The number of nodes that changed their label in the current iteration. */
	private final AtomicInteger modified;

	/** A simple exception handler that stores the thrown exception in {@link #threadException}. */
	private final SimpleUncaughtExceptionHandler simpleUncaughtExceptionHandler;

	/** One of the throwables thrown by some of the threads, if at least one thread has thrown a throwable. */
	private volatile Throwable threadException;

	/** The current update. */
	private int update;

	/** The starting node of the next chunk of nodes to be processed. */
	protected int nextNode;
	/** The number of arcs before {@link #nextNode}. */
	protected long nextArcs;
	/** The outdegrees cumulative function. */
	protected final EliasFanoCumulativeOutdegreeList cumulativeOutdegrees;


	/** Creates a new instance.
	 *
	 * @param symGraph a symmetric, loopless graph.
	 * @param seed a random seed.
	 */
	public LayeredLabelPropagation(final ImmutableGraph symGraph, final long seed) throws IOException {
		this(symGraph, null, seed, false);
	}


	/** Creates a new instance using a specific initial permutation.
	 *
	 * @param symGraph a symmetric, loopless graph.
	 * @param startPerm an initial permutation of the graph, or {@code null} for no permutation.
	 * @param seed a random seed.
	 */
	public LayeredLabelPropagation(final ImmutableGraph symGraph, final int[] startPerm, final long seed) throws IOException {
		this(symGraph, startPerm, seed, false);
	}

	/** Creates a new instance using a specific initial permutation.
	 *
	 * <p>If <code>exact</code> is true, the final permutation is
	 * <em>exactly</em> the same as if you first permute the graph with <code>startPerm</code> and
	 * then apply LLP with an {@code null} starting permutation.
	 *
	 * @param symGraph a symmetric, loopless graph.
	 * @param startPerm an initial permutation of the graph, or {@code null} for no permutation.
	 * @param seed a random seed.
	 * @param exact a boolean flag that forces the algorithm to run exactly.
	 */
	public LayeredLabelPropagation(final ImmutableGraph symGraph, final int[] startPerm, final long seed, final boolean exact) throws IOException {
		this(symGraph, startPerm, 0, seed, exact);
	}

	/** Creates a new instance using a specific initial permutation and specified number of threads.
	 *
	 * <p>If <code>exact</code> is true, the final permutation is
	 * <em>exactly</em> the same as if you first permute the graph with <code>startPerm</code> and
	 * then apply LLP with an {@code null} starting permutation.
	 *
	 * @param symGraph a symmetric, loopless graph.
	 * @param startPerm an initial permutation of the graph, or {@code null} for no permutation.
	 * @param numberOfThreads the number of threads to be used (0 for automatic sizing).
	 * @param seed a random seed.
	 * @param exact a boolean flag that forces the algorithm to run exactly.
	 */
	public LayeredLabelPropagation(final ImmutableGraph symGraph, final int[] startPerm, final int numberOfThreads, final long seed, final boolean exact) throws IOException {
		this.symGraph = symGraph;
		this.n = symGraph.numNodes();
		this.startPerm = startPerm;
		this.seed = seed;
		this.r = new XoRoShiRo128PlusRandom(seed);
		this.exact = exact;
		this.label = new AtomicIntegerArray(n);
		this.volume = new AtomicIntegerArray(n);
		cumulativeOutdegrees = new EliasFanoCumulativeOutdegreeList(symGraph, symGraph.numArcs(), 1);

		this.gapCost = new MutableDouble();
		this.updateList = Util.identity(n);
		simpleUncaughtExceptionHandler = new SimpleUncaughtExceptionHandler();
		labelling = File.createTempFile(this.getClass().getName(), "labelling");
		labelling.deleteOnExit();

		this.numberOfThreads = numberOfThreads != 0 ? numberOfThreads : Runtime.getRuntime().availableProcessors();
		this.canChange = new boolean[n];
		this.modified = new AtomicInteger(0);
		this.objectiveFunction = new double[this.numberOfThreads];
	}


	/**
	 * Sets the basename for label files.
	 *
	 * @param labelBasename basename for label files.
	 */
	public void labelBasename(final String labelBasename) {
		labelBasenameSet = true;
		labelling = new File(labelBasename);
	}


	/**
	 * Combines two labellings devilishly into a new one.
	 *
	 * @param label the minor label; the result will be stored here.
	 * @param major the major label.
	 * @param perm a virtual permutation applied to the graph, or {@code null} for no permutation.
	 * @param support a support array.
	 * @return the resulting number of labels.
	 */
	private static int combine(final int[] label, final int[] major, final int[] perm, final int[] support) {
		final int n = label.length;
		if (n == 0) return 0;
		if (n != major.length) throw new IllegalArgumentException();

		Util.identity(support);

		if (perm == null) IntArrays.mergeSort(support, 0, n, (a, b) -> {
			int t = label[major[a]] - label[major[b]];
			if (t != 0) return t;
			t = major[a] - major[b];
			return t != 0 ? t : label[a] - label[b];
		});
		else IntArrays.mergeSort(support, 0, n, (a, b) -> {
			int t = label[major[a]] - label[major[b]];
			if (t != 0) return t;
			t = perm[major[a]] - perm[major[b]];
			return t != 0 ? t : label[a] - label[b];
		});


		int currMinor = label[support[0]];
		int currMajor = major[support[0]];
		int curr = 0;
		label[support[0]] = curr;

		for (int i = 1; i < n; i++) {
			final int t = support[i];
			final int u = label[t];
			if (major[t] != currMajor || u != currMinor) {
				currMinor = u;
				currMajor = major[t];
				curr++;
			}

			label[t] = curr;
		}

		return ++curr;

	}

	/** A minimal implementation of a set of counters using a hash table without rehashing. */
	private final static class OpenHashTableCounter {
		/** The keys. Always sized as a power of two. */
		private int[] key;
		/** The counters associated to {@link #key}. */
		private int[] count;
		/** Keeps track of the location of each key. Useful for linear-time iteration over the key/value pairs. */
		private int[] location;
		/** The mask used to compute the key locations. */
		private int mask;
		/** The number of keys in the table. */
		private int n;

		public OpenHashTableCounter() {
			mask = -1;
			count = IntArrays.EMPTY_ARRAY;
			key = IntArrays.EMPTY_ARRAY;
			location = IntArrays.EMPTY_ARRAY;
		}

		public void incr(final int k) {
			int pos = (k * 2056437379) & mask;
			while (count[pos] != 0 && key[pos] != k)
				pos = (pos + 1) & mask;
			if (count[pos]++ == 0) {
				key[pos] = k;
				location[n++] = pos;
			}
		}

		public boolean containsKey(final int k) {
			int pos = (k * 2056437379) & mask;
			while (count[pos] != 0 && key[pos] != k)
				pos = (pos + 1) & mask;
			return count[pos] != 0;
		}

		// After a call to this method, incr() cannot be called anymore.
		public void addZeroCount(final int k) {
			int pos = (k * 2056437379) & mask;
			while (count[pos] != 0 && key[pos] != k)
				pos = (pos + 1) & mask;
			if (count[pos] == 0) {
				key[pos] = k;
				location[n++] = pos;
			}
		}

		private final static class Entry extends AbstractInt2IntMap.BasicEntry {
			public Entry() {
				super(0, 0);
			}

			public void setKey(final int key) {
				this.key = key;
			}

			@Override
			public int setValue(final int value) {
				this.value = value;
				return -1; // Violates the interface, but it's all internal.
			}
		}

		public Iterator<Int2IntMap.Entry> entries() {
			return new ObjectIterator<>() {
				private int i;

				private final Entry entry = new Entry();

				@Override
				public boolean hasNext() {
					return i < n;
				}

				@Override
				public Entry next() {
					if (!hasNext()) throw new NoSuchElementException();
					final int l = location[i++];
					entry.setKey(key[l]);
					entry.setValue(count[l]);
					return entry;
				}
			};
		}

		public void clear(final int size) {
			if (mask + 1 < (1 << (Fast.ceilLog2(size) + 1))) {
				mask = (1 << (Fast.ceilLog2(size) + 1)) - 1;
				count = new int[mask + 1];
				key = new int[mask + 1];
				location = new int[mask + 1];
			}
			else while (n-- != 0) count[location[n]] = 0;
			n = 0;
		}
	}

	private final class GapCostThread extends Thread {
		@SuppressWarnings("hiding")
		private final ImmutableGraph symGraph;

		/** The permutation whose cost is to be evaluated. */
		private final int[] perm;

		private GapCostThread(final ImmutableGraph symGraph, final int[] perm) {
			this.symGraph = symGraph;
			this.perm = perm;
		}

		@Override
		public void run() {
			final ImmutableGraph symGraph = this.symGraph;
			final int numNodes = LayeredLabelPropagation.this.n;
			final long numArcs = LayeredLabelPropagation.this.symGraph.numArcs();
			final int[] perm = this.perm;
			int[] permutedSuccessors = new int[32];
			int[] successors;
			final long granularity = Math.max(1024, numArcs >>> 9);
			int start, end;

			double gapCost = 0;
			for (;;) {

				// Try to get another piece of work.
				synchronized(LayeredLabelPropagation.this.cumulativeOutdegrees) {
					if (nextNode == numNodes) {
						LayeredLabelPropagation.this.gapCost.add(gapCost);
						break;
					}
					start = nextNode;
					final long target = nextArcs + granularity;
					if (target >= numArcs) nextNode = numNodes;
					else {
						nextArcs = cumulativeOutdegrees.skipTo(target);
						nextNode = cumulativeOutdegrees.currentIndex();
					}
					end = nextNode;
				}

				final NodeIterator nodeIterator = symGraph.nodeIterator(start);
				for (int i = start; i < end; i++) {
					nodeIterator.nextInt();
					final int node = perm[i];
					final int outdegree = nodeIterator.outdegree();
					if (outdegree > 0) {
						successors = nodeIterator.successorArray();
						permutedSuccessors = IntArrays.grow(permutedSuccessors, outdegree);
						for (int j = outdegree; j-- != 0;)
							permutedSuccessors[j] = perm[successors[j]];
						IntArrays.quickSort(permutedSuccessors, 0, outdegree);
						int prev = node;
						for (int j = 0; j < outdegree; j++) {
							gapCost += Fast.ceilLog2(Math.abs(prev - permutedSuccessors[j]));
							prev = permutedSuccessors[j];
						}
					}
				}
			}
		}
	}

	private final class IterationThread extends Thread {
		@SuppressWarnings("hiding")
		private final ImmutableGraph symGraph;

		/** The current value of &gamma;. */
		private final double gamma;

		/** A progress logger. */
		private final ProgressLogger pl;

		private final int index;

		private IterationThread(final ImmutableGraph symGraph, final double gamma, final int index, final ProgressLogger pl) {
			this.symGraph = symGraph;
			this.gamma = gamma;
			this.index = index;
			this.pl = pl;
		}

		@Override
		public void run() {
			final XoRoShiRo128PlusRandom r = new XoRoShiRo128PlusRandom(LayeredLabelPropagation.this.seed);
			final AtomicIntegerArray label = LayeredLabelPropagation.this.label;
			final AtomicIntegerArray volume = LayeredLabelPropagation.this.volume;
			final ImmutableGraph symGraph = this.symGraph;
			final int numNodes = LayeredLabelPropagation.this.n;
			final long numArcs = LayeredLabelPropagation.this.symGraph.numArcs();
			final int[] updateList = LayeredLabelPropagation.this.updateList;
			final int[] startPerm = LayeredLabelPropagation.this.startPerm;
			final boolean[] canChange = LayeredLabelPropagation.this.canChange;
			final boolean exact = LayeredLabelPropagation.this.exact;
			final double gamma = this.gamma;
			final long granularity = Math.max(1024, numArcs >>> 9);

			int start, end;
			double delta = LayeredLabelPropagation.this.objectiveFunction[index];

			for (;;) {

				// Try to get another piece of work.
				synchronized(LayeredLabelPropagation.this.cumulativeOutdegrees) {
					if (nextNode == numNodes) {
						LayeredLabelPropagation.this.objectiveFunction[index] = delta;
						break;
					}
					start = nextNode;
					final long target = nextArcs + granularity;
					if (target >= numArcs) nextNode = numNodes;
					else {
						nextArcs = cumulativeOutdegrees.skipTo(target);
						nextNode = cumulativeOutdegrees.currentIndex();
					}
					end = nextNode;
				}

				final OpenHashTableCounter map = new OpenHashTableCounter();

				for (int i = start; i < end; i++) {
					final int node = updateList[i];

					/** Note that here we are using a heuristic optimisation: if no neighbour has changed,
					 *  the label of a node cannot change. If gamma != 0, this is not necessarily true,
					 *  as a node might need to change its value just because of a change of volume of
					 *  the adjacent labels. */

					if (canChange[node]) {
						canChange[node] = false;
						final int outdegree = symGraph.outdegree(node);
						if (outdegree > 0) {
							final int currentLabel = label.get(node);
							volume.decrementAndGet(currentLabel);

							map.clear(outdegree);
							LazyIntIterator successors = symGraph.successors(node);
							for (int j = outdegree; j-- != 0;) map.incr(label.get(successors.nextInt()));

							if (!map.containsKey(currentLabel)) map.addZeroCount(currentLabel);

							double max = Double.NEGATIVE_INFINITY;
							double old = 0;
							final IntArrayList majorities = new IntArrayList();

							for (final Iterator<Int2IntMap.Entry> entries = map.entries(); entries.hasNext();) {
								final Int2IntMap.Entry entry = entries.next();
								final int l = entry.getIntKey();
								final int freq = entry.getIntValue(); // Frequency of label in my
								// neighbourhood
								final double val = freq - gamma * (volume.get(l) + 1 - freq);

								if (max == val) majorities.add(l);

								if (max < val) {
									majorities.clear();
									max = val;
									majorities.add(l);
								}

								if (l == currentLabel) old = val;
							}

							if (exact) {
								if (startPerm != null) IntArrays.quickSort(majorities.elements(), 0, majorities.size(), (a, b) -> startPerm[a] - startPerm[b]);
								else IntArrays.quickSort(majorities.elements(), 0, majorities.size());
							}


							// Extract a label from the majorities
							final int nextLabel = majorities.getInt(r.nextInt(majorities.size()));
							if (nextLabel != currentLabel) {
								modified.addAndGet(1);
								successors = symGraph.successors(node);
								for (int j = outdegree; j-- != 0;) canChange[successors.nextInt()] = true;
							}
							label.set(node, nextLabel);
							volume.incrementAndGet(nextLabel);

							delta += max - old;
						}
					}
				}
				synchronized (pl) {
					pl.update(end - start);
				}
			}
		}
	}



	private final class SimpleUncaughtExceptionHandler implements UncaughtExceptionHandler {
		@Override
		public void uncaughtException(final Thread t, final Throwable e) {
			threadException = e;
		}
	}

	private void update(final double gamma) {
		final int n = this.n;
		final int[] updateList = this.updateList;
		modified.set(0);
		nextArcs = nextNode = 0;

		if (exact) {
			if (startPerm == null) Util.identity(updateList);
			else Util.invertPermutation(startPerm, updateList);
		}

		// Local shuffle
		for(int i = 0; i < n;) IntArrays.shuffle(updateList, i, Math.min(i += SHUFFLE_GRANULARITY, n), r);

		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.expectedUpdates = n;
		pl.logInterval = ProgressLogger.TEN_SECONDS;
		pl.itemsName = "nodes";
		pl.start("Starting update " + update + "...");

		final Thread[] thread = new Thread[numberOfThreads];

		nextArcs = nextNode =  0;
		for (int i = 0; i < numberOfThreads; i++) {
			thread[i] = new IterationThread(symGraph.copy(), gamma, i, pl);
			thread[i].setUncaughtExceptionHandler(simpleUncaughtExceptionHandler);
			thread[i].start();
		}

		for (int i = 0; i < numberOfThreads; i++)
			try {
				thread[i].join();
			}
			catch (final InterruptedException e) {
				throw new RuntimeException(e);
			}

		if (threadException != null) throw new RuntimeException(threadException);
		pl.done();
	}



	private void computeGapCost(final int[] newPerm) {
		final int[] startPerm = this.startPerm;
		final AtomicIntegerArray label = this.label;

		Util.identity(newPerm);
		if (startPerm != null) IntArrays.quickSort(newPerm, (x, y) -> {
			final int t = startPerm[label.get(x)] - startPerm[label.get(y)];
			return t != 0 ? t : startPerm[x] - startPerm[y];
		});
		else IntArrays.quickSort(newPerm, (x, y) -> {
			final int t = label.get(x) - label.get(y);
			return t != 0 ? t : x - y;
		});

		Util.invertPermutationInPlace(newPerm);

		final Thread[] thread = new Thread[numberOfThreads];

		nextArcs = nextNode =  0;
		for (int i = 0; i < numberOfThreads; i++) (thread[i] = new GapCostThread(symGraph.copy(), newPerm)).start();

		for (int i = 0; i < numberOfThreads; i++)
			try {
				thread[i].join();
			}
			catch (final InterruptedException e) {
				throw new RuntimeException(e);
			}
	}


	private double objectiveFunction() {
		double res = 0;
		for (final double d : objectiveFunction) res += d;
		return res;
	}

	private void init() {
		for (int i = 0; i < n; i++) {
			label.set(i, i);
			volume.set(i, 1);
			canChange[i] = true;
			updateList[i] = i;
		}
		for (int i = 0; i < numberOfThreads; i++) objectiveFunction[i] = 0;
	}

	/**
	 * Computes the labels of a graph for a given value of &gamma; using the {@linkplain #MAX_UPDATES default maximum number of updates}.
	 *
	 * @param gamma the gamma parameter.
	 * @return the labels.
	 */
	public AtomicIntegerArray computeLabels(final double gamma) {
		return computeLabels(gamma, MAX_UPDATES);
	}
	/**
	 * Computes the labels of a graph for a given value of &gamma;.
	 *
	 * @param gamma the gamma parameter.
	 * @param maxUpdates the maximum number of updates performed.
	 * @return the labels.
	 */
	public AtomicIntegerArray computeLabels(final double gamma, final int maxUpdates) {
		init();
		final String gammaFormatted = GAMMA_FORMAT.format(gamma);
		double prevObjFun = 0;
		double gain = 0;
		final ProgressLogger pl = new ProgressLogger(LOGGER, "updates");
		pl.logger().info("Running " + this.numberOfThreads + " threads");
		pl.start("Starting iterations with gamma=" + gammaFormatted + "...");

		update = 0;

		do {
			prevObjFun = objectiveFunction();
			update(gamma);
			pl.updateAndDisplay();
			gain = 1 - (prevObjFun / objectiveFunction());
			LOGGER.info("Gain: " + gain);
			LOGGER.info("Modified: " + modified.get());
			update++;
		} while (modified.get() > 0 && gain > GAIN_TRESHOLD && update < maxUpdates);

		pl.done();

		return label;
	}

	/**
	 * Computes the final permutation of the graph  using the {@linkplain #MAX_UPDATES default maximum number of updates} and
	 * the {@linkplain #DEFAULT_GAMMAS default gammas}.
	 *
	 * @param cluster if not {@code null}, clusters will be saved to a file with this name.
	 * @return the final permutation of the graph.
	 */
	public int[] computePermutation(final String cluster) throws IOException {
		return computePermutation(DEFAULT_GAMMAS, cluster, MAX_UPDATES);
	}

	/**
	 * Computes the final permutation of the graph  using the {@linkplain #MAX_UPDATES default maximum number of updates}.
	 *
	 * @param gammas a set of parameters that will be used to generate labellings.
	 * @param cluster if not {@code null}, clusters will be saved to a file with this name.
	 * @return the final permutation of the graph.
	 */
	public int[] computePermutation(final double[] gammas, final String cluster) throws IOException {
		return computePermutation(gammas, cluster, MAX_UPDATES);
	}

	/**
	 * Computes the final permutation of the graph.
	 *
	 * @param gammas a set of parameters that will be used to generate labellings.
	 * @param cluster if not {@code null}, clusters will be saved to a file with this name.
	 * @param maxUpdates the maximum number of updates performed.
	 * @return the final permutation of the graph.
	 */
	public int[] computePermutation(final double[] gammas, final String cluster, final int maxUpdates) throws IOException {
		final int n = this.n;
		final int m = gammas.length;

		final double[] gapCosts = new double[m];

		final ProgressLogger plGammas = new ProgressLogger(LOGGER);
		plGammas.itemsName = "gammas";
		plGammas.expectedUpdates = m;
		plGammas.start();

		for (int index = 0; index < m; index++) {
			init();
			final double gamma = gammas[index];
			final String gammaFormatted = GAMMA_FORMAT.format(gamma);
			double prevObjFun = 0;
			double gain = 0;

			final ProgressLogger pl = new ProgressLogger(LOGGER, "updates");
			pl.logger().info("Running " + this.numberOfThreads + " threads");
			pl.start("Starting iterations with gamma=" + gammaFormatted + " (" + (index + 1) + "/" + m + ") ...");

			update = 0;

			do {
				prevObjFun = objectiveFunction();
				update(gamma);
				pl.updateAndDisplay();
				gain = 1 - (prevObjFun / objectiveFunction());
				LOGGER.info("Gain: " + gain);
				LOGGER.info("Modified: " + modified.get());
				update++;
			} while (modified.get() > 0 && gain > GAIN_TRESHOLD && update < maxUpdates);

			pl.done();

			final int length = label.length();
			final DataOutputStream dos = new DataOutputStream(new FastBufferedOutputStream(new FileOutputStream(labelling + "-" + index)));
			for (int i = 0; i < length; i++)
				dos.writeInt(label.get(i));
			dos.close();

			if (!labelBasenameSet) new File(labelling + "-" + index).deleteOnExit();


			gapCost.setValue(0);

			computeGapCost(updateList);
			gapCosts[index] = gapCost.doubleValue();
			LOGGER.info("Completed iteration with gamma " + gammaFormatted + " (" + (index + 1) + "/" + m + ") , gap cost: " + gapCost.doubleValue());
			plGammas.updateAndDisplay();
		}
		plGammas.done();

		label = null; // We no longer need the atomic list

		final int[] best = Util.identity(m);
		IntArrays.quickSort(best, 0, best.length, (x, y) -> (int)Math.signum(gapCosts[y] - gapCosts[x]));

		final int bestGamma = best[m - 1];
		LOGGER.info("Best gamma: " + GAMMA_FORMAT.format(gammas[bestGamma]) + "\twith GapCost: " + gapCosts[bestGamma]);
		LOGGER.info("Worst gamma: " + GAMMA_FORMAT.format(gammas[best[0]]) + "\twith GapCost: " + gapCosts[best[0]]);


		final int intLabel[] = BinIO.loadInts(labelling + "-" + bestGamma);
		if (startPerm != null) for (int i = 0; i < n; i++) intLabel[i] = startPerm[intLabel[i]];


		for (int step = 0; step < m; step++) {
			LOGGER.info("Starting step " + step + "...");
			int[] major = BinIO.loadInts(labelling + "-" + best[step]);
			combine(intLabel, major, startPerm, updateList);
			major = BinIO.loadInts(labelling + "-" + bestGamma);
			final int numberOflabels = combine(intLabel, major, startPerm, updateList);
			LOGGER.info("Number of labels: " + numberOflabels);
			LOGGER.info("Finished step " + step);
		}


		final int[] newPerm = this.updateList; // It is no longer necessary: we reuse it.
		final int[] startPerm = this.startPerm;
		Util.identity(newPerm);
		if (startPerm == null) IntArrays.radixSortIndirect(newPerm, intLabel, true);
		else IntArrays.mergeSort(newPerm, (x, y) -> {
			final int t = intLabel[x] - intLabel[y];
			return t != 0 ? t : startPerm[x] - startPerm[y];
		});

		if (cluster != null) {
			final DataOutputStream dos = new DataOutputStream(new FastBufferedOutputStream(new FileOutputStream(cluster)));

			// Printing clusters; volume is really the best saved clustering
			BinIO.loadInts(labelling + "-" + bestGamma, intLabel);
			int current = intLabel[newPerm[0]];
			int j = 0;
			for (int i = 0; i < n; i++) {
				final int tmp = intLabel[newPerm[i]];
				if (tmp != current) {
					current = tmp;
					j++;
				}
				dos.writeInt(j);
			}
			dos.close();
		}

		Util.invertPermutationInPlace(newPerm);

		return newPerm;
	}
}
