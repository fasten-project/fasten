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

import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.resolution.DependencyGraphBuilder;
import eu.fasten.core.maven.resolution.IMavenResolver;

public class MyRunner {

    private static final String MNT = "/Users/seb/tmp/foo2/";
    private static final String GRAPH_FILE = MNT + "depgraph.db";

    public static void main(String[] args) throws Exception {
        var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5432/fasten_java", "fasten",
                false);

        IMavenResolver res = DependencyGraphBuilder.init(dbContext, GRAPH_FILE);

        var deps = res.resolveDependencies("org.apache.commons", "commons-lang3", "3.2");
        System.out.println("Dependencies:");
        for (var d : deps) {
            System.out.printf("- %s:%s:%s\n", d.getGroupId(), d.getArtifactId(), d.version.toString());
        }

        var dependents = res.resolveDependents("org.apache.commons", "commons-lang3", "3.2");
        System.out.println("Dependents:");
        for (var d : dependents) {
            System.out.printf("- %s:%s:%s\n", d.getGroupId(), d.getArtifactId(), d.version.toString());
        }
    }
}