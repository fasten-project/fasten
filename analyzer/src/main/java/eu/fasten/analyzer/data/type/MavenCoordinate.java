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


package eu.fasten.analyzer.data.type;

import java.io.Serializable;
import java.util.Objects;


public class MavenCoordinate implements Serializable, Namespace {

    public final String artifactId;
    public final String groupId;
    public final String version;


    public MavenCoordinate(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;

    }

    public static MavenCoordinate of(String canonicalform) {
        String[] segments = canonicalform.split(":");
        assert segments.length == 3;
        return new MavenCoordinate(segments[0], segments[1], segments[2]);

    }

    public String getCanonicalForm() {
        return String.join(this.getNamespaceDelim(),
                this.groupId,
                this.artifactId,
                this.version);
    }

    @Override
    public String toString() {
        return "MavenCoordinate(" + this.groupId + ","
                + this.artifactId + ","
                + this.version + ")";
    }

    @Override
    public boolean equals(Object o) {
        // self check
        if (this == o)
            return true;
        // null check
        if (o == null)
            return false;
        // type check and cast
        if (getClass() != o.getClass())
            return false;
        MavenCoordinate coord = (MavenCoordinate) o;
        return
                Objects.equals(this.groupId, coord.groupId) &&
                        Objects.equals(this.artifactId, coord.artifactId) &&
                        Objects.equals(this.version, coord.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.artifactId, this.version);
    }

    @Override
    public String[] getSegments() {
        return new String[]{this.groupId, this.artifactId, this.version};
    }

    @Override
    public String getNamespaceDelim() { return ":"; }
}
