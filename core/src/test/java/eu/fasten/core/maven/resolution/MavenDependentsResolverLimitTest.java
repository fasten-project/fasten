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

import static org.junit.Assert.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.PomBuilder;

public class MavenDependentsResolverLimitTest extends AbstractMavenDependentsResolverTest {

    @Test
    public void defaultValue() {
        assertTrue(config.limit == Integer.MAX_VALUE);
    }

    @BeforeEach
    private void setup() {

        add("c:1", "b:1");
        add("b:1", "a:1");
        add("a:1", DEST);

        add("c:2", "b:2");
        add("b:2", "a:2");
        add("a:2", DEST);

        add(DEST, "x:1");
        add("x:1");
    }

    @Test
    public void all() {
        assertResolution(DEST, "a:1", "b:1", "c:1", "a:2", "b:2", "c:2");
    }

    @Test
    public void onlyOne() {
        config.limit(1);
        assertResolution(DEST, "a:1");
    }

    @Test
    public void onlyTwo() {
        config.limit(2);
        assertResolution(DEST, "a:1", "b:1");
    }

    @Test
    public void onlyThree() {
        config.limit(3);
        assertResolution(DEST, "a:1", "b:1", "c:1");
    }

    @Test
    public void onlyFour() {
        config.limit(4);
        assertResolution(DEST, "a:1", "b:1", "c:1", "a:2");
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