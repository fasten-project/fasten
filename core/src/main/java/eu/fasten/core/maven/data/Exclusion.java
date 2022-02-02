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

	public String artifactId;
	public String groupId;

    @SuppressWarnings("unused")
    private Exclusion() {
        // exists for JSON object mappers
    }

	public Exclusion(final String groupId, final String artifactId) {
		this.groupId = groupId;
		this.artifactId = artifactId;
	}

	public String toJSON() {
		return String.format("%s:%s", groupId, artifactId);
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
		return toJSON();
	}

	public static Exclusion fromJSON(String json) {
		String[] parts = json.split(":");
		return new Exclusion(parts[0], parts[1]);
	}
}