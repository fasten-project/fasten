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

import static eu.fasten.core.maven.data.Scope.IMPORT;
import static eu.fasten.core.maven.data.Scope.PROVIDED;
import static eu.fasten.core.maven.data.Scope.SYSTEM;
import static eu.fasten.core.maven.data.VersionConstraint.parseVersionSpec;
import static eu.fasten.core.maven.resolution.ResolverConfig.resolve;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.PomBuilder;
import eu.fasten.core.maven.data.Scope;

public class MavenDependencyResolverScopesTest extends AbstractMavenDependencyResolverTest {

    private static final Set<String> SOME_COORD = Set.of("g:a:1");

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
    public void resolveCompileDirect() {
        config.scope = Scope.COMPILE;
        add(BASE, //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "s:1", "p:1", "c:1");
    }

    @Test
    public void resolveCompileTrans() {
        config.scope = Scope.COMPILE;
        add(BASE, $("x:1", Scope.COMPILE));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "x:1", "s:1", "c:1");
    }

    @Test
    public void resolveRuntimeDirect() {
        config.scope = Scope.RUNTIME;
        add(BASE, //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "c:1", "r:1"); // WTF p:1? s:1?
    }

    @Test
    public void resolveRuntimeTrans() {
        config.scope = Scope.RUNTIME;
        add(BASE, $("x:1", Scope.COMPILE));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "x:1", "c:1", "r:1"); // WTF s:1?
    }

    @Test
    public void resolveTestDirect() {
        config.scope = Scope.TEST;
        add(BASE, //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "s:1", "p:1", "c:1", "r:1", "t:1");
    }

    @Test
    public void resolveTestTrans() {
        config.scope = Scope.TEST;
        add(BASE, $("x:1", Scope.COMPILE));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "x:1", "s:1", "c:1", "r:1");
    }

    @Test
    public void resolveCompileSystemTrans() {
        config.scope = Scope.COMPILE;
        add(BASE, $("x:1", Scope.SYSTEM));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "x:1");
    }

    @Test
    public void resolveTransSystem_compile() {
        config.scope = Scope.COMPILE;
        add(BASE, $("x:1", Scope.COMPILE));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "x:1", "s:1", "c:1");
    }

    @Test
    public void resolveTransSystem_runtime() {
        config.scope = Scope.RUNTIME;
        add(BASE, $("x:1", Scope.COMPILE));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "x:1", "c:1", "r:1"); // WTF s:1?
    }

    @Test
    public void resolveTransSystem_test() {
        config.scope = Scope.TEST;
        add(BASE, $("x:1", Scope.COMPILE));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "x:1", "c:1", "r:1", "s:1");
    }

    @Test
    public void resolveCompileProvidedTrans() {
        config.scope = Scope.COMPILE;
        add(BASE, $("x:1", Scope.PROVIDED));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "x:1", "s:1", "c:1", "r:1"); // WTF r:1?
    }

    @Test
    public void resolveRuntimeProvidedTrans() {
        config.scope = Scope.RUNTIME;
        add(BASE, $("x:1", Scope.PROVIDED));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE);
    }

    @Test
    public void resolveTestProvidedTrans() {
        config.scope = Scope.TEST;
        add(BASE, $("x:1", Scope.PROVIDED));
        add("x:1", //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertResolution(BASE, "x:1", "s:1", "c:1", "r:1");
    }

    private void add(String from, Dep... tos) {
        danglingGAVs.remove(from);
        var pb = new PomBuilder();
        var parts = from.split(":");
        pb.groupId = parts[0];
        pb.artifactId = parts[0];
        pb.version = parts[1];

        for (var to : tos) {
            danglingGAVs.add(to.coord);
            var partsTo = to.coord.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], parseVersionSpec(partsTo[1]), Set.of(), to.scope, false,
                    "jar", "");
            pb.dependencies.add(d);
        }

        data.add(pb.pom());
    }

    protected void addDangling() {
        for (var gav : new HashSet<>(danglingGAVs)) {
            add(gav);
        }
    }

    private static Dep $(String coord, Scope scope) {
        var d = new Dep();
        d.coord = coord;
        d.scope = scope;
        return d;
    }

    private static class Dep {
        public String coord;
        public Scope scope;
    }
}