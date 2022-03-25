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
import eu.fasten.core.maven.data.Scope;
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

            var pom = this.graph.find(gav);
            if (pom == null) {
                return Set.of();
            } else {
                return resolve(config, new HashSet<>(), QueueData.startFrom(pom));
            }
        }

        var pom = new PomAnalysisResult();
        pom.groupId = "virtual-file";
        pom.artifactId = "pom";
        pom.version = "0.0.1";
        pom.releaseDate = config.timestamp;
        pom.dependencies.addAll(toDeps(gavs));

        var depSet = resolve(config, new HashSet<>(), QueueData.startFrom(pom));
        depSet.remove(pom.toRevision());
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
                // TODO check for time

                var depData = QueueData.nest(data);

                if (dep.optional && !config.alwaysIncludeOptional && depData.isTransitiveDep()) {
                    continue;
                }
                
                if(dep.scope == Scope.PROVIDED && !config.alwaysIncludeProvided && depData.isTransitiveDep()) {
                    continue;
                }

                for (var excl : dep.exclusions) {
                    depData.exclusions.add(String.format("%s:%s", excl.groupId, excl.artifactId));
                }

                var couldBeManaged = !hasVersion(dep) || depData.isTransitiveDep();
                var vcs = couldBeManaged && depData.depMgmt.containsKey(depGA) //
                        ? depData.depMgmt.get(depGA) //
                        : dep.versionConstraints;

                var depPom = graph.find(depGA, vcs, config.timestamp);

                if (depPom != null) {
                    depData.setPom(depPom);
                    queue.add(depData);
                }
            }
        }

        return depSet;
    }

    private static boolean hasVersion(Dependency dep) {
        if (dep.versionConstraints.isEmpty()) {
            return false;
        }
        if (dep.versionConstraints.size() == 1) {
            return !dep.versionConstraints.iterator().next().spec.isEmpty();
        }
        return true;
    }

    private static Set<Dependency> toDeps(Collection<String> gavs) {
        return gavs.stream() //
                .map(gav -> gav.split(":")) //
                .map(parts -> new Dependency(parts[0], parts[0], parts[1])) //
                .collect(Collectors.toSet());
    }

    private static class QueueData {

        private int depth = 0;

        public PomAnalysisResult pom;
        public final Set<String> exclusions = new HashSet<>();
        public final Map<String, Set<VersionConstraint>> depMgmt = new HashMap<>();

        public boolean isTransitiveDep() {
            // 0 = source, 1 = direct, 2 = transitive
            return depth > 1;
        }

        public void setPom(PomAnalysisResult pom) {
            this.pom = pom;
            for (var dm : pom.dependencyManagement) {
                var ga = String.format("%s:%s", dm.groupId, dm.artifactId);
                depMgmt.put(ga, dm.versionConstraints);
            }
        }

        public static QueueData nest(QueueData outer) {
            var inner = new QueueData();
            inner.exclusions.addAll(outer.exclusions);
            inner.depth = outer.depth + 1;
            inner.depMgmt.putAll(outer.depMgmt);
            return inner;
        }

        private static QueueData startFrom(PomAnalysisResult pom) {
            var data = new QueueData();
            data.setPom(pom);
            return data;
        }
    }
}