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
import static eu.fasten.core.maven.data.Scope.RUNTIME;
import static eu.fasten.core.maven.data.Scope.SYSTEM;
import static eu.fasten.core.maven.data.Scope.TEST;
import static eu.fasten.core.maven.resolution.ResolverDepth.TRANSITIVE;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.ResolvedRevision;
import eu.fasten.core.maven.data.Scope;

public class MavenDependentsResolver {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MavenDependentsResolver.class);

    private MavenDependentsData data;

    public void setData(MavenDependentsData data) {
        this.data = data;
    }

    public Set<ResolvedRevision> resolve(String gav, ResolverConfig config) {
        failForInvalidScopes(config);

        var pom = data.findPom(gav, config.resolveAt);
        if (pom == null) {
            throw new MavenResolutionException(String.format("Cannot find coordinate %s", gav));
        }

        var dependents = new HashSet<ResolvedRevision>();
        resolve(pom, config, dependents, new HashSet<>(), false);
        return dependents;
    }

    private void failForInvalidScopes(ResolverConfig config) {
        if (config.scope == Scope.IMPORT || config.scope == Scope.PROVIDED || config.scope == Scope.SYSTEM) {
            var msg = "Invalid resolution scope: %s";
            throw new IllegalArgumentException(String.format(msg, config.scope));
        }
    }

    private void resolve(Pom pom, ResolverConfig config, Set<ResolvedRevision> dependents, Set<Object> visited,
            boolean isTransitiveDep) {

        visited.add(pom);

        var ga = toGA(pom);
        for (var dpd : data.findPotentialDependents(ga, config.resolveAt)) {

            if (visited.contains(dpd)) {
                continue;
            }

            // find correct dependency declaration
            var decl = find(ga, dpd.dependencies);

            if (!matchesScope(decl.getScope(), config.scope, config.alwaysIncludeProvided)) {
                continue;
            }

            if (isTransitiveDep && decl.getScope() == Scope.PROVIDED) {
                continue;
            }

            // check whether version of pom matches the dependency declaration
            if (doesVersionMatch(decl, pom)) {
                dependents.add(toRR(dpd, Scope.COMPILE));

                if (config.depth == TRANSITIVE && shouldProcessDependent(config, decl)) {
                    resolve(dpd, config, dependents, visited, true);
                }
            }
        }
    }

    private static ResolvedRevision toRR(Pom p, Scope s) {
        return new ResolvedRevision(p.toRevision(), s);
    }

    private static boolean shouldProcessDependent(ResolverConfig config, Dependency decl) {
        var isNonProvided = decl.getScope() != Scope.PROVIDED || config.alwaysIncludeProvided;
        var isNonOptional = !decl.isOptional() || config.alwaysIncludeOptional;
        return isNonProvided && isNonOptional;
    }

    private boolean matchesScope(Scope dep, Scope request, boolean alwaysIncludeProvided) {

        if (dep == Scope.PROVIDED && alwaysIncludeProvided) {
            return true;
        }

        if (request == Scope.COMPILE) {
            return dep == COMPILE || dep == SYSTEM || dep == Scope.PROVIDED;
        }

        if (request == Scope.RUNTIME) {
            return dep == RUNTIME || dep == COMPILE || dep == SYSTEM;
        } else {
            return dep == TEST || dep == RUNTIME || dep == COMPILE || dep == SYSTEM || dep == Scope.PROVIDED;
        }
    }

    private boolean doesVersionMatch(Dependency dep, Pom pom) {
        for (var vc : dep.getVersionConstraints()) {
            if (vc.matches(pom.version)) {
                return true;
            }
        }
        return false;
    }

    private static Dependency find(String ga, Set<Dependency> dependencies) {
        for (var dep : dependencies) {
            if (ga.equals(toGA(dep))) {
                return dep;
            }
        }
        throw new IllegalStateException("Cannot find reported dependency");
    }

    private static String toGA(Dependency dep) {
        return String.format("%s:%s", dep.getGroupId(), dep.getArtifactId());
    }

    private static String toGA(Pom pom) {
        return String.format("%s:%s", pom.groupId, pom.artifactId);
    }
}