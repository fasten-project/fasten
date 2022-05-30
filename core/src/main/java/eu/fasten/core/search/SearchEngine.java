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

package eu.fasten.core.search;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongPredicate;

import org.jooq.DSLContext;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.search.predicate.CachingPredicateFactory;
import eu.fasten.core.search.predicate.PredicateFactory;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;
import it.unimi.dsi.lang.ObjectParser;

/**
 * A class offering searching capabilities over the FASTEN knowledge base.
 *
 * <p>
 * Instances of this class access the metadata Postgres database and the RocksDB database of
 * revision call graphs. Users can interrogate the engine by providing an entry point (e.g., a
 * callable) and a {@link LongPredicate} that will be used to filter the results.
 * Reachability queries can be performed using 
 * {@link #fromCallable(long, LongPredicate, int, int, SubmissionPublisher) fromCallable()} and {@link #fromRevision(FastenURI, LongPredicate, int, int, SubmissionPublisher) fromRevision()},
 * whereas coreachability queries can be performed using 
 * {@link #toCallable(long, LongPredicate, int, int, SubmissionPublisher) toCallable()} and {@link #toRevision(FastenURI, LongPredicate, int, int, SubmissionPublisher) toRevision()}.
 * More flexibility can be achieved by calling directly {@link #to(long, LongCollection, LongPredicate, int, int, SubmissionPublisher) to()} and
 * {@link #from(long, LongCollection, LongPredicate, int, int, SubmissionPublisher) from()}, which start from an arbitrary seed.
 * Point-to-point queries (path searches) can be performed using {@link #between(long, long, LongPredicate, int, SubmissionPublisher) between()}.
 * 
 * <p>For more
 * documentation on the available filters, see {@link PredicateFactory}.
 * 
 * <p>For an explicit, detailed example of instantiation and usage of an instance of this class, see {@link SearchEngineClient}.
 * 
 * <p>
 * The resolution of a query related to a given entry point (e.g., a specific callable) yields a certain <em>universe</em>
 * (set of revisions that contain the callable in their merged graph). See Deliverable D.5 for details. 
 * Note that during a search, each merged graph was caused by a specific revision in the universe: this is
 * often called (within this class) the <em>dependent</em> that yield a certain merged graph, or that produced a specific result.
 * 
 * <p>For instance, suppose that we are looking for all the callables called by a given callable <code>fasten://mvn!foo$1.2/bar()</code>.
 * We first determine the universe of revisions where the callable <code>fasten://mvn!foo$1.2/bar()</code> may appear: they are all
 * the revisions that depend (directly or indirectly) on <code>fasten://mvn!foo$1.2/</code>.
 * Suppose that <code>fasten://mvn!goo$3.4/</code> is one of them, and that in the corresponding merged graph the callable 
 * <code>fasten://mvn!foo$1.2/bar()</code> actually appears, and one of the callable that it can reach in
 * that graph is  <code>fasten://mvn!hoo$1.10/baz()</code>; when we insert the latter in the list of result, we will
 * indicate that it was added because of <code>fasten://mvn!goo$3.4/</code> (this is the dependent that caused <code>fasten://mvn!hoo$1.10/baz()</code>
 * to appear in the list of results).
 */

public class SearchEngine implements AutoCloseable {
	/** The number of threads use in searches will be {@link java.lang.Runtime#availableProcessors()} minimized with this constant. */ 
	public static final int MAX_NUMBER_OF_THREADS = 64;
	
	/** A visitor of merged graphs: its {@link #visit(DirectedGraph, LongCollection, Revision)} method is called
	 *  on every merged graph of the universe. */
	public interface UniverseVisitor extends Closeable {
		/** Initializes the visitor.*/
		public void init();
		
		/** Visits a specific merged graph.
		 * 
		 * @param mergedGraph the merged graph to be visited; its nodes represent callable ids.
		 * @param seed the collection of nodes (callable ids) to be used in the visit.
		 * @param dependent the revision that gave rise to this merged graph.
		 */
		public void visit(final DirectedGraph mergedGraph, final LongCollection seed, Revision dependent);
		
		/** Closes the visitor. */
		@Override
		public void close();
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngine.class);
	
