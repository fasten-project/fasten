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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.MavenProduct;
import eu.fasten.core.maven.data.PomAnalysisResult;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyResolver {

    private MavenDependencyGraph graph = new MavenDependencyGraph();

    public void setData(MavenDependencyGraph graph) {
        this.graph = graph;
    }

    public Set<Revision> resolve(Collection<String> gavs, ResolverConfig config) {

        // TODO ensure that date(GAV) > config.timestamp

        if (gavs.size() == 1) {
            var gav = gavs.iterator().next();

            var data = new QueueData();
            data.pom = this.graph.pomForGav.get(gav);
            if (data.pom == null) {
                return Set.of();
            } else {
                return resolve(config, new HashSet<>(), data);
            }
        }

        var data = new QueueData();
        data.pom = new PomAnalysisResult();
        data.pom.groupId = "virtual-file";
        data.pom.artifactId = "pom";
        data.pom.version = "0.0.1";
        data.pom.releaseDate = config.timestamp;
        data.pom.dependencies.addAll(toDeps(gavs));

        var depSet = resolve(config, new HashSet<>(), data);
        depSet.remove(data.pom.toRevision());
        return depSet;
    }

    private Set<Revision> resolve(ResolverConfig config, Set<MavenProduct> addedProducts, QueueData startingData) {

        var depSet = new HashSet<Revision>();

        var queue = new LinkedList<QueueData>();
        queue.add(startingData);

        while (!queue.isEmpty()) {
            var data = queue.poll();

            var p = data.pom.toProduct();
            if (addedProducts.contains(p)) {
                continue;
            }
            addedProducts.add(p);

            depSet.add(data.pom.toRevision());

            for (var dep : data.pom.dependencies) {
                var depGA = String.format("%s:%s", dep.groupId, dep.artifactId);
                if (data.exclusions.contains(depGA)) {
                    continue;
                }

                // TODO check for scope

                var depData = QueueData.nest(data);
                // TODO just check for isTrans to support depth

                for (var excl : dep.exclusions) {
                    depData.exclusions.add(String.format("%s:%s", excl.groupId, excl.artifactId));
                }

                if (depData.depMgmt.containsKey(depGA)) {
                    System.err.println("TODO: Handle depMgmt sections");
                }

                var vcs = dep.versionConstraints;
                depData.pom = graph.find(depGA, vcs, config.timestamp);
                if (depData.pom != null) {
                    queue.add(depData);
                }
            }
        }

        return depSet;
    }

    private static Set<Dependency> toDeps(Collection<String> gavs) {
        return gavs.stream() //
                .map(gav -> gav.split(":")) //
                .map(parts -> new Dependency(parts[0], parts[0], parts[1])) //
                .collect(Collectors.toSet());
    }

    private static class QueueData {

        public int depth = 0;
        public PomAnalysisResult pom;
        public final Set<String> exclusions = new HashSet<>();
        public final Map<String, Set<VersionConstraint>> depMgmt = new HashMap<>();

        public boolean isTransitiveDep() {
            // 0 = source, 1 = direct, 2 = transitive
            return depth > 1;
        }

        public static QueueData nest(QueueData outer) {
            var inner = new QueueData();
            inner.exclusions.addAll(outer.exclusions);
            inner.depth = outer.depth + 1;

            return inner;
        }
    }
}