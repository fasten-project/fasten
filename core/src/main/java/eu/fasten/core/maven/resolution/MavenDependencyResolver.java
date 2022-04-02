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

import static eu.fasten.core.maven.data.Scope.COMPILE;
import static eu.fasten.core.maven.data.Scope.PROVIDED;
import static eu.fasten.core.maven.data.Scope.RUNTIME;
import static eu.fasten.core.maven.data.Scope.SYSTEM;
import static eu.fasten.core.maven.data.Scope.TEST;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.MavenProduct;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.data.Scope;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyResolver {

    private MavenDependencyData graph = new MavenDependencyData();

    public void setData(MavenDependencyData graph) {
        this.graph = graph;
    }

    public Set<Revision> resolve(Collection<String> gavs, ResolverConfig config) {

        failForImportProvidedSystemScope(config);

        if (gavs.size() == 1) {
            var parts = gavs.iterator().next().split(":");
            var ga = String.format("%s:%s", parts[0], parts[1]);

            var pom = this.graph.find(ga, Set.of(new VersionConstraint(parts[2])), config.timestamp);
            if (pom == null) {
                throw new MavenResolutionException(
                        String.format("Cannot find coordinate %s:%s:%s", parts[0], parts[1], parts[2]));
            } else {
                return resolve(config, new HashSet<>(), QueueData.startFrom(pom));
            }
        }

        var pom = new Pom();
        pom.groupId = "virtual-file";
        pom.artifactId = "pom";
        pom.version = "0.0.1";
        pom.releaseDate = config.timestamp;
        pom.dependencies.addAll(toDeps(gavs));

        var depSet = resolve(config, new HashSet<>(), QueueData.startFrom(pom));
        depSet.remove(pom.toRevision());
        return depSet;
    }

    private void failForImportProvidedSystemScope(ResolverConfig config) {
        switch (config.scope) {
        case IMPORT:
        case PROVIDED:
        case SYSTEM:
            throw new IllegalArgumentException(String.format("Invalid resolution scope: %s", config.scope));
        default:
            // nothing to do
        }
    }

    private Set<Revision> resolve(ResolverConfig config, Set<MavenProduct> addedProducts, QueueData startingData) {

        if (startingData.pom.releaseDate > config.timestamp) {
            throw new MavenResolutionException("Requested POM has been released after resolution timestamp");
        }

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

                if (!isScopeCovered(config.scope, dep.scope)) {
                    continue;
                }

                // TODO check for time

                var depData = QueueData.nest(data);
                if (depData.isTransitiveDep()) {

                    if (config.depth == ResolverDepth.DIRECT) {
                        continue;
                    }

                    if (dep.optional && !config.alwaysIncludeOptional) {
                        continue;
                    }

                    if (dep.scope == Scope.PROVIDED && !config.alwaysIncludeProvided) {
                        continue;
                    }

                    if (dep.scope == TEST) {
                        continue;
                    }
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

    private static boolean isScopeCovered(Scope target, Scope dep) {
        if (dep == target) {
            return true;
        }
        if (dep == PROVIDED || dep == SYSTEM) {
            return true;
        }
        if (dep == RUNTIME) {
            return target == TEST;
        }
        if (dep == COMPILE) {
            return target == RUNTIME || target == TEST;
        }
        return false;
    }

    private static boolean hasVersion(Dependency dep) {
        if (dep.versionConstraints.isEmpty()) {
            return false;
        }
        if (dep.versionConstraints.size() == 1) {
            // TODO this case should be obsolete
            return !dep.versionConstraints.iterator().next().spec.isEmpty();
        }
        return true;
    }

    private static Set<Dependency> toDeps(Collection<String> gavs) {
        return gavs.stream() //
                .map(gav -> gav.split(":")) //
                .map(parts -> new Dependency(parts[0], parts[1], parts[2])) //
                .collect(Collectors.toSet());
    }

    private static class QueueData {

        private int depth = 0;

        public Pom pom;
        public final Set<String> exclusions = new HashSet<>();
        public final Map<String, Set<VersionConstraint>> depMgmt = new HashMap<>();

        public boolean isTransitiveDep() {
            // 0 = source, 1 = direct, 2 = transitive
            return depth > 1;
        }

        public void setPom(Pom pom) {
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

        private static QueueData startFrom(Pom pom) {
            var data = new QueueData();
            data.setPom(pom);
            return data;
        }
    }
}