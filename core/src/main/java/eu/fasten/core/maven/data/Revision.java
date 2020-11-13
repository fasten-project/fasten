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

import com.github.zafarkhaja.semver.Version;
import eu.fasten.core.maven.DependencyGraphBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An artifact released in Maven Central.
 */
public class Revision extends MavenProduct {
    // From here: https://ihateregex.io/expr/semver/
    public static final Pattern semverMatcher = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");
    public final Version version;
    public final Timestamp createdAt;
    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    public Revision(final String groupId, final String artifactId, final String version, final Timestamp createdAt){
        super(groupId, artifactId);

        this.version = Version.valueOf(version);
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Revision revision = (Revision) o;
        return version.equals(revision.version) &&
                createdAt.equals(revision.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.artifactId, this.groupId, version, createdAt);
    }
}