	/** Represents an empty graph. */
	private static final ArrayImmutableDirectedGraph NO_GRAPH = new ArrayImmutableDirectedGraph.Builder().build();


	/**
	 * A class representing results with an associated score. More precisely, a result is given by a callable (represented by its id),
	 * a score and the revision that caused that callable to appear in the first place.
	 * 
	 * <p>Results are comparable by inverse score ordering (higher scores come first). Results with the same score are compared by
	 * id.
	 */
	public final static class Result implements Comparable<Result> {
		/** The GID of a callable. */
		public long gid;
		/** The score associated to the callable during the search. */
		public double score;
		/** The dependent that generated this result. */
		public Revision dependent;

		/**
		 * Creates a {@link Result} instance with fields initialized to zero.
		 */
		public Result() {
		}

		/**
		 * Creates a {@link Result} instance using a provided GID, a score and a dependent.
		 *
		 * @param gid the GID of a callable.
		 * @param score the associated score.
		 * @param dependent the revision that caused this result to appear.
		 */
		public Result(final long gid, final double score, final Revision dependent) {
			this.gid = gid;
			this.score = score;
			this.dependent = dependent;
		}

		@Override
		public String toString() {
			return gid + " (" + String.format("%.4f", Double.valueOf(score)) + ") [dependent " + dependent.groupId + ":" + dependent.artifactId + ":" + dependent.version.toString() + "]";
		}

		@Override
		public int hashCode() {
			return (int)HashCommon.mix(gid);
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (getClass() != obj.getClass()) return false;
			final Result other = (Result)obj;
			if (gid != other.gid) return false;
			return true;
		}

		@Override
		public int compareTo(Result o) {
			final int t = Double.compare(o.score, score);
			if (t != 0) return t;
			return Long.compare(gid, o.gid);
		}
	}

	/**
	 * A class representing results in the form of a path. More precisely, a result is given by a {@link LongList} (representing the ids of the callable found along the path),
	 * and the revision that caused that callable to appear in the first place.
	 * 
	 * <p>Results are comparable by length (shortest paths come first). Results with the same length are compared lexicographically.
	 */
	public final class PathResult extends LongArrayList {
		private static final long serialVersionUID = 1L;
		
		/** The dependent that generated this result. */
		public final Revision dependent;
		
		/** Creates an empty path for the given dependent. 
		 * 
		 * @param dependent the dependent that generated this result.
		 */
		public PathResult(final Revision dependent) {
			this.dependent = dependent;
		}
		
		@Override
		public int compareTo(final LongArrayList other) {
			int t = Integer.compare(size(), other.size());
			return t != 0? t : super.compareTo(other);
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			final String sep = " -> ";
			for (long gid: this) sb.append(gid + "\t" + Util.getCallableName(gid, context) + sep);
			if (size() > 0) sb.setLength(sb.length() - sep.length());
			return sb.append("] [dependent " + dependent.groupId + ":" + dependent.artifactId + ":" + dependent.version.toString() + "]").toString();
		}	
	}

	/** The handle to the Postgres metadata database. */
	private final DSLContext context;
	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;
	/** The resolver. */
	private final GraphMavenResolver resolver;
	/** The persistent cache. */
	private final PersistentCache cache;
	/** A cache for the available revisions. */
	private final RevisionCache revisionCache;
	/** The predicate factory to be used to create predicates for this search engine. */
	private final PredicateFactory predicateFactory;
	/** The scorer that will be used to rank results. */
	private final Scorer scorer;
	/** A blacklist of GIDs that will be considered as missing. */
	private final LongOpenHashSet blacklist;

	// Note that these will not work in case of concurrent access.
	
	/** Time spent during resolution (dependency and dependents). */
	public final AtomicLong resolveTime = new AtomicLong();
	/** Time spent stitching graphs (mergeWithCHA()). */
	public final AtomicLong mergeTime = new AtomicLong();
	/** Time spent during {@linkplain #bfs visits}. */
	public final AtomicLong visitTime = new AtomicLong();
	/** The number of overall visited arcs. */
	public final AtomicLong visitedArcs = new AtomicLong();
	/** Throwables thrown by mergeWithCHA(). */
	public final List<Throwable> throwables = Collections.synchronizedList(new ArrayList<>());

