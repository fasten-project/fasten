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

import java.util.HashSet;
import java.util.Set;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.GAV;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.ResolvedRevision;
import eu.fasten.core.maven.data.Scope;

public class MavenDependentsResolver {

    private MavenResolverData data;

    public void setData(MavenResolverData data) {
        this.data = data;
    }

    public Set<ResolvedRevision> resolve(String gav, ResolverConfig config) {
        failForInvalidScopes(config);

        var parts = gav.split(":");
        var pom = data.findPom(new GAV(parts[0], parts[1], parts[2]), config.resolveAt);
        if (pom == null) {
            throw new MavenResolutionException(String.format("Cannot find coordinate %s", gav));
        }

        var dependents = new HashSet<ResolvedRevision>();
        resolve(pom, config, dependents, new HashSet<>(), false, COMPILE, 1);
        return dependents;
    }

    private static void failForInvalidScopes(ResolverConfig config) {
        if (config.scope == Scope.IMPORT || config.scope == Scope.PROVIDED || config.scope == Scope.SYSTEM) {
            var msg = "Invalid resolution scope: %s";
            throw new IllegalArgumentException(String.format(msg, config.scope));
        }
    }

    private void resolve(Pom pom, ResolverConfig config, Set<ResolvedRevision> dependents, Set<Object> visited,
            boolean stopAfterThis, Scope propagatedScope, int depth) {

        if (depth > config.depth) {
            return;
        }

        visited.add(pom);

        for (var dpd : data.findPotentialDependents(pom.toGA(), config.resolveAt)) {

            if (visited.contains(dpd)) {
                continue;
            }

            var decl = findCorrectDependencyDecl(pom, dpd.dependencies);

            if (!matchesScope(decl.getScope(), config.scope, config.alwaysIncludeProvided)) {
                continue;
            }

            if (doesPomVersionMatchDecl(decl, pom)) {
                propagatedScope = deriveScope(propagatedScope, decl.getScope());
                dependents.add(toRR(dpd, propagatedScope));

                if (config.depth > depth && !stopAfterThis && shouldProcessDependent(config, decl)) {
                    var onlyOneMore = decl.getScope() == PROVIDED;
                    resolve(dpd, config, dependents, visited, onlyOneMore, propagatedScope, depth + 1);
                }
            }
        }
    }

    private static Scope deriveScope(Scope prop, Scope dep) {
        if (dep == SYSTEM || prop == SYSTEM) {
            return SYSTEM;
        }
        if (dep == PROVIDED || prop == PROVIDED) {
            return PROVIDED;
        }
        if (dep == TEST) {
            return TEST;
        }
        if (dep == RUNTIME && prop == COMPILE) {
            return RUNTIME;
        }
        return prop;
    }

    private static ResolvedRevision toRR(Pom p, Scope s) {
        return new ResolvedRevision(p.toRevision(), s);
    }

    private static boolean shouldProcessDependent(ResolverConfig config, Dependency decl) {
        var isNonTest = decl.getScope() != TEST;
        var isNonProvided = decl.getScope() != PROVIDED || config.alwaysIncludeProvided;
        var isNonOptional = !decl.isOptional() || config.alwaysIncludeOptional;
        return isNonTest && isNonProvided && isNonOptional;
    }

    private static boolean matchesScope(Scope dep, Scope request, boolean alwaysIncludeProvided) {

        if (dep == PROVIDED && alwaysIncludeProvided) {
            return true;
        }
        if (request == Scope.COMPILE) {
            return dep == COMPILE || dep == SYSTEM || dep == PROVIDED;
        }
        if (request == RUNTIME) {
            return dep == RUNTIME || dep == COMPILE;
        }
        return dep == TEST || dep == RUNTIME || dep == COMPILE || dep == SYSTEM || dep == PROVIDED;
    }

    private static boolean doesPomVersionMatchDecl(Dependency dep, Pom pom) {
        for (var vc : dep.getVersionConstraints()) {
            if (vc.matches(pom.version)) {
                return true;
            }
        }
        return false;
    }

    private static Dependency findCorrectDependencyDecl(Pom pom, Set<Dependency> dependencies) {
        for (var dep : dependencies) {
            if (pom.groupId.equals(dep.groupId)) {
                if (pom.artifactId.equals(dep.artifactId)) {
                    return dep;
                }
            }
        }
        throw new IllegalStateException("Cannot find reported dependency");
    }
}