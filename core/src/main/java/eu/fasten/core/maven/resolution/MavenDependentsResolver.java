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
package eu.fasten.core.maven.resolution;

import static eu.fasten.core.maven.resolution.ResolverDepth.TRANSITIVE;
import static eu.fasten.core.utils.Asserts.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.MavenProduct;
import eu.fasten.core.maven.data.PomAnalysisResultX;
import eu.fasten.core.maven.data.Revision;

public class MavenDependentsResolver {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MavenDependentsResolver.class);

    private MavenDependentsGraph graph;

    public MavenDependentsResolver(MavenDependentsGraph graph) {
        this.graph = graph;
    }

    public Set<Revision> resolve(Revision r, ResolverConfig config) {
        assertTrue(graph.pomForRevision.containsKey(r), "no pom found for provided revision");
        var pom = graph.pomForRevision.get(r);
        return resolve(pom, config, new HashSet<>());
    }

    private HashSet<Revision> resolve(PomAnalysisResultX dependency, ResolverConfig config, Set<Object> visited) {
        assertTrue(dependency.releaseDate <= config.timestamp, "provided revision is newer than resolution date");

        if (visited.contains(dependency)) {
            LOG.info("Dependency has been visited before, skipping.");
            return new HashSet<Revision>();
        }
        visited.add(dependency);

        var dependents = new HashSet<Revision>();

        var product = dependency.toProduct();
        for (var potentialDependent : graph.dependentsForProduct.getOrDefault(product, Set.of())) {

            // skip dependents with too recent releases
            if (potentialDependent.releaseDate > config.timestamp) {
                continue;
            }

            // find correct dependency declaration
            var declaration = find(product, potentialDependent.dependencies);

            // check whether version of pom matches the dependency declaration
            if (doesVersionMatch(declaration, dependency)) {
                dependents.add(potentialDependent.toRevision());
                if (config.depth == TRANSITIVE) {
                    dependents.addAll(resolve(potentialDependent, config, visited));
                }
            }
        }
        return dependents;
    }

    private boolean doesVersionMatch(Dependency dep, PomAnalysisResultX pom) {
        for (var vc : dep.versionConstraints) {
            if (vc.matches(pom.version)) {
                return true;
            }
        }
        return false;
    }

    private static Dependency find(MavenProduct product, Set<Dependency> dependencies) {
        for (var dep : dependencies) {
            if (product.equals(dep.product())) {
                return dep;
            }
        }
        throw new IllegalStateException("Cannot find reported dependency");
    }
}