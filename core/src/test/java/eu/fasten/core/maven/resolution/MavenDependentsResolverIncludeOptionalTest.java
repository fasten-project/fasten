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

import static eu.fasten.core.maven.data.VersionConstraint.parseVersionSpec;
import static org.junit.Assert.assertFalse;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.PomBuilder;
import eu.fasten.core.maven.data.Scope;

public class MavenDependentsResolverIncludeOptionalTest extends AbstractMavenDependentsResolverTest {

    @BeforeEach
    public void setup() {
        add(DEST);
    }

    @Test
    public void disabledByDefault() {
        assertFalse(config.alwaysIncludeOptional);
    }

    @Test
    public void ifUnsetIncludeDirectOptionalDeps() {
        add("a:1", opt(DEST));
        assertResolution(DEST, "a:1");
    }

    @Test
    public void ifUnsetDoNotIncludeTransitiveOptionalDeps() {
        add("a:1", m("b:1"));
        add("b:1", opt(DEST));
        assertResolution(DEST, "b:1");
    }

    @Test
    public void whenSetIncludeDirectOptionalDeps() {
        config.alwaysIncludeOptional = true;
        add("a:1", opt(DEST));
        assertResolution(DEST, "a:1");
    }

    @Test
    public void whenSetIncludeTransitiveOptionalDeps() {
        config.alwaysIncludeOptional = true;
        add("a:1", m("b:1"));
        add("b:1", opt(DEST));
        assertResolution(DEST, "a:1", "b:1");
    }

    private void add(String from, Dep... tos) {
        var pb = new PomBuilder();
        var parts = from.split(":");
        pb.groupId = parts[0];
        pb.artifactId = parts[0];
        pb.version = parts[1];

        for (var to : tos) {
            var partsTo = to.coord.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], parseVersionSpec(partsTo[1]), Set.of(), Scope.COMPILE,
                    to.isOptional, "jar", "");
            pb.dependencies.add(d);
        }

        data.add(pb.pom());
    }

    private static Dep m(String coord) {
        var dep = new Dep();
        dep.coord = coord;
        dep.isOptional = false;
        return dep;
    }

    private static Dep opt(String coord) {
        var dep = new Dep();
        dep.coord = coord;
        dep.isOptional = true;
        return dep;
    }

    private static class Dep {
        public String coord;
        public boolean isOptional;
    }
}