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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Scope;

public class MavenDependencyResolverIncludeOptionalTest extends AbstractMavenDependencyResolverTest {

    @Test
    public void disabledByDefault() {
        assertFalse(config.alwaysIncludeOptional);
    }

    @Test
    public void byDefaultIncludeDirectDependency() {
        add(BASE, dep("a:1"), opt("b:1"));
        assertResolution(BASE, "a:1", "b:1");
    }

    @Test
    public void byDefaultDontIncludeTransitiveDependency() {
        add(BASE, dep("a:1"));
        add("a:1", dep("b:1"), opt("c:1"));
        assertResolution(BASE, "a:1", "b:1");
    }

    @Test
    public void whenSetIncludeDirectDependency() {
        config.alwaysIncludeOptional = true;
        add(BASE, dep("a:1"), opt("b:1"));
        assertResolution(BASE, "a:1", "b:1");
    }

    @Test
    public void whenSetIncludeTransitiveDependency() {
        config.alwaysIncludeOptional = true;
        add(BASE, dep("a:1"));
        add("a:1", dep("b:1"), opt("c:1"));
        assertResolution(BASE, "a:1", "b:1", "c:1");
    }

    private void add(String from, Dep... tos) {
        danglingGAVs.remove(from);
        var pom = new Pom();
        var parts = from.split(":");
        pom.groupId = parts[0];
        pom.artifactId = parts[0];
        pom.version = parts[1];

        for (Dep to : tos) {
            danglingGAVs.add(to.coord);
            var partsTo = to.coord.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], parseVersionSpec(partsTo[1]), Set.of(), Scope.COMPILE,
                    to.isOptional, "jar", "");
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    protected void addDangling() {
        for (var gav : new HashSet<>(danglingGAVs)) {
            add(gav);
        }
    }

    private static Dep dep(String coord) {
        var d = new Dep();
        d.coord = coord;
        d.isOptional = false;
        return d;
    }

    private static Dep opt(String coord) {
        var d = new Dep();
        d.coord = coord;
        d.isOptional = true;
        return d;
    }

    private static class Dep {
        public String coord;
        public boolean isOptional;
    }
}