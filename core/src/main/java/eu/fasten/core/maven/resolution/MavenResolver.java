/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import eu.fasten.core.maven.data.Revision;

public class MavenResolver implements IMavenResolver {

    private final MavenDependentsResolver dependentsResolver;
    private final MavenDependencyResolver dependencyResolver;

    @Inject
    public MavenResolver(MavenDependentsResolver dependentsResolver, MavenDependencyResolver dependencyResolver) {
        this.dependentsResolver = dependentsResolver;
        this.dependencyResolver = dependencyResolver;
    }

    @Override
    public Set<Revision> resolveDependencies(Collection<String> gavs, ResolverConfig config) {
        return dependencyResolver.resolve(gavs, config);
    }

    @Override
    public Set<Revision> resolveDependents(String gid, String aid, String version, ResolverConfig config) {
        var revision = new Revision(gid, aid, version, null);
        return dependentsResolver.resolve(revision, config);
    }
}