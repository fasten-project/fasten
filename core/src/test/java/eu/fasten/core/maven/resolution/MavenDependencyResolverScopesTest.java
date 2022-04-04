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
import static eu.fasten.core.maven.resolution.ResolverConfig.resolve;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.data.Scope;

public class MavenDependencyResolverScopesTest {

    private static final Set<String> SOME_COORD = Set.of("g:a:1");

    private static final String BASE = "base:1";

    private Set<String> danglingGAVs;
    private ResolverConfig config;
    private MavenDependencyData data;
    private MavenDependencyResolver sut;

    @BeforeEach
    public void setup() {
        danglingGAVs = new HashSet<>();
        data = new MavenDependencyData();
        sut = new MavenDependencyResolver();
        sut.setData(data);
        config = new ResolverConfig();
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
    public void resolveCompileDirect() {
        config.scope = Scope.COMPILE;
        add(BASE, //
                $("s:1", Scope.SYSTEM), //
                $("p:1", Scope.PROVIDED), //
                $("c:1", Scope.COMPILE), //
                $("r:1", Scope.RUNTIME), //
                $("t:1", Scope.TEST));
        assertDepSet(BASE, "s:1", "p:1", "c:1");
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
        assertDepSet(BASE, "x:1", "s:1", "c:1");
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
        assertDepSet(BASE, "s:1", "c:1", "r:1");
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
        assertDepSet(BASE, "x:1", "s:1", "c:1", "r:1");
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
        assertDepSet(BASE, "s:1", "p:1", "c:1", "r:1", "t:1");
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
        assertDepSet(BASE, "x:1", "s:1", "c:1", "r:1");
    }

    private void add(String from, Dep... tos) {
        danglingGAVs.remove(from);
        var pom = new Pom();
        var parts = from.split(":");
        pom.groupId = parts[0];
        pom.artifactId = parts[0];
        pom.version = parts[1];

        for (var to : tos) {
            danglingGAVs.add(to.coord);
            var partsTo = to.coord.split(":");
            var d = new Dependency(partsTo[0], partsTo[0], partsTo[1]);
            d.scope = to.scope;
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    private void addDangling() {
        for (var gav : new HashSet<>(danglingGAVs)) {
            add(gav);
        }
    }

    private void assertDepSet(String... gavs) {
        addDangling();
        var baseParts = BASE.split(":");
        var base = String.format("%s:%s:%s", baseParts[0], baseParts[0], baseParts[1]);
        var actuals = sut.resolve(Set.of(base), config);
        var expecteds = Arrays.stream(gavs) //
                .map(gav -> gav.split(":")) //
                .map(parts -> new Revision(parts[0], parts[0], parts[1], new Timestamp(-1L))) //
                .collect(Collectors.toSet());

        if (!expecteds.equals(actuals)) {
            var sb = new StringBuilder();
            sb.append("Expected:\n");
            for (var e : expecteds) {
                sb.append("- ").append(e.groupId).append(":").append(e.artifactId).append(":").append(e.version)
                        .append("\n");
            }
            sb.append("But was:\n");
            for (var a : actuals) {
                sb.append("- ").append(a.groupId).append(":").append(a.artifactId).append(":").append(a.version)
                        .append("\n");
            }
            fail(sb.toString());
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