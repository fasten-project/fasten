package eu.fasten.core.plugins;

import org.jooq.DSLContext;

/**
 * A plugin that uses DependencyGraphResolver
 */
public interface DependencyGraphUser {

    /**
     * In order to use the dependency graph resolver, it must be loaded using database connection
     * and a path to the serialized dependency graph.
     *
     * @param dbContext    Connection to the database
     * @param depGraphPath Path to the serialized dependency graph
     */
    void loadGraphResolver(DSLContext dbContext, String depGraphPath);

}
