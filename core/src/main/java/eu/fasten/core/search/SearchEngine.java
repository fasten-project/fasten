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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.SerializationUtils;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.conf.ParseUnknownFunctions;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Longs;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.search.predicate.CachingPredicateFactory;
import eu.fasten.core.search.predicate.PredicateFactory;
import eu.fasten.core.search.predicate.PredicateFactory.MetadataSource;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.lang.ObjectParser;

/**
 * A class offering searching capabilities over the FASTEN knowledge base.
 *
 * <p>
 * Instances of this class access the metadata Postgres database and the RocksDB database of
 * revision call graphs. Users can interrogate the engine by providing an entry point (e.g., a
 * callable) and a {@link LongPredicate} that will be used to filter the results. For more
 * documentation on the available filters, see {@link PredicateFactory}.
 *
 * <p>
 * This class sports a {@link #main(String[])} method offering a command-line interface over an
 * instance. Please use the command-line help for more information.
 */

public class SearchEngine implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngine.class);
	private static final ArrayImmutableDirectedGraph NO_GRAPH = new ArrayImmutableDirectedGraph.Builder().build();

	private static final int DEFAULT_LIMIT = 10;

	/** The regular expression for commands. */
	private static Pattern COMMAND_REGEXP = Pattern.compile("\\$\\s*(.*)\\s*");

	/**
	 * A class representing results with an associated score.
	 */
	public final static class Result {
		/** The GID of a callable. */
		public long gid;
		/** The score associated to the callable during the search. */
		public double score;

		/**
		 * Creates a {@link Result} instance with fields initialized to zero.
		 */
		public Result() {
		}

		/**
		 * Creates a {@link Result} instance using a provided GID and score.
		 *
		 * @param gid the GID of a callable.
		 * @param score the associated score.
		 */
		public Result(final long gid, final double score) {
			this.gid = gid;
			this.score = score;
		}

		@Override
		public String toString() {
			return gid + " (" + score + ")";
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
	}

	/** The handle to the Postgres metadata database. */
	private final DSLContext context;
	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;
	/** The resolver. */
	private final GraphMavenResolver resolver;
	/** The persistent cache. */
	private final RocksDB cache;
	/** The handle for merged graphs in the persistent cache. */
	private ColumnFamilyHandle mergedHandle;
	/** The handle for dependencies in the persistent cache. */
	private ColumnFamilyHandle dependenciesHandle;
	/** The predicate factory to be used to create predicates for this search engine. */
	private final PredicateFactory predicateFactory;
	/** The scorer that will be used to rank results. */
	private final Scorer scorer;

	/** The maximum number of results that should be printed. */
	private int limit = DEFAULT_LIMIT;
	/** Maximum number of dependents used by {@link #to}. */
	private long maxDependents = Long.MAX_VALUE;
	/**
	 * The filters whose conjunction will be applied by default when executing a query, unless otherwise
	 * specified (compare, e.g., {@link #fromCallable(long)} and
	 * {@link #fromCallable(long, LongPredicate)}).
	 */
	private final ObjectArrayList<LongPredicate> predicateFilters = new ObjectArrayList<>();
	/**
	 * A list parallel to {@link #predicateFilters} that contains the filter specs (readable format of
	 * the filters).
	 */
	private final ObjectArrayList<String> predicateFiltersSpec = new ObjectArrayList<>();

	/** Time spent during resolution (dependency and dependents). */
	private long resolveTime;
	/** Time spent stitching graphs (mergeWithCHA()). */
	private long stitchingTime;
	/** Time spent during {@linkplain #bfs visits}. */
	private long visitTime;
	/** Throwables thrown by mergeWithCHA(). */
	private final List<Throwable> throwables = new ArrayList<>();

	private final Long2ObjectLinkedOpenHashMap<ArrayImmutableDirectedGraph> mergedCache = new Long2ObjectLinkedOpenHashMap<>();
	private final Long2ObjectLinkedOpenHashMap<LongLinkedOpenHashSet> depsCache = new Long2ObjectLinkedOpenHashMap<>();
	
	public static final class RocksDBData {
		public final RocksDB cache;
		public final List<ColumnFamilyHandle> columnFamilyHandles;
		public RocksDBData(RocksDB cache, List<ColumnFamilyHandle> columnFamilyHandles) {
			this.cache = cache;
			this.columnFamilyHandles = columnFamilyHandles;
		}
	}
	
	/** Utility method to open the persistent cache.
	 * 
	 * @param cacheDir the path to the persistent cache.
	 * @param readOnly open RocksDB read-only.
	 * @return a RocksDB handle.
	 */
	public static final RocksDBData openCache(final String cacheDir, final boolean readOnly) throws RocksDBException {
		RocksDB.loadLibrary();
		final ColumnFamilyOptions defaultOptions = new ColumnFamilyOptions();
		@SuppressWarnings("resource")
		final DBOptions dbOptions = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
		final List<ColumnFamilyDescriptor> cfDescriptors = List.of(new ColumnFamilyDescriptor(
				RocksDB.DEFAULT_COLUMN_FAMILY, defaultOptions), 
				new ColumnFamilyDescriptor("merged".getBytes(), defaultOptions), 
				new ColumnFamilyDescriptor("dependencies".getBytes(), defaultOptions),
				new ColumnFamilyDescriptor("components".getBytes(), defaultOptions));
		final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
		return new RocksDBData(readOnly ?
				RocksDB.openReadOnly(dbOptions, cacheDir, cfDescriptors, columnFamilyHandles) :
				RocksDB.open(dbOptions, cacheDir, cfDescriptors, columnFamilyHandles), columnFamilyHandles); 
	}

	private void cachePutMerged(final long key, ArrayImmutableDirectedGraph graph) throws RocksDBException {
		if (graph == null) {
			mergedCache.putAndMoveToFirst(key, NO_GRAPH);
			cache.put(mergedHandle, Longs.toByteArray(key), new byte[0]);
		} else {
			mergedCache.putAndMoveToFirst(key, graph);
			cache.put(mergedHandle, Longs.toByteArray(key), SerializationUtils.serialize(graph));
		}
	}
	
	private void cachePutDeps(final long key, LongLinkedOpenHashSet deps) throws RocksDBException {
		depsCache.putAndMoveToFirst(key, deps);
		cache.put(dependenciesHandle, Longs.toByteArray(key), SerializationUtils.serialize(deps));				
	}

	private ArrayImmutableDirectedGraph cacheGetMerged(final long key) throws RocksDBException {
		ArrayImmutableDirectedGraph merged = mergedCache.get(key);
		if (merged != null) return merged;
		final byte[] array = cache.get(mergedHandle, Longs.toByteArray(key));
		if (array == null) return null;
		if (array.length == 0) merged = NO_GRAPH;
		else merged = SerializationUtils.deserialize(array);
		mergedCache.putAndMoveToFirst(key, merged);
		return merged;
	}
	
	private LongLinkedOpenHashSet cacheGetDeps(final long key) throws RocksDBException {
		LongLinkedOpenHashSet deps = depsCache.get(key);
		if (deps != null) return deps;
		final byte[] array = cache.get(dependenciesHandle, Longs.toByteArray(key));
		if (array == null) return null;
		deps = SerializationUtils.deserialize(array);
		depsCache.putAndMoveToFirst(key, deps);
		return deps;
	}

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
	 *            {@link TrivialScorer} will be used instead.
	 */
	public SearchEngine(final String jdbcURI, final String database, final String rocksDb, final String cacheDir, final String resolverGraph, final String scorer) throws Exception {
		this(PostgresConnector.getDSLContext(jdbcURI, database, false), new RocksDao(rocksDb, true), openCache(cacheDir, false), resolverGraph, scorer == null ? TrivialScorer.getInstance() : ObjectParser.fromSpec(scorer, Scorer.class));
	}

	/**
	 * Creates a new search engine using a given {@link DSLContext} and {@link RocksDao}.
	 *
	 * @param context the DSL context.
	 * @param rocksDao the RocksDB DAO.
	 * @param resolverGraph the path to a serialized resolver graph (will be created if it does not
	 *            exist).
	 * @param scorer a scorer that will be used to sort results; if {@code null}, a
	 *            {@link TrivialScorer} will be used instead.
	 */

	public SearchEngine(final DSLContext context, final RocksDao rocksDao, final RocksDBData rocksDBData, final String resolverGraph, final Scorer scorer) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		this.cache = rocksDBData.cache;
		this.scorer = scorer == null ? TrivialScorer.getInstance() : scorer;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(context, resolverGraph);
		resolver.setIgnoreMissing(true);
		this.predicateFactory = new CachingPredicateFactory(context);
		mergedHandle = rocksDBData.columnFamilyHandles.get(0);
		dependenciesHandle = rocksDBData.columnFamilyHandles.get(1);
	}

	/**
	 * Executes a given command.
	 *
	 * @param command the command.
	 */
	private void executeCommand(final String command) {
		final String[] commandAndArgs = command.split("\\s"); // Split command on whitespace
		final String help = "\t$help                           Help on commands\n" + "\t$clear                          Clear filters\n" + "\t$f ?                            Print the current filter\n" + "\t$f pmatches <REGEXP>            Add filter: package (a.k.a. product) matches <REGEXP>\n" + "\t$f vmatches <REGEXP>            Add filter: version matches <REGEXP>\n" + "\t$f xmatches <REGEXP>            Add filter: path (namespace + entity) matches <REGEXP>\n" + "\t$f cmd <KEY> [<REGEXP>]         Add filter: callable metadata contains key <KEY> (satisfying <REGEXP>)\n" + "\t$f mmd <KEY> [<REGEXP>]         Add filter: module metadata contains key <KEY> (satisfying <REGEXP>)\n" + "\t$f pmd <KEY> [<REGEXP>]         Add filter: package+version metadata contains key <KEY> (satisfying <REGEXP>)\n" + "\t$f cmdjp <JP> <REGEXP>          Add filter: callable metadata queried with the JSONPointer <JP> has a value satisfying <REGEXP>\n" + "\t$f mmdjp <JP> <REGEXP>          Add filter: module metadata queried with the JSONPointer <JP> has a value satisfying <REGEXP>\n" + "\t$f pmdjp <JP> <REGEXP>          Add filter: package+version metadata queried with the JSONPointer <JP> has a value satisfying <REGEXP>\n" + "\t$or                             The last two filters are substituted by their disjunction (or)\n" + "\t$and                            The last two filters are substituted by their conjunction (and)\n" + "\t$not                            The last filter is substituted by its negation (not)\n" + "\t$limit <LIMIT>                  Print at most <LIMIT> results (-1 for infinity)\n" + "\t$maxDependents <LIMIT>          Maximum number of dependents considered in coreachable query resolution (-1 for infinity)" + "\t±<URI>                          Find reachable (+) or coreachable (-) callables from the given callable <URI> satisfying all filters\n" + "";
		try {
			switch (commandAndArgs[0].toLowerCase()) {

			case "help":
				System.err.println(help);
				break;

			case "limit":
				limit = Integer.parseInt(commandAndArgs[1]);
				if (limit < 0) limit = Integer.MAX_VALUE;
				break;
			case "maxdependents":
				maxDependents = Long.parseLong(commandAndArgs[1]);
				if (maxDependents < 0) maxDependents = Long.MAX_VALUE;
				break;

			case "clear":
				predicateFilters.clear();
				predicateFiltersSpec.clear();
				break;

			case "f":
				LongPredicate predicate = null;
				Pattern regExp;
				MetadataSource mds;
				switch (commandAndArgs[1].toLowerCase()) {
				case "pmatches":
					regExp = Pattern.compile(commandAndArgs[2]);
					predicate = predicateFactory.fastenURIMatches(uri -> matchRegexp(uri.getProduct(), regExp));
					break;
				case "vmatches":
					regExp = Pattern.compile(commandAndArgs[2]);
					predicate = predicateFactory.fastenURIMatches(uri -> matchRegexp(uri.getVersion(), regExp));
					break;
				case "xmatches":
					regExp = Pattern.compile(commandAndArgs[2]);
					predicate = predicateFactory.fastenURIMatches(uri -> matchRegexp(uri.getPath(), regExp));
					break;
				case "cmd":
				case "mmd":
				case "pmd":
					final String key = commandAndArgs[2];
					mds = null;
					switch (commandAndArgs[1].toLowerCase().charAt(0)) {
					case 'c':
						mds = MetadataSource.CALLABLE;
						break;
					case 'm':
						mds = MetadataSource.MODULE;
						break;
					case 'p':
						mds = MetadataSource.PACKAGE_VERSION;
						break;
					default:
						throw new RuntimeException("Cannot happen");
					}
					if (commandAndArgs.length == 3) predicate = predicateFactory.metadataContains(mds, key);
					else {
						regExp = Pattern.compile(commandAndArgs[3]);
						predicate = predicateFactory.metadataContains(mds, key, s -> matchRegexp(s, regExp));
					}
					break;
				case "cmdjp":
				case "mmdjp":
				case "pmdjp":
					final String jsonPointer = commandAndArgs[2];
					mds = null;
					switch (commandAndArgs[1].toLowerCase().charAt(0)) {
					case 'c':
						mds = MetadataSource.CALLABLE;
						break;
					case 'm':
						mds = MetadataSource.MODULE;
						break;
					case 'p':
						mds = MetadataSource.PACKAGE_VERSION;
						break;
					default:
						throw new RuntimeException("Cannot happen");
					}
					regExp = Pattern.compile(commandAndArgs[3]);
					predicate = predicateFactory.metadataQueryJSONPointer(mds, jsonPointer, s -> matchRegexp(s, regExp));
					break;
				case "?":
					System.err.println(String.join(" && ", predicateFiltersSpec));
					break;
				default:
					throw new RuntimeException("Unknown type of predicate " + commandAndArgs[1]);
				}
				if (predicate != null) {
					predicateFilters.push(predicate);
					predicateFiltersSpec.push(String.join(" ", Arrays.copyOfRange(commandAndArgs, 1, commandAndArgs.length)));
				}
				break;

			case "and":
			case "or":
				if (predicateFilters.size() < 2) throw new RuntimeException("At least two predicates must be present");
				if ("and".equals(commandAndArgs[0].toLowerCase())) {
					predicateFilters.push(predicateFilters.pop().and(predicateFilters.pop()));
					predicateFiltersSpec.push("(" + predicateFiltersSpec.pop() + " && " + predicateFiltersSpec.pop() + ")");
				} else {
					predicateFilters.push(predicateFilters.pop().or(predicateFilters.pop()));
					predicateFiltersSpec.push("(" + predicateFiltersSpec.pop() + " || " + predicateFiltersSpec.pop() + ")");
				}
				break;

			case "not":
				if (predicateFilters.size() < 1) throw new RuntimeException("At least one predicates must be present");
				predicateFilters.push(predicateFilters.pop().negate());
				predicateFiltersSpec.push("!(" + predicateFiltersSpec.pop() + ")");
				break;

			default:
				System.err.println("Unknown command " + command);
			}

		} catch (final RuntimeException e) {
			System.err.println("Exception while executing command " + command);
			e.printStackTrace(System.err);
			System.err.println(help);
		}
	}

	/**
	 * Returns true if the given string fully matches the given regular expression (i.e., it matches it
	 * from start to end).
	 *
	 * @param s the string.
	 * @param regExp the regular expression.
	 * @return true iff s is not null and it matches the regular expression from start to end.
	 */
	private static boolean matchRegexp(final String s, final Pattern regExp) {
		if (s == null) return false;
		final Matcher matcher = regExp.matcher(s);
		return matcher.matches() && matcher.start() == 0 && matcher.end() == s.length();
	}

	/**
	 * Use the given {@link CGMerger} to get the stitched graph for the given revision.
	 *
	 * @param dm the {@link CGMerger} to be used.
	 * @param id the database identifier of a revision.
	 * @return the stitched graph for the revision with database identifier {@code id}, or {@code null}
	 *         if {@link CGMerger#mergeWithCHA(long)} returns {@code null} (usually because the provided
	 *         artifact is not present in the graph database).
	 */
	private DirectedGraph getStitchedGraph(final CGMerger dm, final long id) {
		final DirectedGraph result = dm.mergeAllDeps();
		if (result != null) {
			LOGGER.info("Graph id: " + id + " stitched graph nodes: " + result.numNodes() + " stitched graph arcs: " + result.numArcs());
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
	 * @param results a list of {@linkplain Result results} that will be filled during the visit;
	 *            pre-existing results will not be modified.
	 */
	protected static void bfs(final DirectedGraph graph, final boolean forward, final LongCollection seed, final LongPredicate filter, final Scorer scorer, final Collection<Result> results) {
		final LongArrayFIFOQueue queue = new LongArrayFIFOQueue(seed.size());
		seed.forEach(x -> queue.enqueue(x)); // Load initial state
		final LongOpenHashSet seen = new LongOpenHashSet();
		seed.forEach(x -> seen.add(x)); // Load initial state
		int d = -1;
		long sentinel = queue.firstLong();
		final Result probe = new Result();
		final LongSet nodes = graph.nodes();

		while (!queue.isEmpty()) {
			final long gid = queue.dequeueLong();
			if (gid == sentinel) {
				d++;
				sentinel = -1;
			}

			if (!nodes.contains(gid)) continue; // We accept arbitrary seed sets

			if (!seed.contains(gid) && filter.test(gid)) {
				// TODO: maybe we want to update in case of improved score?
				probe.gid = gid;
				if (!results.contains(probe)) results.add(new Result(gid, scorer.score(graph, gid, d)));
			}

			final LongIterator iterator = forward ? graph.successors(gid).iterator() : graph.predecessors(gid).iterator();

			while (iterator.hasNext()) {
				final long x = iterator.nextLong();
				if (seen.add(x)) {
					if (sentinel == -1) sentinel = x;
					queue.enqueue(x);
				}
			}
		}
	}

	/**
	 * Computes the callables satisfying the conjunction of {@link #predicateFilters} and reachable from
	 * the provided callable, and returns them in a ranked list.
	 *
	 * @param gid the global ID of a callable.
	 * @return a list of {@linkplain Result results}.
	 */
	private List<Result> fromCallable(final long gid) throws RocksDBException {
		return fromCallable(gid, predicateFilters.stream().reduce(x -> true, LongPredicate::and));
	}

	/**
	 * Computes the callables satisfying the given predicate and reachable from the provided callable,
	 * and returns them in a ranked list.
	 *
	 * @param gid the global ID of a callable.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @return a list of {@linkplain Result results}.
	 */
	public List<Result> fromCallable(final long gid, final LongPredicate filter) throws RocksDBException {
		return from(Util.getRevision(gid, context), LongSets.singleton(gid), filter);
	}

	/**
	 * Computes the callables satisfying satisfying the conjunction of {@link #predicateFilters} and
	 * reachable from the provided revision, and returns them in a ranked list.
	 *
	 * @param revisionUri a FASTEN URI specifying a revision.
	 * @return a list of {@linkplain Result results}.
	 */
	private List<Result> fromRevision(final FastenURI revisionUri) throws RocksDBException {
		return fromRevision(revisionUri, predicateFilters.stream().reduce(x -> true, LongPredicate::and));
	}

	/**
	 * Computes the callables satisfying the given predicate and reachable from the provided revision,
	 * and returns them in a ranked list.
	 *
	 * @param revisionUri a FASTEN URI specifying a revision.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @return a list of {@linkplain Result results}.
	 */
	public List<Result> fromRevision(final FastenURI revisionUri, final LongPredicate filter) throws RocksDBException {
		// Fetch revision id
		final long rev = Util.getRevisionId(revisionUri, context);
		if (rev == -1) throw new IllegalArgumentException("Unknown revision " + revisionUri);
		return from(rev, null, filter);
	}

	/**
	 * Computes the callables satisfying the given predicate and reachable from the provided seed, in
	 * the stitched graph associated with the provided revision, and returns them in a ranked list.
	 *
	 * @param rev the database id of a revision.
	 * @param seed a collection of GIDs that will be used as a seed for the visit; if {@code null}, the
	 *            entire set of GIDs of the specified revision will be used as a seed.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @return a list of {@linkplain Result results}.
	 */
	public List<Result> from(final long rev, LongCollection seed, final LongPredicate filter) throws RocksDBException {
		final var graph = rocksDao.getGraphData(rev);
		if (graph == null) throw new NoSuchElementException("Revision associated with callable missing from the graph database");
		if (seed == null) seed = graph.nodes();

		LOGGER.debug("Revision call graph has " + graph.numNodes() + " nodes");

		DirectedGraph stitchedGraph = cacheGetMerged(rev);
		if (stitchedGraph == NO_GRAPH) throw new NullPointerException("mergeWithCHA() returned null on gid " + rev);
		if (stitchedGraph == null) {
			final Record2<String, String> record = context.select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(PackageVersions.PACKAGE_VERSIONS.ID.eq(Long.valueOf(rev))).fetchOne();
			final String[] a = record.component1().split(":");
			final String groupId = a[0];
			final String artifactId = a[1];
			final String version = record.component2();

			final LongLinkedOpenHashSet depFromCache = cacheGetDeps(rev);

			final LongLinkedOpenHashSet dependencyIds; 
			if (depFromCache == null) {
				resolveTime -= System.nanoTime();
				final Set<Revision> dependencySet = resolver.resolveDependencies(groupId, artifactId, version, -1, context, true);
				dependencyIds = LongLinkedOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id));
				dependencyIds.addAndMoveToFirst(rev);
				resolveTime += System.nanoTime();
				cachePutDeps(rev, dependencyIds);
			}
			else dependencyIds = depFromCache;

			LOGGER.debug("Found " + dependencyIds.size() + " dependencies");

			stitchingTime -= System.nanoTime();
			final var dm = new CGMerger(dependencyIds, context, rocksDao);
			stitchedGraph = getStitchedGraph(dm, rev);
			stitchingTime += System.nanoTime();

			if (stitchedGraph == null) {
				cachePutMerged(rev, null);
				throw new NullPointerException("mergeWithCHA() returned null on gid " + rev);
			}
			else {
				stitchedGraph = ArrayImmutableDirectedGraph.copyOf(stitchedGraph, false);
				cachePutMerged(rev, (ArrayImmutableDirectedGraph)stitchedGraph);
			}
		}

		LOGGER.debug("Stiched graph has " + stitchedGraph.numNodes() + " nodes");

		final ObjectLinkedOpenHashSet<Result> results = new ObjectLinkedOpenHashSet<>();

		visitTime -= System.nanoTime();
		bfs(stitchedGraph, true, seed, filter, scorer, results);
		visitTime += System.nanoTime();

		LOGGER.debug("Found " + results.size() + " reachable nodes");

		final Result[] array = results.toArray(new Result[0]);
		Arrays.sort(array, (x, y) -> Double.compare(y.score, x.score));
		return Arrays.asList(array);
	}

	/**
	 * Computes the callables satisfying the conjunction of {@link #predicateFilters} and coreachable
	 * from the provided callable, and returns them in a ranked list.
	 *
	 * @param gid the global ID of a callable.
	 * @return a list of {@linkplain Result results}.
	 */
	private List<Result> toCallable(final long gid) throws RocksDBException {
		return toCallable(gid, predicateFilters.stream().reduce(x -> true, LongPredicate::and));
	}

	/**
	 * Computes the callables satisfying the given predicate and coreachable from the provided callable,
	 * and returns them in a ranked list. They will be filtered by the conjuction of
	 * {@link #predicateFilters}.
	 *
	 * @param gid the global ID of a callable.
	 * @return a list of {@linkplain Result results}.
	 */
	public List<Result> toCallable(final long gid, final LongPredicate filter) throws RocksDBException {
		return to(Util.getRevision(gid, context), LongSets.singleton(gid), filter);
	}

	/**
	 * Computes the callables satisfying {{@link #predicateFilters} and coreachable from the provided
	 * revision, and returns them in a ranked list.
	 *
	 * @param revisionUri a FASTEN URI specifying a revision.
	 * @return a list of {@linkplain Result results}.
	 */
	private List<Result> toRevision(final FastenURI revisionUri) throws RocksDBException {
		return toRevision(revisionUri, predicateFilters.stream().reduce(x -> true, LongPredicate::and));
	}

	/**
	 * Computes the callables satisfying the given predicate and coreachable from the provided revision,
	 * and returns them in a ranked list.
	 *
	 * @param revisionUri a FASTEN URI specifying a revision.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @return a list of {@linkplain Result results}.
	 */
	public List<Result> toRevision(final FastenURI revisionUri, final LongPredicate filter) throws RocksDBException {
		// Fetch revision id
		final long rev = Util.getRevisionId(revisionUri, context);
		if (rev == -1) throw new IllegalArgumentException("Unknown revision " + revisionUri);
		return to(rev, null, filter);
	}

	/**
	 * Computes the callables satisfying the given predicate and coreachable from the provided seed, in
	 * the stitched graph associated with the provided revision, and returns them in a ranked list.
	 *
	 * @param revId the database id of a revision.
	 * @param seed a collection of GIDs that will be used as a seed for the visit; if {@code null}, the
	 *            entire set of GIDs of the specified revision will be used as a seed.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @return a list of {@linkplain Result results}.
	 */
	public List<Result> to(final long revId, LongCollection seed, final LongPredicate filter) throws RocksDBException {
		throwables.clear();
		final var graph = rocksDao.getGraphData(revId);
		if (graph == null) throw new NoSuchElementException("Revision associated with callable missing from the graph database");
		if (seed == null) seed = graph.nodes();

		String[] data = Util.getGroupArtifactVersion(revId, context);
		String groupId = data[0];
		String artifactId = data[1];
		String version = data[2];

		final BlockingQueue<Revision> s = resolver.resolveDependentsPipeline(groupId, artifactId, version, -1, true, maxDependents);

		final ObjectLinkedOpenHashSet<Result> results = new ObjectLinkedOpenHashSet<>();

		long trueDependents = 0;

		for (;;) {
			Revision dependent = null;
			try {
				dependent = s.take();
			} catch(InterruptedException cantHappen) {}
			if (dependent == GraphMavenResolver.END) break;
			var dependentId = dependent.id;

			byte[] dependentIdAsByteArray = Longs.toByteArray(dependentId);
			DirectedGraph stitchedGraph = cacheGetMerged(dependentId);
			if (stitchedGraph == NO_GRAPH) continue;
			if (stitchedGraph == null) {
				groupId = dependent.groupId;
				artifactId = dependent.artifactId;
				version = dependent.version.toString();

				LOGGER.debug("Analyzing dependent " + groupId + ":" + artifactId + ":" + version);

				final LongLinkedOpenHashSet depFromCache = cacheGetDeps(dependentId);

				final LongLinkedOpenHashSet dependencyIds;
				if (depFromCache == null) {
					resolveTime -= System.nanoTime();
					final Set<Revision> dependencySet = resolver.resolveDependencies(groupId, artifactId, version, -1, context, true);
					dependencyIds = LongLinkedOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id));
					dependencyIds.addAndMoveToFirst(dependentId);
					resolveTime += System.nanoTime();
					cachePutDeps(dependentId, dependencyIds);
				} else dependencyIds = depFromCache;

				LOGGER.debug("Dependent has " + graph.numNodes() + " nodes");
				LOGGER.debug("Found " + dependencyIds.size() + " dependencies");

				if (dependentId != revId && !dependencyIds.contains(revId)) {
					LOGGER.debug("False dependent");
					continue; // We cannot possibly reach the callable
				}

				trueDependents++;

				stitchingTime -= System.nanoTime();
				final var dm = new CGMerger(dependencyIds, context, rocksDao);

				try {
					stitchedGraph = getStitchedGraph(dm, dependentId);
				} catch (final Throwable t) {
					throwables.add(t);
					LOGGER.error("mergeWithCHA threw an exception", t);
				}
				stitchingTime += System.nanoTime();

				if (stitchedGraph == null) {
					cachePutMerged(dependentId, null);
					LOGGER.error("mergeWithCHA returned null on gid " + dependentId);
					continue;
				} else {
					stitchedGraph = ArrayImmutableDirectedGraph.copyOf(stitchedGraph, false);
					cachePutMerged(dependentId, (ArrayImmutableDirectedGraph)stitchedGraph);
				}
			}

			LOGGER.debug("Stiched graph has " + stitchedGraph.numNodes() + " nodes");
			final int sizeBefore = results.size();

			visitTime -= System.nanoTime();
			bfs(stitchedGraph, false, seed, filter, scorer, results);
			visitTime += System.nanoTime();

			LOGGER.debug("Found " + (results.size() - sizeBefore) + " coreachable nodes");
		}

		LOGGER.debug("Found " + trueDependents + " true dependents");
		LOGGER.debug("Found overall " + results.size() + " coreachable nodes");

		final Result[] array = results.toArray(new Result[0]);
		Arrays.sort(array, (x, y) -> Double.compare(y.score, x.score));
		return Arrays.asList(array);
	}

	@Override
	public void close() throws Exception {
		cache.close();
		rocksDao.close();
	}

	@SuppressWarnings("boxing")
	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(SearchEngine.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("cache", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The RocksDB cache."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		final String rocksDb = jsapResult.getString("rocksDb");
		final String cacheDir = jsapResult.getString("cache");
		final String resolverGraph = jsapResult.getString("resolverGraph");

		try (SearchEngine searchEngine = new SearchEngine(jdbcURI, database, rocksDb, cacheDir, resolverGraph, null)) {

			final DSLContext context = searchEngine.context;
			context.settings().withParseUnknownFunctions(ParseUnknownFunctions.IGNORE);

			@SuppressWarnings("resource")
			final Scanner scanner = new Scanner(System.in);
			for (;;) {
				System.out.print("[$help for help]>");
				System.out.flush();
				if (!scanner.hasNextLine()) break;
				String line = scanner.nextLine();
				if (line.length() == 0) continue;
				final Matcher matcher = COMMAND_REGEXP.matcher(line);
				if (matcher.matches()) {
					searchEngine.executeCommand(matcher.group(1));
					continue;
				}
				try {
					final char dir = line.charAt(0);
					if (dir != '+' && dir != '-') {
						if (dir != '#') System.err.println("First character must be '+', '-', or '#'");
						continue;
					}
					line = line.substring(1);
					final FastenJavaURI uri = FastenJavaURI.create(line);

					final long start = -System.nanoTime();
					searchEngine.stitchingTime = searchEngine.resolveTime = searchEngine.visitTime = 0;

					final List<Result> r;

					if (uri.getPath() == null) {
						r = dir == '+' ? searchEngine.fromRevision(uri) : searchEngine.toRevision(uri);
						for (int i = 0; i < Math.min(searchEngine.limit, r.size()); i++) System.out.println(r.get(i).gid + "\t" + Util.getCallableName(r.get(i).gid, context) + "\t" + r.get(i).score);
					} else {
						final long gid = Util.getCallableGID(uri, context);
						if (gid == -1) {
							System.err.println("Unknown URI " + uri);
							continue;
						}
						r = dir == '+' ? searchEngine.fromCallable(gid) : searchEngine.toCallable(gid);
						for (int i = 0; i < Math.min(searchEngine.limit, r.size()); i++) System.out.println(r.get(i).gid + "\t" + Util.getCallableName(r.get(i).gid, context) + "\t" + r.get(i).score);
					}

					for (final var t : searchEngine.throwables) {
						System.err.println(t);
						System.err.println("\t" + t.getStackTrace()[0]);
					}
					System.err.printf("\n%d results \nTotal time: %.3fs Resolve time: %.3fs Stitching time: %.3fs Visit time %.3fs\n", r.size(), (System.nanoTime() + start) * 1E-9, searchEngine.resolveTime * 1E-9, searchEngine.stitchingTime * 1E-9, searchEngine.visitTime * 1E-9);
				} catch (final Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}