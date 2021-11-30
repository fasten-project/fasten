package eu.fasten.core.search;

import java.util.Set;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.GraphMavenResolver;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.search.predicate.CachingPredicateFactory;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

public class Example {
	private static final Logger LOGGER = LoggerFactory.getLogger(Example.class);

	/** The handle to the Postgres metadata database. */
	private final DSLContext context;
	/** The handle to the RocksDB DAO. */
	private final RocksDao rocksDao;
	/** The resolver. */
	private final GraphMavenResolver resolver;
	
	public Example(final String jdbcURI, final String database, final String rocksDb, final String resolverGraph) throws Exception {
		this(PostgresConnector.getDSLContext(jdbcURI, database, false), new RocksDao(rocksDb, true), resolverGraph);
	}

	public Example(final DSLContext context, final RocksDao rocksDao, final String resolverGraph) throws Exception {
		this.context = context;
		this.rocksDao = rocksDao;
		resolver = new GraphMavenResolver();
		resolver.buildDependencyGraph(context, resolverGraph);
		resolver.setIgnoreMissing(true);
		new CachingPredicateFactory(context);
	}

	public static void main(final String args[]) throws Exception {
		final SimpleJSAP jsap = new SimpleJSAP(Example.class.getName(), "Example.", new Parameter[] {
				new UnflaggedOption("revisionId", JSAP.LONG_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The revision id."),
				new UnflaggedOption("jdbcURI", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The JDBC URI."),
				new UnflaggedOption("database", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The database name."),
				new UnflaggedOption("rocksDb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to the RocksDB database of revision call graphs."),
				new UnflaggedOption("resolverGraph", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The path to a resolver graph (will be created if it does not exist)."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final long id = jsapResult.getLong("revisionId");
		final String jdbcURI = jsapResult.getString("jdbcURI");
		final String database = jsapResult.getString("database");
		final String rocksDb = jsapResult.getString("rocksDb");
		final String resolverGraph = jsapResult.getString("resolverGraph");

		final Example example = new Example(jdbcURI, database, rocksDb, resolverGraph);
		final DSLContext context = example.context;

		final var graph = example.rocksDao.getGraphData(id);
		if (graph == null) System.exit(1);
		
		final Record2<String, String> record = context.select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(PackageVersions.PACKAGE_VERSIONS.ID.eq(Long.valueOf(id))).fetchOne();
		final String[] a = record.component1().split(":");
		final String groupId = a[0];
		final String artifactId = a[1];
		final String version = record.component2();
		final Set<Revision> dependencySet = example.resolver.resolveDependencies(groupId, artifactId, version, -1, context, true);
		final String name = groupId + ":" + artifactId + "$" + version;
		LOGGER.info("Analyzing graph " + name  + " with id " + id);
		LOGGER.info("Dependencies: " + dependencySet);

		var deps = LongLinkedOpenHashSet.toSet(dependencySet.stream().mapToLong(x -> x.id));
		deps.addAndMoveToFirst(id);
		final var dm = new CGMerger(deps, context, example.rocksDao);
	}
}