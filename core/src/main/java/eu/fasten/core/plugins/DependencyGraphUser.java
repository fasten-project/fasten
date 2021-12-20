package eu.fasten.core.plugins;

import org.jooq.DSLContext;

/**
 * A plugin that uses DependencyGraphResolver
 */
public interface DependencyGraphUser {

    /**
     * In order to use the dependency CPythonGraph resolver, it must be loaded using database connection
     * and a path to the serialized dependency CPythonGraph.
     *
     * @param dbContext    Connection to the database
     * @param depGraphPath Path to the serialized dependency CPythonGraph
     */
    void loadGraphResolver(DSLContext dbContext, String depGraphPath);

}
