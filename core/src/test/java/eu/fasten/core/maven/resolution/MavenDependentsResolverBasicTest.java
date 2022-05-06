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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.PomBuilder;

public class MavenDependentsResolverBasicTest extends AbstractMavenDependentsResolverTest {

    @BeforeEach
    public void setup() {
        add(DEST);
    }

    @Test
    public void direct() {
        add("a:1", DEST);
        assertResolution(DEST, "a:1");
    }

    @Test
    public void transitive() {
        add("a:1", DEST);
        add("b:1", "a:1");
        assertResolution(DEST, "a:1", "b:1");
    }

    @Test
    public void multipleBranches() {
        add("a:1", DEST);
        add("b:1", DEST);
        assertResolution(DEST, "a:1", "b:1");
    }

    @Test
    public void multipleBranchesTransitive() {
        add("a:1", DEST);
        add("aa:1", "a:1");
        add("b:1", DEST);
        add("bb:1", "b:1");
        assertResolution(DEST, "a:1", "aa:1", "b:1", "bb:1");
    }

    @Test
    public void cycleDirect() {
        add("a:1", DEST);
        add(DEST, "a:1");
        assertResolution(DEST, "a:1");
    }

    @Test
    public void cycleTransitive() {
        add("a:1", DEST);
        add("b:1", "a:1");
        add(DEST, "b:1");
        assertResolution(DEST, "a:1", "b:1");
    }

    @Test
    public void throwsWhenDestCannotBeFound() {
        var e = assertThrows(MavenResolutionException.class, () -> {
            sut.resolve("non:existing:1", config);
        });
        assertEquals("Cannot find coordinate non:existing:1", e.getMessage());
    }

    @Test
    public void illegalStateDepNotFound() {
        add("a:1", DEST);
        add("b:1", "a:1");
        add(DEST, "b:1");
        assertResolution(DEST, "a:1", "b:1");
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