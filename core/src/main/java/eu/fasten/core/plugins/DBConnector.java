package eu.fasten.core.plugins;

import org.jooq.DSLContext;

/**
 * A plug-in that needs to get access to a database should implement this interface.
 */
public interface DBConnector extends FastenPlugin {

    /**
     * This methods sets a DB connection for plug-ins.
     *
     * @param dslContext A DSL context for JOOQ to query the database.
     */
    void setDBConnection(DSLContext dslContext);

}
