package eu.fasten.core.examples;

import java.sql.SQLException;
import java.util.Map;

import org.jgrapht.alg.scoring.HarmonicCentrality;
import org.jgrapht.alg.scoring.PageRank;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.ClosestFirstIterator;
import org.jooq.DSLContext;
import org.rocksdb.RocksDBException;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;

public class CallGraphAnalysisExample {

	private static String getCallableName(final long id, final DSLContext context) {
		return context.select(Callables.CALLABLES.FASTEN_URI).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(id)).fetchOne().component1();
	}

	public static void main(final String args[]) throws JSAPException, IllegalArgumentException, SQLException, RocksDBException {
		final SimpleJSAP jsap = new SimpleJSAP(CallGraphAnalysisExample.class.getName(), "Analyzes a revision call graph", new Parameter[] {
				new FlaggedOption("postgres", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'p', "postgres", "The URI of the Postgres server."),
				new FlaggedOption("db", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'd', "db", "The Postgres database."),
				new FlaggedOption("rocksdb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'r', "rocksdb", "The path to the RocksDB graph database."),
				new UnflaggedOption("group", JSAP.STRING_PARSER, JSAP.NOT_REQUIRED, "The Maven group of the revision."),
				new UnflaggedOption("product", JSAP.STRING_PARSER, JSAP.NOT_REQUIRED, "The product associated with the revision."),
				new UnflaggedOption("version", JSAP.STRING_PARSER, JSAP.NOT_REQUIRED, "The version of the revision."), });

		final JSAPResult jsapResult = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final String group = jsapResult.getString("group");
		final String product = jsapResult.getString("product");
		final String version = jsapResult.getString("version");

		// Connect to the Postgres database (you'll need to set the password as a system variable)
		final var context = PostgresConnector.getDSLContext(jsapResult.getString("postgres"), jsapResult.getString("db"));
		// Connect to the graph database
		final var rocksDao = new eu.fasten.core.data.graphdb.RocksDao(jsapResult.getString("rocksdb"), true);

		// Retrieve the ID of the requested revision
		final var packageName = group + Constants.mvnCoordinateSeparator + product;
		final var result = context.select(PackageVersions.PACKAGE_VERSIONS.ID).from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES).on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName)).and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version)).fetchOne();
		if (result == null) throw new IllegalArgumentException("The requested revision (group=" + group + ", product=" + product + ", version=" + version + ") is not in the Postgres database");
		final long id = result.component1();

		// Retrieve the associated revision call graph
		final DirectedGraph graph = rocksDao.getGraphData(id);
		if (graph == null) throw new IllegalArgumentException("The requested revision (group=" + group + ", product=" + product + ", version=" + version + ", id=" + id + ") is not in the RocksDB database");

		// Now we compute PageRank
		final PageRank<Long, long[]> pageRank = new PageRank<>(graph);

		// Find node with highest PageRank
		double bestPR = 0;
		long bestPRNode = -1;
		for (final long v : graph.nodes()) {
			final double pr = pageRank.getVertexScore(v);
			if (pr > bestPR) {
				bestPR = pr;
				bestPRNode = v;
			}
		}

		System.out.println("The callable with highest PageRank is " + getCallableName(bestPRNode, context) + " (id=" + bestPRNode + ")");

		// Now we compute harmonic centrality
		final HarmonicCentrality<Long, long[]> harmonicCentrality = new HarmonicCentrality<>(graph);
		final Map<Long, Double> harmonicCentralityScores = harmonicCentrality.getScores();

		// Find node with highest harmonic centrality
		double bestH = 0;
		long bestHNode = -1;
		for (final long v : graph.nodes()) {
			final double h = harmonicCentralityScores.get(v);
			if (h > bestH) {
				bestH = h;
				bestHNode = v;
			}
		}

		System.out.println("The callable with highest PageRank is " + getCallableName(bestHNode, context) + " (id=" + bestHNode + ")");

		// Now we find reachable nodes in a radius of 3, starting from the first enumerated vertex node
		long v = graph.nodes().iterator().nextLong();
		System.out.println("Finding nodes reachable within distance 3 from " + getCallableName(v, context) + " (id=" + v + ")");
		ClosestFirstIterator<Long, long[]> closestFirstIterator = new ClosestFirstIterator<>(graph, v, 3);
		closestFirstIterator.forEachRemaining((x) -> {
			System.out.println("Found node " + x + " at distance " + closestFirstIterator.getShortestPathLength(x));
		});

		// Now we find coreachable nodes in a radius of 3, starting from the first enumerated vertex node.
		// Note that we're using JGraphT methods here.
		final EdgeReversedGraph<Long, long[]> transpose = new EdgeReversedGraph<>(graph);
		v = transpose.vertexSet().iterator().next().longValue();
		System.out.println("Finding nodes coreachable within distance 3 from " + getCallableName(v, context) + " (id=" + v + ")");
		closestFirstIterator = new ClosestFirstIterator<>(transpose, v, 3);
		closestFirstIterator.forEachRemaining((x) -> {
			System.out.println("Found node " + x + " at distance " + closestFirstIterator.getShortestPathLength(x));
		});

		context.close();
		rocksDao.close();
	}
}