	/**
	 * Creates a new search engine using a given JDBC URI, database name and path to RocksDB.
	 *
	 * @implNote This method creates a {@linkplain DSLContext context}, {@linkplain RocksDao RocksDB
	 *           DAO}, opens a {@linkplain #openCache(String) persistent cache} 
	 *           and instantiates {@linkplain Scorer scorer} using the given parameters; then, it delegates to
	 *           {@link #SearchEngine(DSLContext, RocksDao, String, Scorer)}.
	 *
	 * @param jdbcURI the JDBC URI.
	 * @param database the database name.
	 * @param rocksDb the path to the RocksDB database of revision call graphs.
	 * @param cacheDir the path to the persistent cache.
	 * @param resolverGraph the path to a serialized resolver graph (will be created if it does not
	 *            exist).
	 * @param scorer an {@link ObjectParser} specification providing a scorer; if {@code null}, a
	 *            {@link NormalizedIndegreeScorer} will be used instead.
	 * @param blacklist a blacklist of GIDs that will be considered as missing.
	 */
	public SearchEngine(final String jdbcURI, final String database, final String rocksDb, final String cacheDir, final String resolverGraph, final String scorer, final LongOpenHashSet blacklist) throws Exception {
		this(PostgresConnector.getDSLContext(jdbcURI, database, false), new RocksDao(rocksDb, true), new PersistentCache(cacheDir, false), resolverGraph, scorer == null ? NormalizedIndegreeScorer.getInstance() : ObjectParser.fromSpec(scorer, Scorer.class), blacklist);
	}

