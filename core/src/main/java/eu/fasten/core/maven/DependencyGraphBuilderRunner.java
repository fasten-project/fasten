/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.core.maven;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.graph.MavenGraph;
import eu.fasten.core.maven.utils.DependencyGraphUtilities;
import picocli.CommandLine;

@CommandLine.Command(name = "DependencyGraphBuilderRunner")
public class DependencyGraphBuilderRunner implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilderRunner.class);
    
    @CommandLine.Option(names = {"-p", "--serializedPath"},
            paramLabel = "PATH",
            description = "Path to load a serialized Maven dependency graph from",
            required = true)
    protected String serializedPath;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            required = true)
    protected String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            required = true)
    protected String dbUser;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new DependencyGraphBuilder()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public void run()  {

        DSLContext dbContext;
        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser, true);
        } catch (Exception e) {
            logger.warn("Could not connect to Database", e);
            return;
        }
        try {
            MavenGraph graph;
            if(DependencyGraphUtilities.doesDependencyGraphExist(serializedPath)) {
                graph = DependencyGraphUtilities.loadDependencyGraph(serializedPath);
            } else {
                graph = DependencyGraphUtilities.buildDependencyGraphFromScratch(dbContext, serializedPath);
            }
        } catch (Exception e) {
            logger.warn("Could not load serialized dependency graph from {}\n", serializedPath, e);
            return;
        }
    }
}