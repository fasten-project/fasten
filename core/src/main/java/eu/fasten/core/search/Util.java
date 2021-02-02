package eu.fasten.core.search;

import java.sql.SQLException;

import org.jooq.DSLContext;
import org.jooq.conf.Settings;

import eu.fasten.core.data.FastenURI;

/**
 * Miscellaneous utility methods.
 */

public class Util {

	private Util() {}

	/**
	 * Given a FASTEN URI and a database connection, returns the associated GID.
	 *
	 * @implSpec This method uses the SHA-based index.
	 *
	 * @implNote The provided {@link DSLContext} <strong>must</strong> be able to parse
	 *           {@linkplain Settings#withParseUnknownFunctions(org.jooq.conf.ParseUnknownFunctions)
	 *           unknown functions}.
	 *
	 * @param uri a FASTEN URI.
	 * @param context a database connection that is able to parse
	 *            {@linkplain Settings#withParseUnknownFunctions(org.jooq.conf.ParseUnknownFunctions)
	 *            unknown functions}.
	 * @return the GID of {@code uri}, if {@code uri} is in the database; &minus;1 otherwise.
	 */

	public long getGID(final FastenURI uri, final DSLContext context) throws SQLException {
		final String product = uri.getRawProduct();
		final String version = uri.getRawVersion();
		final String path = uri.getRawPath();
		final var parsingConnection = context.parsingConnection();
        final var statement = parsingConnection.createStatement();

		final String query = "select id from callables " +
				"join modules on modules.id=callables.modules.id " +
				"join package_versions where package_versions.id=modules.package_version.id " +
				"join packages where packages.id=packages_versions.package.id" +
				"where package.package_name='" + product.replace("'", "\'") + 
				"' and package_versions.version='" + version.replace("'", "\'") +
				"' and digest(fasten_uri, 'sha1'::text) = digest('" + path.replace("'", "\'") + "', 'sha1'::text)";

		final java.sql.ResultSet result = statement.executeQuery(query);
		return result.next() ? -1 : result.getLong(1);
	}

}
