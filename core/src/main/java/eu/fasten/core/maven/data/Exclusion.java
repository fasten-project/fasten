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

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Exclusion implements Serializable {

    private static final long serialVersionUID = -1350444195222504726L;

    private String artifactId;
    private String groupId;

    private int hashCode = 0;

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
        refreshHashCode();
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
        refreshHashCode();
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private void refreshHashCode() {
        hashCode = HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return String.format("%s:%s", groupId, artifactId);
    }

    public static Exclusion init(String groupId, String artifactId) {
        var e = new Exclusion();
        e.groupId = groupId;
        e.artifactId = artifactId;
        e.refreshHashCode();
        return e;
    }

    // TODO remove everything below

    @Deprecated
    public String toJSON() {
        return String.format("%s:%s", groupId, artifactId);
    }

    @Deprecated
    public static Exclusion fromJSON(String json) {
        String[] parts = json.split(":");
        return Exclusion.init(parts[0], parts[1]);
    }
}