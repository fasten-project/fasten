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
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.ClosestFirstIterator;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.conf.ParseUnknownFunctions;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.DatabaseMerger;
import eu.fasten.core.search.predicate.PredicateFactory;
import eu.fasten.core.search.predicate.PredicateFactory.MetadataSource;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * A class offering searching capabilities over the FASTEN knowledge base.
 *
 * <p>
 * Instances of this class access the metadata Postgres database and the RocksDB database of
 * revision call graphs. Users can interrogate the engine by providing an entry point (e.g., a
 * callable) and a {@link LongPredicate} that will be used to filter the results. For more
 * documentation on the available filters, see {@link PredicateFactory}.
 */

public class SearchEngine {
	private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngine.class);

	private static final int DEFAULT_LIMIT = 10;

	/** The regular expression for commands. */
	private static Pattern COMMAND_REGEXP = Pattern.compile("\\$\\s*(.*)\\s*"); 
	
	
	public final static class Result {
		public long gid;
		public double score;

		public Result(final long gid, final double score) {
			this.gid = gid;
			this.score = score;
		}

		@Override
		public String toString() {
			return gid + " (" + score + ")";
		}
	}

	/** The handle to the Postgres metadata database. */
	private final DSLContext context;
	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;
	/** The resolver. */
	private final GraphMavenResolver resolver;
	/** The predicate factory to be used to create predicates for this search engine. */
	private PredicateFactory predicateFactory;

	/** The maximum number of results that should be printed. */
	private int limit = DEFAULT_LIMIT;
	/** The filters whose conjunction will be applied by default when executing a query, unless otherwise
	 *  specified (compare, e.g., {@link #fromCallable(long)} and {@link #fromCallable(long, LongPredicate)}). */
	private ObjectArrayList<LongPredicate> predicateFilters = new ObjectArrayList<>();

	/**
	 * Creates a new search engine using a given JDBC URI, database name and path to RocksDB.
	 *
	 * @implNote This method creates a context and DAO using the given parameters and delegates to
	 *           {@link #SearchEngine(DSLContext, RocksDao)}.
	 *
	 * @param jdbcURI the JDBC URI.
	 * @param database the database name.
	 * @param rocksDb the path to the RocksDB database of revision call graphs.
	 * @param resolverGraph the path to a serialized resolver graph (will be created if it does not
	 *            exist).
	 * @throws Exception
	 */
	public SearchEngine(final String jdbcURI, final String database, final String rocksDb, final String resolverGraph) throws Exception {
		this(PostgresConnector.getDSLContext(jdbcURI, database), new RocksDao(rocksDb, true), resolverGraph);
	}

	/**
	 * Creates a new search engine using a given {@link DSLContext} and {@link RocksDao}.
	 *
	 * @param context the DSL context.
	 * @param rocksDao the RocksDB DAO.
	 * @param resolver a resolver.
	 * @param resolverGraph the path to a serialized resolver graph (will be created if it does not
	 *            exist).
	 * @throws Exception
	 */

	public SearchEngine(final DSLContext context, final RocksDao rocksDao, final String resolverGraph) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(null, resolverGraph);
		this.predicateFactory = new PredicateFactory(context);
	}

	public long gid2Rev(final long gid) {
		return context.select(PackageVersions.PACKAGE_VERSIONS.ID).from(PackageVersions.PACKAGE_VERSIONS).
				join(Modules.MODULES).on(Modules.MODULES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID)).
				join(Callables.CALLABLES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID)).where(Callables.CALLABLES.ID.eq(Long.valueOf(gid))).fetchOne().component1().longValue();
	}

	/** Executes a given command.
	 * 
	 * @param command the command.
	 */
	private void executeCommand(final String command) {
		String[] commandAndArgs = command.split("\\s"); // Split command on whitespace
		String help = 
				"\t$help                           Help on commands\n" +
				"\t$limit <LIMIT>                  Print at most <LIMIT> results (-1 for infinity)\n" +
				"\t$clear                          Clear filters\n" +
				"\t$and pmatches <REGEXP>          Add filter: package (a.k.a. product) matches <REGEXP>\n" +
				"\t$and vmatches <REGEXP>          Add filter: version matches <REGEXP>\n" +
				"\t$and xmatches <REGEXP>          Add filter: path (namespace + entity) matches <REGEXP>\n" +
				"\t$and cmd <KEY> [<VALREGEXP>]    Add filter: callable metadata contains key <KEY> (satisfying <REGEXP>)\n" +
				"\t$and mmd <KEY> [<VALREGEXP>]    Add filter: module metadata contains key <KEY> (satisfying <REGEXP>)\n" +
				"\t$and pmd <KEY> [<VALREGEXP>]    Add filter: package+version metadata contains key <KEY> (satisfying <REGEXP>)\n" +
				"";
		try {
			switch(commandAndArgs[0].toLowerCase()) {
			
			case "help":
				System.err.println(help);
				break;
				
			case "limit":
				limit = Integer.parseInt(commandAndArgs[1]);
				if (limit < 0) limit = Integer.MAX_VALUE;
				break;
				
			case "clear":
				predicateFilters.clear();
				break;
			
			case "and":
				LongPredicate predicate = null;
				Pattern regExp;
				switch(commandAndArgs[1].toLowerCase()) {
				case "pmatches":
					regExp = Pattern.compile(commandAndArgs[2]);
					predicate = predicateFactory.fastenURIMatches(uri -> uri.getProduct() != null && regExp.matcher(uri.getProduct()).matches());
					break;
				case "vmatches":
					regExp = Pattern.compile(commandAndArgs[2]);
					predicate = predicateFactory.fastenURIMatches(uri -> uri.getVersion() != null && regExp.matcher(uri.getVersion()).matches());
					break;
				case "xmatches":
					regExp = Pattern.compile(commandAndArgs[2]);
					predicate = predicateFactory.fastenURIMatches(uri -> uri.getPath() != null && regExp.matcher(uri.getPath()).matches());
					break;
				case "cmd": case "mmd": case "pmd":
					String key = commandAndArgs[2];
					MetadataSource mds = null;
					switch (commandAndArgs[1].toLowerCase().charAt(0)) {
					case 'c':  mds = MetadataSource.CALLABLE; break;
					case 'm':  mds = MetadataSource.MODULE; break;
					case 'p':  mds = MetadataSource.PACKAGE_VERSION; break;
					default: throw new RuntimeException("Cannot happen"); 
					}
				 	if (commandAndArgs.length == 3) predicate = predicateFactory.metadataContains(MetadataSource.CALLABLE, key);
				 	else {
				 		regExp = Pattern.compile(commandAndArgs[3]);
				 		predicate = predicateFactory.metadataContains(MetadataSource.CALLABLE, key, regExp.asPredicate());
				 	}
				 	break;
				default:
					throw new RuntimeException("Unknown type of predicate " + commandAndArgs[1]);
				}
				if (predicate != null) predicateFilters.add(predicate);
				break;
			}
		} catch (RuntimeException e) {
			System.err.println("Exception while executing command " + command);
			e.printStackTrace(System.err);
			System.err.println(help);
		}
	}

	/**
	 * Computes the callables satisfying the given predicate and reachable from the provided callable,
	 * and returns them in a ranked list. They will be filtered by the conjuction of {@link #predicateFilters}.
	 *
	 * @param gid the global ID of a callable.
	 * @return a list of {@linkplain Result results}.
	 */
	public List<Result> fromCallable(final long gid) throws RocksDBException {
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
		// Fetch revision id
		final long rev = gid2Rev(gid);

		final var graph = rocksDao.getGraphData(rev);
		if (graph == null) throw new NoSuchElementException("Revision associated with callable missing fromå the graph database");

		final Record2<String, String> record = context.select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(PackageVersions.PACKAGE_VERSIONS.ID.eq(rev)).fetchOne();
		final String[] a = record.component1().split(":");
		final String groupId = a[0];
		final String artifactId = a[1];
		final String version = record.component2();
		final Set<Revision> dependencySet = resolver.resolveDependencies(groupId, artifactId, version, -1, context, true);

		LOGGER.debug("Found " + dependencySet.size() + " dependencies");

		final DatabaseMerger dm = new DatabaseMerger(LongOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id)), context, rocksDao);
		final var stitchedGraph = dm.mergeWithCHA(groupId + ":" + artifactId + ":" + version);

		if (!stitchedGraph.nodes().contains(gid)) throw new IllegalStateException("The stitched graph does not contain the given callable");

		LOGGER.debug("Stiched graph has " + stitchedGraph.numNodes() + " nodes");

		final ArrayList<Result> results = new ArrayList<>();

		final ClosestFirstIterator<Long, LongLongPair> reachable = new ClosestFirstIterator<>(stitchedGraph, Long.valueOf(gid));
		reachable.forEachRemaining((x) -> {
			if (filter.test(x)) results.add(new Result(x, (stitchedGraph.outdegree(x) + stitchedGraph.indegree(x)) / reachable.getShortestPathLength(x)));
		});

		LOGGER.debug("Found " + results.size() + " reachable nodes");

		Collections.sort(results, (x, y) -> Double.compare(y.score, x.score));
		return results;
	}

	/**
	 * Computes the callables satisfying the given predicate and coreachable from the provided callable,
	 * and returns them in a ranked list. They will be filtered by the conjuction of {@link #predicateFilters}.
	 *
	 * @param gid the global ID of a callable.
	 * @return a list of {@linkplain Result results}.
	 */
	public List<Result> toCallable(final long gid) throws RocksDBException {
		return toCallable(gid, predicateFilters.stream().reduce(x -> true, LongPredicate::and));
	}

	/**
	 * Computes the callables satisfying the given predicate and coreachable from the provided callable,
	 * and returns them in a ranked list.
	 *
	 * @param gid the global ID of a callable.
	 * @param filter a {@link LongPredicate} that will be used to filter callables.
	 * @return a list of {@linkplain Result results}.
	 */
	public List<Result> toCallable(final long gid, final LongPredicate filter) throws RocksDBException {
		// Fetch revision id
		final long rev = gid2Rev(gid);

		final var graph = rocksDao.getGraphData(rev);
		if (graph == null) throw new NoSuchElementException("Revision associated with callable missing from the graph database");

		Record2<String, String> record = context.select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(PackageVersions.PACKAGE_VERSIONS.ID.eq(rev)).fetchOne();
		String[] a = record.component1().split(":");
		String groupId = a[0];
		String artifactId = a[1];
		String version = record.component2();
		final Set<Revision> dependentSet = resolver.resolveDependents(groupId, artifactId, version, -1, true);

		LOGGER.debug("Found " + dependentSet.size() + " dependents");

		final LongOpenHashSet dependentIds = LongOpenHashSet.toSet(dependentSet.stream().mapToLong(x -> x.id));
		dependentIds.add(rev);
		LOGGER.debug("Found " + dependentIds.size() + " dependents");

		final ArrayList<Result> results = new ArrayList<>();

		for (final var dependentId : dependentIds) {
			record = context.select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(PackageVersions.PACKAGE_VERSIONS.ID.eq(dependentId)).fetchOne();

			LOGGER.debug("Analyzing dependent " + groupId + ":" + artifactId + ":" + version);
			a = record.component1().split(":");
			groupId = a[0];
			artifactId = a[1];
			version = record.component2();
			final Set<Revision> dependencySet = resolver.resolveDependencies(groupId, artifactId, version, -1, context, true);

			LOGGER.debug("Found " + dependencySet.size() + " dependencies");

			final LongOpenHashSet dependencyIds = LongOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id));
			if (dependentId != rev && !dependencyIds.contains(dependentId)) {
				LOGGER.debug("False dependent");
				continue; // We cannot possibly reach the callable
			}
			final DatabaseMerger dm = new DatabaseMerger(dependencyIds, context, rocksDao);
			final var stitchedGraph = dm.mergeWithCHA(groupId + ":" + artifactId + ":" + version);
			if (!stitchedGraph.nodes().contains(gid)) continue; // We cannot possibly reach the callable

			LOGGER.debug("Stiched graph has " + stitchedGraph.numNodes() + " nodes");
			final int sizeBefore = results.size();

			final ClosestFirstIterator<Long, LongLongPair> coreachable = new ClosestFirstIterator<>(new EdgeReversedGraph<>(stitchedGraph), Long.valueOf(gid));
			coreachable.forEachRemaining((x) -> {
				if (filter.test(x)) results.add(new Result(x, (stitchedGraph.outdegree(x) + stitchedGraph.indegree(x)) / coreachable.getShortestPathLength(x)));
			});

			LOGGER.debug("Found " + (results.size() - sizeBefore) + " coreachable nodes");
		}

		LOGGER.debug("Found overall " + results.size() + " coreachable nodes");
		Collections.sort(results, (x, y) -> Double.compare(y.score, x.score));
		return results;
	}

	// dbContext=PostgresConnector.getDSLContext("jdbc:postgresql://monster:5432/fasten_java","fastenro");rocksDao=new
	// eu.fasten.core.data.graphdb.RocksDao("/home/vigna/graphdb/",true);

	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(SearchEngine.class.getName(), "Creates an instance of SearchEngine and answers queries from the command line (rlwrap recommended).", new Parameter[] {
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		final String rocksDb = jsapResult.getString("rocksDB");
		final String resolverGraph = jsapResult.getString("resolverGraph");

		final SearchEngine searchEngine = new SearchEngine(jdbcURI, database, "/mnt/fasten/graphdb", resolverGraph);
		searchEngine.context.settings().withParseUnknownFunctions(ParseUnknownFunctions.IGNORE);

		final Scanner scanner = new Scanner(System.in);
		for(;;) {
			System.out.print("[$help for help]>");
			System.out.flush();
			if (!scanner.hasNextLine()) break;
			String line = scanner.nextLine();
			if (line.length() == 0) continue;
			Matcher matcher = COMMAND_REGEXP.matcher(line);
			if (matcher.matches()) {
				searchEngine.executeCommand(matcher.group(1));
				continue;
			}
			try {
				final char dir = line.charAt(0);
				line = line.substring(1);
				final FastenJavaURI uri = FastenJavaURI.create(line);
				final long gid = Util.getCallableGID(uri, searchEngine.context);
				if (gid == -1) {
					System.err.println("Unknown URI " + uri);
					continue;
				}
				final var r = dir == '+' ? searchEngine.fromCallable(gid) : searchEngine.toCallable(gid);
				for(int i = 0; i < Math.min(searchEngine.limit, r.size()); i++)
					System.out.println(r.get(i).gid + "\t" + Util.getCallableName(r.get(i).gid, searchEngine.context) + "\t" + r.get(i).score);
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}

}