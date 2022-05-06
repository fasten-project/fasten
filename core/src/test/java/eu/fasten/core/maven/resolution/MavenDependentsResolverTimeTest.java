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

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.PomBuilder;

public class MavenDependentsResolverTimeTest extends AbstractMavenDependentsResolverTest {

    private static final long SOME_TIME = 1234;

    @Test
    public void destDoesNotExistYet() {
        add(0, DEST);
        config.resolveAt = SOME_TIME - 1;
        var e = assertThrows(MavenResolutionException.class, () -> {
            sut.resolve("dest:dest:1", config);
        });
        assertEquals("Cannot find coordinate dest:dest:1", e.getMessage());
    }

    @Test
    public void direct() {
        add(0, DEST);
        add(0, "a:1", DEST);
        assertDependentsAt(0, DEST, "a:1");
    }

    @Test
    public void directTooNew() {
        add(0, DEST);
        add(1, "a:1", DEST);
        assertDependentsAt(0, DEST);
    }

    @Test
    public void directTooNewWithTransitive() {
        add(0, DEST);
        add(1, "a:1", DEST);
        add(0, "b:1", "a:1");
        assertDependentsAt(0, DEST);
    }

    @Test
    public void transitive() {
        add(0, DEST);
        add(0, "a:1", DEST);
        add(0, "b:1", "a:1");
        assertDependentsAt(0, DEST, "a:1", "b:1");
    }

    @Test
    public void transitiveToNew() {
        add(0, DEST);
        add(0, "a:1", DEST);
        add(1, "b:1", "a:1");
        assertDependentsAt(0, DEST, "a:1");
    }

    private void add(long releasedAtDelta, String from, String... tos) {
        var pb = new PomBuilder();
        var parts = from.split(":");
        pb.groupId = parts[0];
        pb.artifactId = parts[0];
        pb.version = parts[1];
        pb.releaseDate = SOME_TIME + releasedAtDelta;

        for (var to : tos) {
            var partsTo = to.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], partsTo[1]);

            pb.dependencies.add(d);
        }

        data.add(pb.pom());
    }

    private void assertDependentsAt(long resolveAtDelta, String shortTarget, String... depSet) {
        config.resolveAt = SOME_TIME + resolveAtDelta;
        assertResolution(shortTarget, depSet);
    }
}