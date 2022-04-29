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
import static eu.fasten.core.maven.data.Scope.PROVIDED;
import static eu.fasten.core.maven.data.VersionConstraint.parseVersionSpec;
import static org.junit.Assert.assertFalse;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Scope;

public class MavenDependentsResolverIncludeProvidedTest extends AbstractMavenDependentsResolverTest {

    private static final Scope[] SCOPES = new Scope[] { Scope.COMPILE, Scope.RUNTIME, Scope.TEST };

    @BeforeEach
    public void setup() {
        add(DEST);
    }

    @Test
    public void disabledByDefault() {
        assertFalse(config.alwaysIncludeProvided);
    }

    @Test
    public void ifUnsetIncludeDirectProvidedDepsForCompile() {
        config.scope = Scope.COMPILE;
        add("a:1", $(DEST, PROVIDED));
        assertResolution(DEST, "a:1");
    }

    @Test
    public void ifUnsetDoNotIncludeDirectProvidedDepsForRuntime() {
        config.scope = Scope.RUNTIME;
        add("a:1", $(DEST, PROVIDED));
        assertResolution(DEST);
    }

    @Test
    public void ifUnsetIncludeDirectProvidedDepsForTest() {
        config.scope = Scope.TEST;
        add("a:1", $(DEST, PROVIDED));
        assertResolution(DEST, "a:1");
    }

    @Test
    public void ifUnsetDoNotIncludeTransitiveProvidedDepsForCompile() {
        config.scope = Scope.COMPILE;
        add("a:1", $("b:1", COMPILE));
        add("b:1", $(DEST, PROVIDED));
        assertResolution(DEST, "b:1");
    }

    @Test
    public void ifUnsetDoNotIncludeTransitiveProvidedDepsForRuntime() {
        config.scope = Scope.RUNTIME;
        add("a:1", $("b:1", COMPILE));
        add("b:1", $(DEST, PROVIDED));
        assertResolution(DEST);
    }

    @Test
    public void ifUnsetDoNotIncludeTransitiveProvidedDepsForTest() {
        config.scope = Scope.TEST;
        add("a:1", $("b:1", COMPILE));
        add("b:1", $(DEST, PROVIDED));
        assertResolution(DEST, "b:1");
    }

    @Test
    public void whenSetIncludeDirectProvidedDepsForAllScopes() {
        add("a:1", $(DEST, PROVIDED));

        for (var scope : SCOPES) {
            config.alwaysIncludeProvided = true;
            config.scope = scope;
            assertResolution(DEST, "a:1");
        }
    }

    @Test
    public void whenSetIncludeTransitiveProvidedDepsForAllScopes() {
        add("a:1", $("b:1", COMPILE));
        add("b:1", $(DEST, PROVIDED));

        for (var scope : SCOPES) {
            config.alwaysIncludeProvided = true;
            config.scope = scope;
            assertResolution(DEST, "a:1", "b:1");
        }
    }

    private void add(String from, Dep... tos) {
        var pom = new Pom();
        var parts = from.split(":");
        pom.groupId = parts[0];
        pom.artifactId = parts[0];
        pom.version = parts[1];

        for (var to : tos) {
            var partsTo = to.coord.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], parseVersionSpec(partsTo[1]), Set.of(), to.scope, false,
                    "jar", "");
            pom.dependencies.add(d);
        }

        data.add(pom);
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