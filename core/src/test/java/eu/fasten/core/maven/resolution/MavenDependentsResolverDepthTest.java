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

import static eu.fasten.core.maven.resolution.ResolverDepth.DIRECT;
import static eu.fasten.core.maven.resolution.ResolverDepth.TRANSITIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.PomBuilder;

public class MavenDependentsResolverDepthTest extends AbstractMavenDependentsResolverTest {

    @Test
    public void defaultValue() {
        assertEquals(TRANSITIVE, config.depth);
    }

    @Test
    public void ifTransitiveIncludeTransitive() {
        addDepChain();
        assertResolution(DEST, "a:1", "b:1");
    }

    @Test
    public void ifDirectOnlyIncludeDirect() {
        addDepChain();
        config.depth = DIRECT;
        assertResolution(DEST, "b:1");
    }

    private void addDepChain() {
        add("a:1", "b:1");
        add("b:1", DEST);
        add(DEST, "c:1");
        add("c:1");
    }

    private void add(String from, String... tos) {
        var pb = new PomBuilder();
        var parts = from.split(":");
        pb.groupId = parts[0];
        pb.artifactId = parts[0];
        pb.version = parts[1];

        for (String to : tos) {
            var partsTo = to.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], partsTo[1]);
            pb.dependencies.add(d);
        }

        data.add(pb.pom());
    }
}