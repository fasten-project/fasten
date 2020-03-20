package eu.fasten.core.plugins;

import java.sql.SQLException;

/**
 * A plug-in that needs to get access to a database should implement this interface.
 */
public interface DBConnector {

    /**
     *  This method should provide DB credentials to the underlying DB for getting a connection.
     * @param DBUrl The URL of a database
     * @param username a username for the database
     * @param password a password for the username
     */
    public void getDBAccess(String DBUrl, String username, String password) throws SQLException;

}
