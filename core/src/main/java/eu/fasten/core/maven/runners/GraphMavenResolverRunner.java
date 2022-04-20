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
package eu.fasten.core.maven.runners;

import static eu.fasten.core.maven.resolution.ResolverConfig.resolve;
import static eu.fasten.core.maven.resolution.ResolverDepth.TRANSITIVE;

import java.io.File;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.ResolvedRevision;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.resolution.IMavenResolver;
import eu.fasten.core.maven.resolution.MavenResolver;
import eu.fasten.core.maven.resolution.MavenResolverIO;
import picocli.CommandLine;

@CommandLine.Command(name = "GraphMavenResolverRunner")
public class GraphMavenResolverRunner implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MavenResolver.class);

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new GraphMavenResolverRunner()).execute(args);
        System.exit(exitCode);
    }

    @CommandLine.Option(names = {"-p", "--serializedPath"},
            paramLabel = "PATH",
            description = "Path to load a serialized Maven dependency graph from",
            required = true)
    protected String serializedPath;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:postgres")
    protected String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            defaultValue = "postgres")
    protected String dbUser;

    private IMavenResolver graphResolver;
    
    @Override
    public void run() {


        DSLContext dbContext;
        try {
            dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser, true);
        } catch (SQLException e) {
            logger.error("Could not connect to the database", e);
            return;
        }

        try {
            graphResolver = new MavenResolverIO(dbContext, new File(serializedPath)).loadResolver();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        repl(dbContext);
    }

    public void repl(DSLContext db) {
        System.out.println("Query format: [!]group:artifact:version<:ts>");
        System.out.println("! at the beginning means search for dependents (default: dependencies)");
        System.out.println("ts: Use the provided timestamp for resolution (default: the artifact release timestamp)");
        try (var scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                var input = scanner.nextLine();

                if (input.equals("")) continue;
                if (input.equals("quit") || input.equals("exit")) break;

                var parts = input.split(":");

                if (parts.length < 3 || parts[2] == null) {
                    System.out.println("Wrong input: " + input + ". Format is: [!]group:artifact:version<:ts>");
                    continue;
                }

                var timestamp = -1L;
                if (parts.length > 3 && parts[3] != null) {
                    try {
                        timestamp = Long.parseLong(parts[3]);
                    } catch (NumberFormatException nfe) {
                        System.err.println("Error parsing the provided timestamp");
                        continue;
                    }
                }

                Set<ResolvedRevision> revisions;
                var startTS = System.currentTimeMillis();
                try {
                    var cfg = resolve().at(timestamp).depth(TRANSITIVE);
                    if (parts[0].startsWith("!")) {
                        parts[0] = parts[0].substring(1);
                        revisions = graphResolver.resolveDependents(parts[0], parts[1], parts[2], cfg);
                    } else {
                        revisions = graphResolver.resolveDependencies(parts[0], parts[1], parts[2], cfg);
                    }
                } catch (Exception e) {
                    System.err.println("Error retrieving revisions: " + e.getMessage());
                    e.printStackTrace(System.err);
                    continue;
                }

                for (var rev : revisions.stream().sorted(Comparator.comparing(Revision::toString)).
                        collect(Collectors.toList())) {
                    System.out.println(rev.toString());
                }
                System.err.println(revisions.size() + " revisions, " + (System.currentTimeMillis() - startTS) + " ms");
            }
        }
    }
}