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
import java.util.stream.Collectors;

import eu.fasten.core.maven.data.ResolvedRevision;
import eu.fasten.core.maven.data.Revision;

public interface IMavenResolver {

    Set<ResolvedRevision> resolveDependencies(Collection<String> gavs, ResolverConfig config);

    Set<ResolvedRevision> resolveDependents(String gid, String aid, String version, ResolverConfig config);

    // map one to many

    default Set<ResolvedRevision> resolveDependencies(String gid, String aid, String version, ResolverConfig config) {
        var gav = String.format("%s:%s:%s", gid, aid, version);
        return resolveDependencies(Set.of(gav), config);
    }

    // no config

    default Set<ResolvedRevision> resolveDependencies(Collection<String> gavs) {
        return resolveDependencies(gavs, new ResolverConfig());
    }

    default Set<ResolvedRevision> resolveDependencies(String gid, String aid, String version) {
        return resolveDependencies(gid, aid, version, new ResolverConfig());
    }

    default Set<ResolvedRevision> resolveDependents(String gid, String aid, String version) {
        return resolveDependents(gid, aid, version, new ResolverConfig());
    }

    // revision

    default Set<ResolvedRevision> resolveDependenciesForRevisions(Collection<Revision> revisions, ResolverConfig config) {
        var gavs = revisions.stream() //
                .map(r -> String.format("%s:%s:%s", r.groupId, r.artifactId, r.version)) //
                .collect(Collectors.toSet());
        return resolveDependencies(gavs, config);
    }

    default Set<ResolvedRevision> resolveDependencies(Revision r, ResolverConfig config) {
        return resolveDependencies(r.groupId, r.artifactId, r.version.toString(), config);
    }

    default Set<ResolvedRevision> resolveDependents(Revision r, ResolverConfig config) {
        return resolveDependents(r.groupId, r.artifactId, r.version.toString(), config);
    }

    // revision and no config

    default Set<ResolvedRevision> resolveDependencies(Revision r) {
        return resolveDependencies(r.groupId, r.artifactId, r.version.toString(), new ResolverConfig());
    }

    default Set<ResolvedRevision> resolveDependenciesForRevisions(Collection<Revision> revisions) {
        return resolveDependenciesForRevisions(revisions, new ResolverConfig());
    }

    default Set<ResolvedRevision> resolveDependents(Revision r) {
        return resolveDependents(r, new ResolverConfig());
    }
}