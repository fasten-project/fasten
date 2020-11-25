package eu.fasten.core.examples;

import java.sql.SQLException;

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
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;

public class CallGraphAnalysisExample {
	public static void main(final String args[]) throws JSAPException, IllegalArgumentException, SQLException, RocksDBException {
		final SimpleJSAP jsap = new SimpleJSAP(CallGraphAnalysisExample.class.getName(), "Analyzes a revision call graph", new Parameter[] {
				new FlaggedOption("postgres", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'p', "postgres", "The URI of the Postgres server."),
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
		final var context = PostgresConnector.getDSLContext(jsapResult.getString("db"), "fastenro");
		// Connect to the graph database
		final var rocksDao = new eu.fasten.core.data.graphdb.RocksDao(jsapResult.getString("rocksdb"), true);

		// Retrieve the ID of the requested revision
		final var packageName = group + Constants.mvnCoordinateSeparator + product;
		final var result = context.select(PackageVersions.PACKAGE_VERSIONS.ID).
				from(PackageVersions.PACKAGE_VERSIONS).
				join(Packages.PACKAGES).
				on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID)).
				where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName)).and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version)).fetchOne();
		if (result == null) throw new IllegalArgumentException("The requested revision (group=" + group + ", product=" + product + ", version=" + version + ") is not in the Postgres database");
		final long id = result.component1();

		// Retrieve the associated revision call graph
		final DirectedGraph graphData = rocksDao.getGraphData(id);
		if ( graphData == null)  throw new IllegalArgumentException("The requested revision (group=" + group + ", product=" + product + ", version=" + version + ", id=" + id + ") is not in the RocksDB database");
	}
}
