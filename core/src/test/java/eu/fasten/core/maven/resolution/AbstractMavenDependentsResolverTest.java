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
package eu.fasten.core.maven.resolution;

import static org.junit.Assert.fail;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;

import eu.fasten.core.maven.data.ResolvedRevision;
import eu.fasten.core.maven.data.Scope;

public class AbstractMavenDependentsResolverTest {

    protected static final String DEST = "dest:1";

    protected MavenDependentsData data;
    protected MavenDependentsResolver sut;

    protected ResolverConfig config;

    @BeforeEach
    public void setupAbstract() {
        data = new MavenDependentsData();
        sut = new MavenDependentsResolver();
        sut.setData(data);
        config = new ResolverConfig();
    }

    protected void assertDependents(String shortTarget, String... depSet) {
        var targetParts = shortTarget.split(":");
        var target = String.format("%s:%s:%s", targetParts[0], targetParts[0], targetParts[1]);
        var actuals = sut.resolve(target, config);
        var expecteds = Arrays.stream(depSet) //
                .map(gav -> gav.split(":")) //
                .map(parts -> new ResolvedRevision(-1, parts[0], parts[0], parts[1], new Timestamp(-1L), Scope.COMPILE)) //
                .collect(Collectors.toSet());

        if (!expecteds.equals(actuals)) {
            var sb = new StringBuilder();
            sb.append("Expected:\n");
            for (var e : expecteds) {
                sb.append("- ").append(e.getGroupId()).append(":").append(e.getArtifactId()).append(":")
                        .append(e.version).append("\n");
            }
            sb.append("But was:\n");
            for (var a : actuals) {
                sb.append("- ").append(a.getGroupId()).append(":").append(a.getArtifactId()).append(":")
                        .append(a.version).append("\n");
            }
            fail(sb.toString());
        }
    }
}