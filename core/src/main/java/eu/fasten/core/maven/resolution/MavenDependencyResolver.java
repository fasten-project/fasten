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
import eu.fasten.core.maven.data.ResolvedRevision;
import eu.fasten.core.maven.data.Scope;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyResolver {

    private MavenDependencyData graph = new MavenDependencyData();

    public void setData(MavenDependencyData graph) {
        this.graph = graph;
    }

    public Set<ResolvedRevision> resolve(Collection<String> gavs, ResolverConfig config) {

        failForImportProvidedSystemScope(config);

        if (gavs.size() == 1) {
            var parts = gavs.iterator().next().split(":");
            var ga = String.format("%s:%s", parts[0], parts[1]);

            var pom = this.graph.find(ga, Set.of(new VersionConstraint(parts[2])), config.resolveAt);
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
        pom.releaseDate = config.resolveAt;
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

    private Set<ResolvedRevision> resolve(ResolverConfig config, Set<MavenProduct> addedProducts,
            QueueData startingData) {

        if (startingData.pom.releaseDate > config.resolveAt) {
            throw new MavenResolutionException("Requested POM has been released after resolution timestamp");
        }

        var depSet = new HashSet<ResolvedRevision>();

        var queue = new LinkedList<QueueData>();
        queue.add(startingData);

        while (!queue.isEmpty()) {
            var data = queue.poll();

            var p = data.pom.toProduct();
            if (addedProducts.contains(p)) {
                continue;
            }
            addedProducts.add(p);

            depSet.add(toResolvedRevision(data));

            if (data.scope == SYSTEM) {
                continue;
            }

            for (var dep : data.pom.dependencies) {
                var depGA = dep.toGA();
                if (data.exclusions.contains(depGA)) {
                    continue;
                }

                if (!isScopeCovered(config.scope, dep.getScope(), config.alwaysIncludeProvided, data.scope)) {
                    continue;
                }

                var depData = QueueData.nest(data, dep.getScope());
                if (depData.isTransitiveDep()) {

                    if (config.depth == ResolverDepth.DIRECT) {
                        continue;
                    }

                    if (dep.isOptional() && !config.alwaysIncludeOptional) {
                        continue;
                    }

                    if (dep.getScope() == Scope.SYSTEM && config.scope == RUNTIME) {
                        continue;
                    }

                    if (dep.getScope() == Scope.PROVIDED && !config.alwaysIncludeProvided) {
                        continue;
                    }

                    if (dep.getScope() == TEST) {
                        continue;
                    }
                }

                for (var excl : dep.getExclusions()) {
                    var exclGA = new StringBuilder().append(excl.groupId).append(':').append(excl.artifactId)
                            .toString();
                    depData.exclusions.add(exclGA);
                }

                var couldBeManaged = !hasVersion(dep) || depData.isTransitiveDep();
                var vcs = couldBeManaged && depData.depMgmt.containsKey(depGA) //
                        ? depData.depMgmt.get(depGA) //
                        : dep.getVersionConstraints();

                var depPom = graph.find(depGA, vcs, config.resolveAt);

                if (depPom != null) {
                    depData.setPom(depPom);
                    queue.add(depData);
                }
            }
        }

        return depSet;
    }

    private static ResolvedRevision toResolvedRevision(QueueData data) {
        return new ResolvedRevision(data.pom.toRevision(), data.scope);
    }

    private static boolean isScopeCovered(Scope request, Scope dep, boolean alwaysIncludeProvided, Scope inherited) {
        if (dep == request) {
            return true;
        }
        if (dep == SYSTEM) {
            return request != RUNTIME;
        }
        if (dep == PROVIDED) {
            return alwaysIncludeProvided || request != RUNTIME;
        }
        if (dep == RUNTIME) {
            return request == TEST || inherited == PROVIDED;
        }
        if (dep == COMPILE) {
            return request == RUNTIME || request == TEST;
        }
        return false;
    }

    private static boolean hasVersion(Dependency dep) {
        if (dep.getVersionConstraints().isEmpty()) {
            return false;
        }
        if (dep.getVersionConstraints().size() == 1) {
            // TODO this case should be obsolete
            return !dep.getVersionConstraints().iterator().next().getSpec().isEmpty();
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
        public Scope scope = COMPILE;
        public final Set<String> exclusions = new HashSet<>();
        public final Map<String, Set<VersionConstraint>> depMgmt = new HashMap<>();

        public boolean isTransitiveDep() {
            // 0 = source, 1 = direct, 2 = transitive
            return depth > 1;
        }

        public void setPom(Pom pom) {
            this.pom = pom;
            for (var dm : pom.dependencyManagement) {
                depMgmt.put(dm.toGA(), dm.getVersionConstraints());
            }
        }

        public static QueueData nest(QueueData outer, Scope depScope) {
            var inner = new QueueData();
            inner.exclusions.addAll(outer.exclusions);
            inner.depth = outer.depth + 1;
            inner.depMgmt.putAll(outer.depMgmt);
            inner.scope = getMostSpecific(outer.scope, depScope);
            return inner;
        }

        private static Scope getMostSpecific(Scope cur, Scope dep) {
            if (dep == SYSTEM || dep == PROVIDED) {
                return dep;
            }
            if (cur == PROVIDED) {
                return cur;
            }
            if (cur == COMPILE) {
                return dep;
            }
            if (cur == RUNTIME) {
                if (dep == COMPILE) {
                    return RUNTIME;
                }
                return dep;
            }
            return TEST;
        }

        private static QueueData startFrom(Pom pom) {
            var data = new QueueData();
            data.setPom(pom);
            return data;
        }
    }
}