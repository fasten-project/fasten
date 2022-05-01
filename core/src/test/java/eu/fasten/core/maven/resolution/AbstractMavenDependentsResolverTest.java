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

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;

import eu.fasten.core.maven.data.ResolvedRevision;

public class AbstractMavenDependentsResolverTest {

    protected static final String DEST = "dest:1";

    protected MavenResolverData data;
    protected MavenDependentsResolver sut;

    protected ResolverConfig config;

    @BeforeEach
    public void setupAbstract() {
        data = new MavenResolverData();
        sut = new MavenDependentsResolver();
        sut.setData(data);
        config = new ResolverConfig();
    }

    protected Set<ResolvedRevision> assertResolution(String shortTarget, String... depSet) {
        addDangling();
        var targetParts = shortTarget.split(":");
        var target = String.format("%s:%s:%s", targetParts[0], targetParts[0], targetParts[1]);
        var actualsRaw = sut.resolve(target, config);

        var actuals = actualsRaw.stream() //
                .map(rr -> String.format("%s:%s:%s", rr.getGroupId(), rr.getArtifactId(), rr.version)) //
                .collect(Collectors.toCollection(TreeSet::new));
        var expecteds = Arrays.stream(depSet) //
                .map(gav -> gav.split(":")) //
                .map(parts -> String.format("%s:%s:%s", parts[0], parts[0], parts[1])) //
                .collect(Collectors.toSet());

        assertEquals(expecteds, actuals);
        return actualsRaw;
    }

    private void assertEquals(Set<String> expecteds, TreeSet<String> actuals) {
        if (!expecteds.equals(actuals)) {
            var sb = new StringBuilder();

            sb.append("Resolution has returned an unexpected set of dependencies:\n\n");
            sb.append("Config: ").append(config).append("\n\n");

            if (expecteds.isEmpty()) {
                sb.append("Expected empty resolution result, ");
            } else {
                sb.append("Expected:\n");
                for (var e : expecteds) {
                    sb.append("- ").append(e).append("\n");
                }
            }

            if (actuals.isEmpty()) {
                sb.append("But resolution result was empty");
            } else {
                sb.append("But was:\n");
                for (var a : actuals) {
                    sb.append("- ").append(a).append("\n");
                }
            }
            fail(sb.toString());
        }
    }

    protected void addDangling() {
        // override if necessary
    }
}