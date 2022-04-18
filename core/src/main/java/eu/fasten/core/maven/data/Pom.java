/*
 * Copyright 2021 Delft University of Technology
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

import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import eu.fasten.core.data.Constants;

public class Pom implements Cloneable {

    public transient long id;

    public String forge = Constants.mvnForge;

    public String artifactId = null;
    public String groupId = null;
    public String packagingType = null;
    public String version = null;

    // g:a:packaging:version
    public String parentCoordinate = null;

    public long releaseDate = -1L;
    public String projectName = null;

    // used LinkedHashSet, because order is relevant for resolution
    public final LinkedHashSet<Dependency> dependencies = new LinkedHashSet<>();
    public final Set<Dependency> dependencyManagement = new HashSet<>();

    public String repoUrl = null;
    public String commitTag = null;
    public String sourcesUrl = null;
    public String artifactRepository = null;

    /** gid:aid:packaging:version */
    public String toCoordinate() {
        return new StringBuilder().append(groupId).append(':').append(artifactId).append(':').append(packagingType)
                .append(':').append(version).toString();
    }

    public String toGAV() {
        return new StringBuilder().append(groupId).append(':').append(artifactId).append(':').append(version)
                .toString();
    }

    public String toGA() {
        return new StringBuilder().append(groupId).append(':').append(artifactId).toString();
    }

    public MavenProduct toProduct() {
        return new MavenProduct(id, groupId, artifactId);
    }

    public Revision toRevision() {
        return new Revision(id, groupId, artifactId, version, new Timestamp(releaseDate));
    }

    @Override
    public Pom clone() {
        var clone = new Pom();
        clone.forge = forge;

        clone.artifactId = artifactId;
        clone.groupId = groupId;
        clone.packagingType = packagingType;
        clone.version = version;

        clone.parentCoordinate = parentCoordinate;

        clone.releaseDate = releaseDate;
        clone.projectName = projectName;

        clone.dependencies.addAll(dependencies);
        clone.dependencyManagement.addAll(dependencyManagement);

        clone.repoUrl = repoUrl;
        clone.commitTag = commitTag;
        clone.sourcesUrl = sourcesUrl;
        clone.artifactRepository = artifactRepository;

        return clone;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((artifactRepository == null) ? 0 : artifactRepository.hashCode());
        result = prime * result + ((commitTag == null) ? 0 : commitTag.hashCode());
        result = prime * result + ((dependencies == null) ? 0 : dependencies.hashCode());
        result = prime * result + ((dependencyManagement == null) ? 0 : dependencyManagement.hashCode());
        result = prime * result + ((forge == null) ? 0 : forge.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
        result = prime * result + ((packagingType == null) ? 0 : packagingType.hashCode());
        result = prime * result + ((parentCoordinate == null) ? 0 : parentCoordinate.hashCode());
        result = prime * result + ((projectName == null) ? 0 : projectName.hashCode());
        result = prime * result + (int) (releaseDate ^ (releaseDate >>> 32));
        result = prime * result + ((repoUrl == null) ? 0 : repoUrl.hashCode());
        result = prime * result + ((sourcesUrl == null) ? 0 : sourcesUrl.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, MULTI_LINE_STYLE);
    }
}