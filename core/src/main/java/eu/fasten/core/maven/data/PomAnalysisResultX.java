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

import static java.lang.String.format;
import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * DO NOT EDIT, THIS IS A TEMPORARY CLONE FROM THE OTHER REPO
 */
public class PomAnalysisResultX implements Cloneable {

    public transient long id;

    public String artifactId = null;
    public String groupId = null;
    public String packagingType = null;
    public String version = null;

    public long releaseDate = -1L;
    public String projectName = null;

    // used LinkedHashSet, because order is relevant for resolution
    public final LinkedHashSet<Dependency> dependencies = new LinkedHashSet<>();
    public final Set<Dependency> dependencyManagement = new HashSet<>();

    /** gid:aid:packaging:version */
    public String toCoordinate() {
        return format("%s:%s:%s:%s", groupId, artifactId, packagingType, version);
    }

    public MavenProduct toProduct() {
        return new MavenProduct(id, groupId, artifactId);
    }

    public Revision toRevision() {
        return new Revision(id, groupId, artifactId, version, new Timestamp(releaseDate));
    }

    @Override
    public PomAnalysisResultX clone() {
        var clone = new PomAnalysisResultX();

        clone.id = id;

        clone.artifactId = artifactId;
        clone.groupId = groupId;
        clone.packagingType = packagingType;
        clone.version = version;

        clone.releaseDate = releaseDate;
        clone.projectName = projectName;

        clone.dependencies.addAll(dependencies);
        clone.dependencyManagement.addAll(dependencyManagement);

        return clone;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, MULTI_LINE_STYLE);
    }
}