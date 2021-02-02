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

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.function.LongPredicate;

import org.jgrapht.traverse.ClosestFirstIterator;
import org.jooq.conf.ParseUnknownFunctions;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.rocksdb.RocksDBException;

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
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

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

	public final static class Result {
		public long gid;
		public double score;

		public Result(final long gid, final double score) {
			this.gid = gid;
			this.score = score;
		}

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
	}

	public long gid2Rev(final long gid) {
		return context.select(PackageVersions.PACKAGE_VERSIONS.ID).from(PackageVersions.PACKAGE_VERSIONS).
				join(Modules.MODULES).on(Modules.MODULES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID)).
				join(Callables.CALLABLES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID)).where(Callables.CALLABLES.ID.eq(Long.valueOf(gid))).fetchOne().component1().longValue();
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

		final DatabaseMerger dm = new DatabaseMerger(LongOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id)), context, rocksDao);
		final var stitchedGraph = dm.mergeWithCHA(groupId + ":" + artifactId + ":" + version);

		if (!stitchedGraph.nodes().contains(gid)) throw new IllegalStateException("The stitched graph does not contain the given callable");

		final ArrayList<Result> results = new ArrayList<>();

		final ClosestFirstIterator<Long, LongLongPair> reachable = new ClosestFirstIterator<>(stitchedGraph, Long.valueOf(gid));
		reachable.forEachRemaining((x) -> {
			if (filter.test(x)) results.add(new Result(x, (stitchedGraph.outdegree(x) + stitchedGraph.indegree(x)) / reachable.getShortestPathLength(x)));
		});

		Collections.sort(results, (x, y) -> Double.compare(x.score, y.score));
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
		final var parsingConnection = searchEngine.context.parsingConnection();
		searchEngine.context.settings().withParseUnknownFunctions(ParseUnknownFunctions.IGNORE);
		final var s = parsingConnection.createStatement();

		final Scanner scanner = new Scanner(System.in);
		while (scanner.hasNextLine()) {
			final String line = scanner.nextLine();
			try {
				final FastenJavaURI uri = FastenJavaURI.create(line);
				final java.sql.ResultSet result = s.executeQuery("select id from callables where digest(fasten_uri, 'sha1'::text) = digest('" + uri + "', 'sha1'::text)");

				result.next();
				var r = searchEngine.fromCallable(result.getLong(1), x -> true);
				System.err.println(r.subList(0, 10));
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}

}