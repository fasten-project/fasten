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
import eu.fasten.core.maven.data.PomBuilder;
import eu.fasten.core.maven.data.Scope;

public class MavenDependencyResolverIncludeProvidedTest extends AbstractMavenDependencyResolverTest {

    private static final Scope[] SCOPES = new Scope[] { Scope.COMPILE, Scope.RUNTIME, Scope.TEST };

    @Test
    public void disabledByDefault() {
        assertFalse(config.alwaysIncludeProvided);
    }

    @Test
    public void ifUnsetIncludeDirectProvidedDepsForCompile() {
        config.scope = Scope.COMPILE;
        add(BASE, dep("a:1"), prov("b:1"));
        assertResolution(BASE, "a:1", "b:1");
    }

    @Test
    public void ifUnsetDoNotIncludeDirectProvidedDepsForRuntime() {
        config.scope = Scope.RUNTIME;
        add(BASE, dep("a:1"), prov("b:1"));
        assertResolution(BASE, "a:1");
    }

    @Test
    public void ifUnsetIncludeDirectProvidedDepsForTest() {
        config.scope = Scope.TEST;
        add(BASE, dep("a:1"), prov("b:1"));
        assertResolution(BASE, "a:1", "b:1");
    }

    @Test
    public void ifUnsetDoNotIncludeTransitiveProvidedDepsForAllScopes() {
        add(BASE, dep("a:1"));
        add("a:1", dep("b:1"), prov("c:1"));

        for (var scope : SCOPES) {
            config.scope = scope;
            assertResolution(BASE, "a:1", "b:1");
        }
    }

    @Test
    public void whenSetIncludeDirectProvidedDepsForAllScope() {
        config.alwaysIncludeProvided = true;
        add(BASE, dep("a:1"), prov("b:1"));

        for (var scope : SCOPES) {
            config.scope = scope;
            assertResolution(BASE, "a:1", "b:1");
        }
    }

    @Test
    public void whenSetIncludeTransitiveProvidedDepsForAllScopes() {
        config.alwaysIncludeProvided = true;
        add(BASE, dep("a:1"));
        add("a:1", dep("b:1"), prov("c:1"));

        for (var scope : SCOPES) {
            config.scope = scope;
            assertResolution(BASE, "a:1", "b:1", "c:1");
        }
    }

    private void add(String from, Dep... tos) {
        danglingGAVs.remove(from);
        var pb = new PomBuilder();
        var parts = from.split(":");
        pb.groupId = parts[0];
        pb.artifactId = parts[0];
        pb.version = parts[1];

        for (Dep to : tos) {
            danglingGAVs.add(to.coord);
            var partsTo = to.coord.split(":");
            var s = to.isProvided ? Scope.PROVIDED : Scope.COMPILE;
            var d = new Dependency(partsTo[0], partsTo[0], parseVersionSpec(partsTo[1]), Set.of(), s, false, "jar", "");
            pb.dependencies.add(d);
        }

        data.add(pb.pom());
    }

    protected void addDangling() {
        for (var gav : new HashSet<>(danglingGAVs)) {
            add(gav);
        }
    }

    private static Dep dep(String coord) {
        var d = new Dep();
        d.coord = coord;
        d.isProvided = false;
        return d;
    }

    private static Dep prov(String coord) {
        var d = new Dep();
        d.coord = coord;
        d.isProvided = true;
        return d;
    }

    private static class Dep {
        public String coord;
        public boolean isProvided;
    }
}