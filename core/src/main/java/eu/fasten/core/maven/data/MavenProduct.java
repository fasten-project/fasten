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

import java.util.Objects;

/**
 * A versionless Maven artifact
 */
public class MavenProduct {

    public long id;
    public String groupId;
    public String artifactId;

    public MavenProduct(){}

    public MavenProduct(final String groupId, final String artifactId) {
        this.id = 0;
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public MavenProduct(long id, String groupId, String artifactId) {
        this.id = id;
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenProduct that = (MavenProduct) o;
        return groupId.equals(that.groupId) &&
                artifactId.equals(that.artifactId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId);
    }

    @Override
    public String toString() {
        return String.format("%s%s%s", groupId, Constants.mvnCoordinateSeparator, artifactId);
    }
}
