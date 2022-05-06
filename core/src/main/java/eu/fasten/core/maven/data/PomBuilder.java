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
package eu.fasten.core.maven.data;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class PomBuilder {

    public String groupId = null;
    public String artifactId = null;
    public String packagingType = null;
    public String version = null;

    // g:a:packaging:version
    public String parentCoordinate = null;

    public long releaseDate = -1L;
    public String projectName = null;

    // used LinkedHashSet, because order is relevant for resolution
    public LinkedHashSet<Dependency> dependencies = new LinkedHashSet<>();
    public Set<Dependency> dependencyManagement = new HashSet<>();

    public String repoUrl = null;
    public String commitTag = null;
    public String sourcesUrl = null;
    public String artifactRepository = null;

    public Pom pom() {
        return new Pom(groupId, artifactId, packagingType, version, parentCoordinate, releaseDate, projectName,
                dependencies, dependencyManagement, repoUrl, commitTag, sourcesUrl, artifactRepository);
    }

    public PomBuilder groupId(String gid) {
        this.groupId = gid;
        return this;
    }

    public PomBuilder artifactId(String aid) {
        this.artifactId = aid;
        return this;
    }

    public PomBuilder packagingType(String t) {
        this.packagingType = t;
        return this;
    }

    public PomBuilder version(String v) {
        this.version = v;
        return this;
    }

    public PomBuilder parentCoordinate(String string) {
        this.parentCoordinate = string;
        return this;
    }

    public PomBuilder releaseDate(long string) {
        this.releaseDate = string;
        return this;
    }

    public PomBuilder projectName(String string) {
        this.projectName = string;
        return this;
    }

    public PomBuilder addDependency(Dependency d) {
        this.dependencies.add(d);
        return this;
    }

    public PomBuilder setDependencies(LinkedHashSet<Dependency> dependencies) {
        this.dependencies = dependencies;
        return this;
    }

    public PomBuilder addDependencyManagement(Dependency d) {
        this.dependencyManagement.add(d);
        return this;
    }

    public PomBuilder setDependencyManagement(Set<Dependency> dependencies) {
        this.dependencyManagement = dependencies;
        return this;
    }

    public PomBuilder repoUrl(String string) {
        this.repoUrl = string;
        return this;
    }

    public PomBuilder commitTag(String string) {
        this.commitTag = string;
        return this;
    }

    public PomBuilder sourcesUrl(String string) {
        this.sourcesUrl = string;
        return this;
    }

    public PomBuilder artifactRepository(String string) {
        this.artifactRepository = string;
        return this;
    }
}