	/**
	 * Creates a new search engine using a given {@link DSLContext} and {@link RocksDao}.
	 *
	 * @param context the DSL context.
	 * @param rocksDao the RocksDB DAO.
	 * @param resolverGraph the path to a serialized resolver graph (will be created if it does not
	 *            exist).
	 * @param scorer a scorer that will be used to sort results; if {@code null}, a
	 *            {@link NormalizedIndegreeScorer} will be used instead.
	 * @param blacklist a blacklist of GIDs that will be considered as missing.
	 */
	public SearchEngine(final DSLContext context, final RocksDao rocksDao, final PersistentCache cache, final String resolverGraph, final Scorer scorer, final LongOpenHashSet blacklist) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		this.cache = cache;
		this.scorer = scorer == null ? NormalizedIndegreeScorer.getInstance() : scorer;
		this.blacklist = blacklist;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(context, resolverGraph);
		resolver.setIgnoreMissing(true);
		this.predicateFactory = new CachingPredicateFactory(context);
		this.revisionCache = new RevisionCache(rocksDao);
	}

	/**
	 * Resets counters and timers that measure visit speed.
	 */
	public void resetCounters() {
		mergeTime.set(0);
		visitTime.set(0);
		resolveTime.set(0);
		visitedArcs.set(0);
	}


	/**
	 * Use the given {@link CGMerger} to get the merged graph for the given revision.
	 *
	 * @param dm the {@link CGMerger} to be used.
	 * @param id the database identifier of a revision.
	 * @return the merged graph for the revision with database identifier {@code id}, or {@code null}
	 *         if {@link CGMerger#mergeWithCHA(long)} returns {@code null} (usually because the provided
	 *         artifact is not present in the graph database).
	 */
	private DirectedGraph getMergedGraph(final CGMerger dm, final long id) {
		final DirectedGraph result = dm.mergeAllDeps();
		if (result != null) {
			LOGGER.info("Graph id: " + id + " merged graph nodes: " + result.numNodes() + " merged graph arcs: " + result.numArcs());
			return result;
		}
		else return null;
	}

	/**
	 * Performs a breadth-first visit of the given graph, starting from the provided seed, using the
	 * provided predicate and returning a collection of {@link Result} instances scored using the
	 * provided scorer and satisfying the provided filter.
	 *
	 * @param graph a {@link DirectedGraph}.
	 * @param forward if true, the visit follows arcs; if false, the visit follows arcs backwards.
	 * @param seed an initial seed; may contain GIDs that do not appear in the graph, which will be
	 *            ignored.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @param scorer a scorer that will be used to score the results.
	 * @param results a list of {@linkplain Result results} that will be filled during the visit.
	 * @param maxResults the maximum number of results deposited in {@code results}; results with a higher score
	 * will replace results with a lower score if the {@code maxResults} threshold is exceeded.
	 * @param globalVisitTime an {@link AtomicLong} where the visit time in nanoseconds will be added.
	 * @param globalVisitedArcs an {@link AtomicLong} where the number of visited arcs will be added.
	 * @param dependent the revision that produced the graph.
	 * @return an ordered set of scored results.
	 */
	protected static ObjectRBTreeSet<Result> bfs(final DirectedGraph graph, final boolean forward, final LongCollection seed, final LongPredicate filter, final Scorer scorer, final int maxResults, final AtomicLong globalVisitTime, final AtomicLong globalVisitedArcs, final Revision dependent) {
		final LongSet nodes = graph.nodes();
		final LongArrayFIFOQueue visitQueue = new LongArrayFIFOQueue(graph.numNodes() + 1); // The +1 can be removed in fastutil > 8.5.9
		final LongOpenHashSet seen = new LongOpenHashSet(graph.numNodes(), 0.5f);
		final ObjectRBTreeSet<Result> results = new ObjectRBTreeSet<>();

		seed.forEach(gid -> {
			if (nodes.contains(gid)) {
				visitQueue.enqueue(gid);
				seen.add(gid);
			}}); // Load initial state, skipping seeds out of graph

		LOGGER.debug("Going to visit a graph with " + graph.numNodes() + " nodes and " + graph.numArcs() + " arcs and seed size " + visitQueue.size() + " within dependent " + dependent.groupId + ":" + dependent.artifactId + "$" + dependent.version);

		if (visitQueue.isEmpty()) return results;
		
		final long start = -System.nanoTime();
		
		int d = -1;
		long sentinel = visitQueue.firstLong(), visitedArcs = 0;

		while (!visitQueue.isEmpty()) {
			final long gid = visitQueue.dequeueLong();
			if (gid == sentinel) {
				d++;
				sentinel = -1;
			}

			if (filter.test(gid)) {
				final double score = scorer.score(graph, gid, d);
				if (results.size() < maxResults || score > results.last().score) {
					results.add(new Result(gid, score, dependent));
					if (results.size() > maxResults) results.remove(results.last());
				}
			}

			final LongIterator iterator = forward ? graph.successorsIterator(gid) : graph.predecessorsIterator(gid);

			while (iterator.hasNext()) {
				final long x = iterator.nextLong();
				visitedArcs++;
				if (seen.add(x)) {
					if (sentinel == -1) sentinel = x;
					visitQueue.enqueue(x);
				}
			}
		}

		LOGGER.debug("Visited " + visitedArcs + " arcs");	
		globalVisitTime.addAndGet(start + System.nanoTime());
		globalVisitedArcs.addAndGet(visitedArcs);
		return results;
	}

	/** 
	 * Performs a breadth-first visit of the given graph, starting from a given gid and trying to reach a certain other gid, using the
	 * provided predicate and returning a {@link PathResult} (possibly empty, if no path was found). The first (hence, one of the shortest) path
	 * found is returned.
	 * 
	 * @param graph the graph to be visied.
	 * @param gidFrom the id of the callable to start from.
	 * @param gidTo the id of the callable to reach.
	 * @param filter the filter that is applied to all nodes (except for <code>gidFrom</code>) before adding them to the visit queue.
	 * @param globalVisitTime an {@link AtomicLong} where the visit time in nanoseconds will be added.
	 * @param globalVisitedArcs an {@link AtomicLong} where the number of visited arcs will be added.
	 * @param dependent the revision that produced the graph.
	 * @return the path found (or an empty path, if no path was found).
	 */
	
	protected PathResult bfsBetween(final DirectedGraph graph, final long gidFrom, final long gidTo, final LongPredicate filter, final AtomicLong globalVisitTime, final AtomicLong globalVisitedArcs, final Revision dependent) {
		LOGGER.debug("Starting point-to-point visit from " + gidFrom + " to " + gidTo);
		final LongSet nodes = graph.nodes();
		final LongArrayFIFOQueue visitQueue = new LongArrayFIFOQueue(graph.numNodes() + 1); // The +1 can be removed in fastutil > 8.5.9
		final LongOpenHashSet seen = new LongOpenHashSet(graph.numNodes(), Hash.FAST_LOAD_FACTOR);
		final Long2LongOpenHashMap parent = new Long2LongOpenHashMap(graph.numNodes(), Hash.FAST_LOAD_FACTOR);
		final PathResult results = new PathResult(dependent);
		
		if (nodes.contains(gidFrom)) {
			visitQueue.enqueue(gidFrom);
			seen.add(gidFrom);
			parent.put(gidFrom, -1);
		}

		if (visitQueue.isEmpty()) {
			LOGGER.debug("Immediately exiting point-to-point visit from " + gidFrom + " to " + gidTo);
			return results;
		} else if (!nodes.contains(gidTo)) {
			LOGGER.debug("Point-to-point visit from " + gidFrom + " to " + gidTo + " aborted because the target is missing");
			return results;
		} else {
			LOGGER.debug("Actual point-to-point visit from " + gidFrom + " to " + gidTo);
		}
		
		final long start = -System.nanoTime();
		
		long visitedArcs = 0;
		boolean found = false;
		
		while (!visitQueue.isEmpty() && !found) {
			final long gid = visitQueue.dequeueLong();
			LOGGER.debug("Dequeued " + gid);
			final LongIterator iterator = graph.successorsIterator(gid);

			while (iterator.hasNext() && !found) {
				final long x = iterator.nextLong();
				LOGGER.debug("Among the successors of " + gid + " found " + x);
				visitedArcs++;
				if (seen.add(x) && filter.test(x)) {
					LOGGER.debug("New! Enqueuing " + x);
					visitQueue.enqueue(x);
					parent.put(x, gid);
					found = x == gidTo; 
				}
			}
		}
		
		if (found) {
			LongArrayList path = new LongArrayList();
			long current = gidTo;
			path.push(current);
			while (current != gidFrom) {
				current = parent.get(current);
				path.push(current);
			}
			LOGGER.debug("Path at the end of the visit " + path);
			while (!path.isEmpty()) results.add(path.popLong());
			LOGGER.debug("Results at the end of the visit " + results);
		} 
	
		globalVisitTime.addAndGet(start + System.nanoTime());
		globalVisitedArcs.addAndGet(visitedArcs);
		return results;
	}

	/**
	 * Computes the callables satisfying the given predicate and reachable from the provided callable, in all the revisions of the universe,
	 * and returns them in a ranked list.
	 *
	 * @param gid the global ID of a callable.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @param maxResults the maximum number of results returned.
	 * @param maxDependents the maximum number of dependents.
	 * @param publisher a publisher where each intermediate result is published.
	 * @return a future to be used to wait for the computation to complete.
	 */
	public Future<Void> fromCallable(final long gid, final LongPredicate filter, final int maxResults, final int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		return from(Util.getRevision(gid, context), LongSets.singleton(gid), filter, maxResults, maxDependents, publisher);
	}

	/**
	 * Computes the callables satisfying the given predicate and reachable from the provided revision,
	 * and returns them in a ranked list.
	 *
	 * @param revisionUri a FASTEN URI specifying a revision.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @param maxResults the maximum number of results returned.
	 * @param maxDependents the maximum number of dependents.
	 * @param publisher a publisher where each intermediate result is published.
	 * @return a future to be used to wait for the computation to complete.
	 */
	public Future<Void> fromRevision(final FastenURI revisionUri, final LongPredicate filter, final int maxResults, final int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		// Fetch revision id
		final long rev = Util.getRevisionId(revisionUri, context);
		if (rev == -1) throw new IllegalArgumentException("Unknown revision " + revisionUri);
		return from(rev, null, filter, maxResults, maxDependents, publisher);
	}


	/**
	 * Computes the callables satisfying the given predicate and coreachable from the provided callable,
	 * and returns them in a ranked list. They will be filtered by the conjuction of
	 * {@link #predicateFilters}.
	 *
	 * @param gid the global ID of a callable.
	 * @param maxResults the maximum number of results returned.
	 * @param maxDependents the maximum number of dependents.
	 * @param publisher a publisher where each intermediate result is published.
	 * @return a future to be used to wait for the computation to complete.
	 */
	public Future<Void> toCallable(final long gid, final LongPredicate filter, final int maxResults, final int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		return to(Util.getRevision(gid, context), LongSets.singleton(gid), filter, maxResults, maxDependents, publisher);
	}

	/**
	 * Computes the callables satisfying the given predicate and coreachable from the provided revision,
	 * and returns them in a ranked list.
	 *
	 * @param revisionUri a FASTEN URI specifying a revision.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @param maxResults the maximum number of results returned.
	 * @param maxDependents the maximum number of dependents.
	 * @param publisher a publisher where each intermediate result is published.
	 * @return a future to be used to wait for the computation to complete.
	 */
	public Future<Void> toRevision(final FastenURI revisionUri, final LongPredicate filter, final int maxResults, final int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		// Fetch revision id
		final long rev = Util.getRevisionId(revisionUri, context);
		if (rev == -1) throw new IllegalArgumentException("Unknown revision " + revisionUri);
		return to(rev, null, filter, maxResults, maxDependents, publisher);
	}

	/**
	 * Computes the callables satisfying the given predicate and coreachable from the provided seed, in
	 * the merged graph associated with the provided revision.
	 *
	 * @param revId the database id of a revision.
	 * @param providedSeed a collection of GIDs that will be used as a seed for the visit; if {@code null}, the
	 *            entire set of GIDs of the specified revision will be used as a seed.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @param maxResults the maximum number of results returned.
	 * @param maxDependents the maximum number of dependents.
	 * @param publisher a publisher where each intermediate result is published.
	 * @return a future controlling the completion of the search.
	 */
	public Future<Void> to(final long revId, final LongCollection providedSeed, final LongPredicate filter, final int maxResults,  final int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		return visitUniverse(revId, providedSeed, maxDependents, new UniverseVisitor() {
			
			@Override
			public void visit(final DirectedGraph mergedGraph, final LongCollection seed, final Revision dependent) {
				publisher.submit(bfs(mergedGraph, false, seed, filter, scorer, maxResults, visitTime, visitedArcs, dependent));
			}
			
			@Override
			public void init() {}
			
			@Override
			public void close() {
				publisher.close();
			}
		});
	}

	/**
	 * Computes the callables satisfying the given predicate and reachable from the provided seed, in
	 * the merged graph associated with the provided revision.
	 *
	 * @param revId the database id of a revision.
	 * @param providedSeed a collection of GIDs that will be used as a seed for the visit; if {@code null}, the
	 *            entire set of GIDs of the specified revision will be used as a seed.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @param maxResults the maximum number of results returned.
	 * @param maxDependents the maximum number of dependents.
	 * @param publisher a publisher where each intermediate result is published.
	 * @return a future controlling the completion of the search.
	 */
	public Future<Void> from(final long revId, LongCollection providedSeed, final LongPredicate filter, final int maxResults,  int maxDependents, final SubmissionPublisher<SortedSet<Result>> publisher) throws RocksDBException {
		return visitUniverse(revId, providedSeed, maxDependents, new UniverseVisitor() {
			
			@Override
			public void visit(final DirectedGraph mergedGraph, final LongCollection seed, final Revision dependent) {
				publisher.submit(bfs(mergedGraph, true, seed, filter, scorer, maxResults, visitTime, visitedArcs, dependent));
			}
			
			@Override
			public void init() {}
			
			@Override
			public void close() {
				publisher.close();
			}
		});
	}
	
	/**
	 * Computes the shortest paths from a given callable to another given callable, such that all nodes (except possibly for the first one) satisfy the given predicate, in
	 * the merged graph associated with the provided revision.
	 *
	 * @param gidFrom the starting callable id.
	 * @param gidTo the ending callable id.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @param maxResults the maximum number of results returned.
	 * @param maxDependents the maximum number of dependents.
	 * @param publisher a publisher where each intermediate result is published.
	 * @return a future controlling the completion of the search.
	 * @throws RocksDBException
	 */
	public Future<Void> between(long gidFrom, long gidTo, final LongPredicate filter, int maxDependents, SubmissionPublisher<PathResult> publisher) throws RocksDBException {
		long rev = Util.getRevision(gidFrom, context);
		final var graph = rocksDao.getGraphData(rev);
		if (graph == null) throw new NoSuchElementException("Revision associated with callable missing from the graph database");
		LOGGER.debug("Revision call graph has " + graph.numNodes() + " nodes");

		return visitUniverse(rev, LongArrayList.of(gidFrom), maxDependents, new UniverseVisitor() {
			
			@Override
			public void visit(final DirectedGraph mergedGraph, final LongCollection seed, final Revision dependent) {
				PathResult path = bfsBetween(mergedGraph, gidFrom, gidTo, filter, visitTime, visitedArcs, dependent); // May return a path of length 0 if it could not reach the target
				if (path.size() > 0) publisher.submit(path);
			}
			
			@Override
			public void init() {}
			
			@Override
			public void close() {
				publisher.close();
			}
		});
		
	}

	/**
	 * Computes the universe of a given revision <code>revId</code>, and visits it using a suitable visitor. 
	 * The universe is obtained by taking the set of (direct and indirect) dependents of the given revision,
	 * obtaining for each of them the corresponding merged graph and possibly discarding it if the graph so
	 * obtained does not contain <code>revId</code>. To each of them a certain visitor is applied. The visitor
	 * receives the merged graph, and a collection of seeds (a set of callables within <code>revId</code>, or
	 * the set of all callables within <code>revId</code> if <code>providedSeed</code> is <code>null</code>). 
	 * 
	 * @param revId the database id of a revision.
	 * @param providedSeed a collection of GIDs that will be used as a seed for the visit; if {@code null}, the
	 *            entire set of GIDs of the specified revision will be used as a seed.
	 * @param maxDependents the maximum number of dependents.
	 * @param visitor the visitor to be used.
	 * @return a future controlling the completion of the search.
	 */
	public Future<Void> visitUniverse(final long revId, final LongCollection providedSeed, final int maxDependents, final UniverseVisitor visitor) throws RocksDBException {
		LOGGER.debug("Called visitUniverse for revision " + revId + " with " + (providedSeed == null || providedSeed.size() <= 10 ? "seed " + providedSeed : "large (size>10) seed"));
		throwables.clear();
		if (blacklist.contains(revId)) throw new NoSuchElementException("Revision " + revId + " is blacklisted");
		final var graph = rocksDao.getGraphData(revId);
		if (graph == null) throw new NoSuchElementException("Revision associated with callable missing from the graph database");
		final var seed = providedSeed == null ? LongOpenHashSet.toSet(graph.nodes().longStream().filter(node -> graph.isInternal(node))) : providedSeed;

		String[] data = Util.getGroupArtifactVersion(revId, context);
		String groupId = data[0];
		String artifactId = data[1];
		String version = data[2];
		LOGGER.debug("visitUniverse(" + groupId + ":" + artifactId + ":" + version.toString() + "), maxDependents=" + maxDependents);


		final int numberOfThreads = Math.min(MAX_NUMBER_OF_THREADS, Runtime.getRuntime().availableProcessors());
		final ArrayBlockingQueue<Revision> s = new ArrayBlockingQueue<>(numberOfThreads * 10);

		final ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads + 1); // +1 for the pipeline
		final ExecutorCompletionService<Void> executorCompletionService = new ExecutorCompletionService<>(executorService);

		final ArrayList<Future<Void>> futures = new ArrayList<>();

		for(int i = 0; i < numberOfThreads; i++) futures.add(executorCompletionService.submit(() -> {
			for(;;) {
				final Revision dependent;
				try {
					dependent = s.take();
					if (LOGGER.isDebugEnabled()) {
						if (dependent == GraphMavenResolver.END) LOGGER.debug("Obtained poison pill");
						else LOGGER.debug("Obtained dependent " + dependent.groupId + ":" + dependent.artifactId + "$" + dependent.version.toString());
					}
				} catch(InterruptedException canceled) {
					return null;
				}
				if (dependent == GraphMavenResolver.END) return null;

				var dependentId = dependent.id;

				if (blacklist.contains(dependentId)) {
					LOGGER.debug("Dependent with GID " + dependent.id + " [" + dependent.groupId + ":" + dependent.artifactId + "$" + dependent.version.toString() + "] is blacklisted");
					continue;
				}

				if (!revisionCache.contains(dependentId)) {
					LOGGER.debug("Dependent with GID " + dependent.id + " [" + dependent.groupId + ":" + dependent.artifactId + "$" + dependent.version.toString() + "] is not in the callable index");
					continue;
				}

				// Check that the graph is a true dependent
				final LongLinkedOpenHashSet depFromCache = cache.getDeps(dependentId);

				final LongLinkedOpenHashSet dependencyIds;
				if (depFromCache == null) {
					final long start = -System.nanoTime();
					final Set<Revision> dependencySet = resolver.resolveDependencies(dependent.groupId, dependent.artifactId, dependent.version.toString(), -1, context, true);
					dependencyIds = LongLinkedOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id));
					dependencyIds.addAndMoveToFirst(dependentId);
					resolveTime.addAndGet(start + System.nanoTime());
					cache.putDeps(dependentId, dependencyIds);
				} else dependencyIds = depFromCache;

				LOGGER.debug("Found " + dependencyIds.size() + " dependencies");

				if (dependentId != revId && !dependencyIds.contains(revId)) {
					LOGGER.debug("False dependent " + dependent.groupId + ":" + dependent.artifactId + ":" + dependent.version.toString());
					continue; // We cannot possibly reach the callable
				}
				LOGGER.debug("True dependent " + dependent.groupId + ":" + dependent.artifactId + ":" + dependent.version.toString());
				
				DirectedGraph mergedGraph = cache.getMerged(dependentId);
				if (mergedGraph == NO_GRAPH) {
					LOGGER.debug("Dependent with GID " + dependent.id + " [" + dependent.groupId + ":" + dependent.artifactId + "$" + dependent.version.toString() + "] is mapped to NO_GRAPH");
					continue;
				}
				if (mergedGraph == null) {
					LOGGER.debug("Analyzing dependent " + dependent.groupId + ":" + dependent.artifactId + ":" + dependent.version.toString());

					for(LongIterator iterator =  dependencyIds.iterator(); iterator.hasNext();) 
						if (!revisionCache.mayContain(iterator.nextLong())) iterator.remove();

					final long start = -System.nanoTime();
					final var dm = new CGMerger(dependencyIds, context, rocksDao);

					try {
						mergedGraph = getMergedGraph(dm, dependentId);
					} catch (final Throwable t) {
						throwables.add(t);
						LOGGER.error("mergeWithCHA threw an exception", t);
					}
					mergeTime.addAndGet(start + System.nanoTime());

					if (mergedGraph == null) {
						cache.putMerged(dependentId, null);
						LOGGER.error("mergeWithCHA returned null on gid " + dependentId);
						continue;
					} else {
						mergedGraph = ArrayImmutableDirectedGraph.copyOf(mergedGraph, false);
						cache.putMerged(dependentId, (ArrayImmutableDirectedGraph)mergedGraph);
					}
				}
				
				LOGGER.debug("Merged graph for dependent " + dependent.groupId + ":" + dependent.artifactId + ":" + dependent.version.toString() 
						+ " has " + mergedGraph.numNodes() + " nodes");
				visitor.visit(mergedGraph, seed, dependent);
			}
		}));

		// Last future is the pipeline future
		futures.add(resolver.resolveDependentsPipeline(groupId, artifactId, version, s, -1, true, maxDependents, numberOfThreads, executorCompletionService));

		executorService.shutdown();

		final ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
		
		final Future<Void> result = singleThreadExecutor.submit(() -> {
			int i = 0;
			try {
				for(; i < numberOfThreads + 1; i++) executorCompletionService.take();
			} catch (final InterruptedException cancelled) {
				for(var future: futures) future.cancel(true);
				for(; i < numberOfThreads + 1; i++) executorCompletionService.take();
				for(var future: futures) future.get();
			} finally {
				visitor.close();
			}
			
			return null;
		});
		
		singleThreadExecutor.shutdown();
		return result;
	}

	
	
	
	@Override
	public void close() throws Exception {
		cache.close();
		rocksDao.close();
	}

	/** Returns the context (i.e., Postgres metadata database) used by this search engine.
	 * 
	 * @return the context.
	 */
	public DSLContext context() {
		return context;
	}

	/** Returns the predicate factory used to create predicates for this search engine.
	 * 
	 * @return the predicate factory.
	 */
	public PredicateFactory predicateFactory() {
		return predicateFactory;
	}

}