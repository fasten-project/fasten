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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.ToStringBuilder;

import eu.fasten.core.data.Constants;

public class Pom {

    private static final LinkedHashSet<Dependency> NO_DEPS = new LinkedHashSet<>() {
        private static final long serialVersionUID = -7233644259488131119L;

        // will always be empty, so preventing "add" variants is all that is necessary

        public boolean add(Dependency e) {
            throw new UnsupportedOperationException();
        };

        public boolean addAll(Collection<? extends Dependency> c) {
            throw new UnsupportedOperationException();
        };
    };
    private static final Set<Dependency> NO_DEPMGMT = Set.of();

    public transient long id;

    public final String forge = Constants.mvnForge;

    public final String artifactId;
    public final String groupId;
    public final String packagingType;
    public final String version;

    // g:a:packaging:version
    public final String parentCoordinate;

    public final long releaseDate;
    public final String projectName;

    public final Set<Dependency> dependencies;
    public final Set<Dependency> dependencyManagement;

    public final String repoUrl;
    public final String commitTag;
    public final String sourcesUrl;
    public final String artifactRepository;

    private final int hashCode;
    private final GAV gav;
    private final GA ga;

    // use LinkedHashSet for dependencies, because order is relevant for resolution
    public Pom(String groupId, String artifactId, String packagingType, String version, String parentCoordinate,
            long releaseDate, String projectName, LinkedHashSet<Dependency> dependencies,
            Set<Dependency> dependencyManagement, String repoUrl, String commitTag, String sourcesUrl,
            String artifactRepository) {
        this.groupId = Ids.gid(groupId);
        this.artifactId = Ids.gid(artifactId);
        this.packagingType = packagingType;
        this.version = Ids.version(version);

        this.parentCoordinate = parentCoordinate;

        this.releaseDate = releaseDate;
        this.projectName = projectName;

        if (dependencies == null || dependencies.isEmpty()) {
            this.dependencies = NO_DEPS;
        } else {
            // TODO check for "double wrapping"
            this.dependencies = Collections.unmodifiableSet(dependencies);
        }

        if (dependencyManagement == null || dependencyManagement.isEmpty()) {
            this.dependencyManagement = NO_DEPMGMT;
        } else {
            // TODO check for "double wrapping"
            this.dependencyManagement = Collections.unmodifiableSet(dependencyManagement);
        }

        this.repoUrl = repoUrl;
        this.commitTag = commitTag;
        this.sourcesUrl = sourcesUrl;
        this.artifactRepository = artifactRepository;

        hashCode = getHashCode();

        gav = Ids.gav(new GAV(groupId, artifactId, version));
        ga = Ids.ga(new GA(groupId, artifactId));
    }

    /** gid:aid:packaging:version */
    public String toCoordinate() {
        return new StringBuilder().append(groupId).append(':').append(artifactId).append(':').append(packagingType)
                .append(':').append(version).toString();
    }

    public GAV toGAV() {
        return gav;
    }

    public GA toGA() {
        return ga;
    }

    public MavenProduct toProduct() {
        return new MavenProduct(id, groupId, artifactId);
    }

    public Revision toRevision() {
        return new Revision(id, groupId, artifactId, version, new Timestamp(releaseDate));
    }

    public PomBuilder clone() {
        var clone = new PomBuilder();

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

    public int getHashCode() {
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
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Pom other = (Pom) obj;
        return hashCode == other.hashCode;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, MULTI_LINE_STYLE);
    }
}