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

package eu.fasten.core.maven.data;

import eu.fasten.core.data.Constants;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.lang.management.ManagementFactory;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * An artifact released in Maven Central.
 */
public class Revision extends MavenProduct {

    public final DefaultArtifactVersion version;
    public final Timestamp createdAt;

    public Revision(final String groupId, final String artifactId, final String version, final Timestamp createdAt){
        super(groupId, artifactId);

        this.version = new DefaultArtifactVersion(version);
        this.createdAt = createdAt;
    }

    public MavenProduct product() {
        return new MavenProduct(this.groupId, this.artifactId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Revision revision = (Revision) o;
        return version.equals(revision.version) && createdAt.equals(revision.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.artifactId, this.groupId, version, createdAt);
    }

    @Override
    public String toString() {
        return String.format("%s%s%s%s", groupId, Constants.mvnCoordinateSeparator,
                artifactId, Constants.mvnCoordinateSeparator, version);
    }
}
