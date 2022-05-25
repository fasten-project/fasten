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
import java.util.Set;

import javax.inject.Inject;

import eu.fasten.core.maven.data.ResolvedRevision;

public class MavenResolver implements IMavenResolver {

    private final MavenDependentsResolver dependentsResolver;
    private final MavenDependencyResolver dependencyResolver;

    @Inject
    public MavenResolver(MavenDependentsResolver dependentsResolver, MavenDependencyResolver dependencyResolver) {
        this.dependentsResolver = dependentsResolver;
        this.dependencyResolver = dependencyResolver;
    }

    @Override
    public Set<ResolvedRevision> resolveDependencies(Collection<String> gavs, ResolverConfig config) {
        return dependencyResolver.resolve(gavs, config);
    }

    @Override
    public Set<ResolvedRevision> resolveDependents(String gid, String aid, String version, ResolverConfig config) {
        var gav = String.format("%s:%s:%s", gid, aid, version);
        return dependentsResolver.resolve(gav, config);
    }
}