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

import static eu.fasten.core.maven.data.Scope.COMPILE;
import static eu.fasten.core.maven.data.Scope.IMPORT;
import static eu.fasten.core.maven.data.Scope.PROVIDED;
import static eu.fasten.core.maven.data.Scope.RUNTIME;
import static eu.fasten.core.maven.data.Scope.SYSTEM;
import static eu.fasten.core.maven.data.Scope.TEST;
import static eu.fasten.core.maven.data.VersionConstraint.parseVersionSpec;
import static eu.fasten.core.maven.resolution.ResolverConfig.resolve;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.PomBuilder;
import eu.fasten.core.maven.data.Scope;

public class MavenDependentsResolverScopesTest extends AbstractMavenDependentsResolverTest {

    private static final String SOME_COORD = "some:coord:1.2.3";

    @BeforeEach
    public void setup() {
        add(DEST);
    }

    @Test
    public void cannotResolveImport() {
        var e = assertThrows(IllegalArgumentException.class, () -> {
            sut.resolve(SOME_COORD, resolve().scope(IMPORT));
        });
        assertEquals("Invalid resolution scope: IMPORT", e.getMessage());
    }

    @Test
    public void cannotResolveSystem() {
        var e = assertThrows(IllegalArgumentException.class, () -> {
            sut.resolve(SOME_COORD, resolve().scope(SYSTEM));
        });
        assertEquals("Invalid resolution scope: SYSTEM", e.getMessage());
    }

    @Test
    public void cannotResolveProvided() {
        var e = assertThrows(IllegalArgumentException.class, () -> {
            sut.resolve(SOME_COORD, resolve().scope(PROVIDED));
        });
        assertEquals("Invalid resolution scope: PROVIDED", e.getMessage());
    }

    @Test
    public void resolveCompileDirectDependents() {
        addDependentsWithAllScopes(DEST);
        config.scope = COMPILE;
        assertResolution(DEST, "c:1", "s:1", "p:1");
    }

    @Test
    public void resolveRuntimeDirectDependents() {
        addDependentsWithAllScopes(DEST);
        config.scope = RUNTIME;
        assertResolution(DEST, "c:1", "r:1");
    }

    @Test
    public void resolveTestDirectDependents() {
        addDependentsWithAllScopes(DEST);
        config.scope = TEST;
        assertResolution(DEST, "c:1", "r:1", "t:1", "s:1", "p:1");
    }

    @Test
    public void resolveCompileTransitiveDependents() {
        add("x:1", $(DEST, COMPILE));
        addDependentsWithAllScopes("x:1");
        config.scope = COMPILE;
        assertResolution(DEST, "x:1", "c:1", "s:1", "p:1");
    }

    @Test
    public void resolveRuntimeTransitiveDependents() {
        add("x:1", $(DEST, COMPILE));
        addDependentsWithAllScopes("x:1");
        config.scope = RUNTIME;
        assertResolution(DEST, "x:1", "c:1", "r:1");
    }

    @Test
    public void resolveTestTransitiveDependents() {
        add("x:1", $(DEST, COMPILE));
        addDependentsWithAllScopes("x:1");
        config.scope = TEST;
        assertResolution(DEST, "x:1", "c:1", "r:1", "t:1", "s:1", "p:1");
    }

    @Test
    public void invalidDepForFullCoverage() {
        config.scope = TEST;
        add("x:1", $(DEST, IMPORT));
        assertResolution(DEST);
    }

    private void add(String from, Dep... tos) {
        var pb = new PomBuilder();
        var parts = from.split(":");
        pb.groupId = parts[0];
        pb.artifactId = parts[0];
        pb.version = parts[1];

        for (var to : tos) {
            var partsTo = to.coord.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], parseVersionSpec(partsTo[1]), Set.of(), to.scope, false,
                    "jar", "");
            pb.dependencies.add(d);
        }

        data.add(pb.pom());
    }

    private void addDependentsWithAllScopes(String dest) {
        add("c:1", $(dest, COMPILE));
        add("r:1", $(dest, RUNTIME));
        add("t:1", $(dest, TEST));
        add("s:1", $(dest, SYSTEM));
        add("p:1", $(dest, PROVIDED));
    }

    private static Dep $(String coord, Scope scope) {
        var dep = new Dep();
        dep.coord = coord;
        dep.scope = scope;
        return dep;
    }

    private static class Dep {
        public String coord;
        public Scope scope;
    }
}