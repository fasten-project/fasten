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

import static eu.fasten.core.maven.data.Scope.TEST;
import static eu.fasten.core.maven.resolution.ResolverConfig.resolve;

import java.io.File;
import java.sql.SQLException;

import org.jooq.DSLContext;

import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.resolution.MavenResolverIO;
import eu.fasten.core.maven.resolution.ResolverConfig;

public class MyRunner {

    private static final String MNT = "/Users/seb/tmp/foo2/";
    private static final File DIR_DEPGRAPH = new File(MNT + "depgraph/");

    private static final ResolverConfig CONFIG = resolve().scope(TEST).alwaysIncludeProvided(true);

    public static void main(String[] args) throws Exception {

        if (!DIR_DEPGRAPH.exists()) {
            DIR_DEPGRAPH.mkdirs();
        }

        var resolver = new MavenResolverIO(getDbContext(), DIR_DEPGRAPH).loadResolver();

        var coord = "org.springframework:spring-beans:4.1.4.RELEASE".split(":");

        var deps = resolver.resolveDependencies(coord[0], coord[1], coord[2], CONFIG);
        System.out.println("Dependencies:");
        for (var d : deps) {
            System.out.printf("- %s:%s:%s (%s)\n", d.getGroupId(), d.getArtifactId(), d.version.toString(), d.scope);
        }

        var dependents = resolver.resolveDependents(coord[0], coord[1], coord[2], CONFIG);
        System.out.println("Dependents:");
        for (var d : dependents) {
            System.out.printf("- %s:%s:%s (%s)\n", d.getGroupId(), d.getArtifactId(), d.version.toString(), d.scope);
        }
    }

    private static DSLContext getDbContext() throws SQLException {
        return PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5432/fasten_java", "fasten", false);
    }
}