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

public class GA {

    public final String groupId;
    public final String artifactId;

    private final int hashCode;

    public GA(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.hashCode = getHashCode();
    }

    private int getHashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
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
        GA other = (GA) obj;
        return hashCode == other.hashCode;
    }

    @Override
    public String toString() {
        return new StringBuilder().append(groupId).append(':').append(artifactId).toString();
    }
}