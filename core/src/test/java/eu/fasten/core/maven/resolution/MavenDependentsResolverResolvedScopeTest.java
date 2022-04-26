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
import static eu.fasten.core.maven.data.Scope.RUNTIME;
import static eu.fasten.core.maven.data.Scope.SYSTEM;
import static eu.fasten.core.maven.data.Scope.TEST;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Scope;

public class MavenDependentsResolverResolvedScopeTest extends AbstractMavenDependentsResolverTest {

    private static final Set<Scope> RESOLVABLE_SCOPES = Set.of(COMPILE, RUNTIME, TEST);
    private static final boolean[] TRUE_FALSE = new boolean[] { true, false };

    protected static final String BASE = "base:1";
    protected static final String S = "s:1";
    protected static final String P = "p:1";
    protected static final String C = "c:1";
    protected static final String R = "r:1";
    protected static final String T = "t:1";

    protected Set<String> danglingGAVs;
    private String base;

    @BeforeEach
    public void setup() {
        danglingGAVs = new HashSet<>();
        base = null;

        add(BASE, //
                s(SYSTEM), //
                p(PROVIDED), //
                c(COMPILE), //
                r(RUNTIME), //
                t(TEST));

        add(P, //
                $("ps:1", Scope.SYSTEM), //
                $("pp:1", Scope.PROVIDED), //
                $("pc:1", Scope.COMPILE), //
                $("pr:1", Scope.RUNTIME), //
                $("pt:1", Scope.TEST));

        add(C, //
                $("cs:1", Scope.SYSTEM), //
                $("cp:1", Scope.PROVIDED), //
                $("cc:1", Scope.COMPILE), //
                $("cr:1", Scope.RUNTIME), //
                $("ct:1", Scope.TEST));

        add(R, //
                $("rs:1", Scope.SYSTEM), //
                $("rp:1", Scope.PROVIDED), //
                $("rc:1", Scope.COMPILE), //
                $("rr:1", Scope.RUNTIME), //
                $("rt:1", Scope.TEST));

        add(T, //
                $("ts:1", Scope.SYSTEM), //
                $("tp:1", Scope.PROVIDED), //
                $("tc:1", Scope.COMPILE), //
                $("tr:1", Scope.RUNTIME), //
                $("tt:1", Scope.TEST));
    }

    @Test
    public void base() {
        base = BASE;
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime();
            assertTesting();
        });
    }

    @Test
    public void s() {
        base = S;
        independentOfIncludeProvided(() -> {
            assertCompile(base(SYSTEM));
            assertRuntime(); // WTF why is system not covered by runtime?
            assertTesting(base(SYSTEM));
        });
    }

    @Test
    public void p() {
        base = P;
        independentOfIncludeProvided(() -> {
            assertCompile(base(PROVIDED));
            assertTesting(base(PROVIDED));
        });

        config.alwaysIncludeProvided = false;
        assertRuntime(); // WTF why is provided not covered by runtime?

        config.alwaysIncludeProvided = true;
        assertRuntime(base(PROVIDED));
    }

    @Test
    public void c() {
        base = C;
        independentOfIncludeProvided(() -> {
            assertCompile(base(COMPILE));
            assertRuntime(base(COMPILE));
            assertTesting(base(COMPILE));
        });
    }

    @Test
    public void r() {
        base = R;
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime(base(RUNTIME));
            assertTesting(base(RUNTIME));
        });
    }

    @Test
    public void t() {
        base = T;
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime();
            assertTesting(base(TEST));
        });
    }

    @Test
    public void ps() {
        base = "ps:1";
        independentOfIncludeProvided(() -> {
            assertCompile(p(SYSTEM), base(SYSTEM));
            assertRuntime();
            assertTesting(p(SYSTEM), base(SYSTEM));
        });
    }

    @Test
    public void pp() {
        base = "pp:1";
        withoutIncludeProvided(() -> {
            assertCompile(p(PROVIDED));
            assertRuntime();
            assertTesting(p(PROVIDED));
        });
        withIncludeProvided(() -> {
            RESOLVABLE_SCOPES.forEach(scope -> {
                assertScope(scope, p(PROVIDED), base(PROVIDED));
            });
        });
    }

    @Test
    public void pc() {
        base = "pc:1";
        withoutIncludeProvided(() -> {
            assertCompile(p(COMPILE), base(PROVIDED));
            assertRuntime(p(COMPILE));
            assertTesting(p(COMPILE), base(PROVIDED));
        });
        withIncludeProvided(() -> {
            RESOLVABLE_SCOPES.forEach(scope -> {
                assertScope(scope, p(COMPILE), base(PROVIDED));
            });
        });
    }

    @Test
    public void pr() {
        base = "pr:1";
        withoutIncludeProvided(() -> {
            assertCompile(); // WTF: technically incorrect, in reality, base>p>pr is a dependency
            assertRuntime(p(RUNTIME));
            assertTesting(p(RUNTIME), base(PROVIDED));
        });
        withIncludeProvided(() -> {
            assertCompile();
            assertRuntime(p(RUNTIME), base(PROVIDED));
            assertTesting(p(RUNTIME), base(PROVIDED));
        });
    }

    @Test
    public void pt() {
        base = "pt:1";
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime();
            assertTesting(p(TEST));
        });
    }

    @Test
    public void cs() {
        base = "cs:1";
        independentOfIncludeProvided(() -> {
            assertCompile(c(SYSTEM), base(SYSTEM));
            assertRuntime();
            assertTesting(c(SYSTEM), base(SYSTEM));
        });
    }

    @Test
    public void cp() {
        base = "cp:1";
        withoutIncludeProvided(() -> {
            assertCompile(c(PROVIDED));
            assertRuntime();
            assertTesting(c(PROVIDED));
        });
        withIncludeProvided(() -> {
            RESOLVABLE_SCOPES.forEach(scope -> {
                assertScope(scope, c(PROVIDED), base(PROVIDED));
            });
        });
    }

    @Test
    public void cc() {
        base = "cc:1";
        independentOfIncludeProvided(() -> {
            RESOLVABLE_SCOPES.forEach(scope -> {
                assertScope(scope, c(COMPILE), base(COMPILE));
            });
        });
    }

    @Test
    public void cr() {
        base = "cr:1";
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime(c(RUNTIME), base(RUNTIME));
            assertTesting(c(RUNTIME), base(RUNTIME));
        });
    }

    @Test
    public void ct() {
        base = "ct:1";
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime();
            assertTesting(c(TEST));
        });
    }

    @Test
    public void rs() {
        base = "rs:1";
        independentOfIncludeProvided(() -> {
            assertCompile(r(SYSTEM));
            assertRuntime();
            assertTesting(r(SYSTEM), base(SYSTEM));
        });
    }

    @Test
    public void rp() {
        base = "rp:1";
        withoutIncludeProvided(() -> {
            assertCompile(r(PROVIDED));
            assertRuntime();
            assertTesting(r(PROVIDED));
        });
        withIncludeProvided(() -> {
            assertCompile(r(PROVIDED));
            assertRuntime(r(PROVIDED), base(PROVIDED));
            assertTesting(r(PROVIDED), base(PROVIDED));
        });
    }

    @Test
    public void rc() {
        base = "rc:1";
        independentOfIncludeProvided(() -> {
            assertCompile(r(COMPILE));
            assertRuntime(r(COMPILE), base(RUNTIME));
            assertTesting(r(COMPILE), base(RUNTIME));
        });
    }

    @Test
    public void rr() {
        base = "rr:1";
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime(r(RUNTIME), base(RUNTIME));
            assertTesting(r(RUNTIME), base(RUNTIME));
        });
    }

    @Test
    public void rt() {
        base = "rt:1";
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime();
            assertTesting(r(TEST));
        });
    }

    @Test
    public void ts() {
        base = "ts:1";
        independentOfIncludeProvided(() -> {
            assertCompile(t(SYSTEM));
            assertRuntime();
            assertTesting(t(SYSTEM), base(SYSTEM));
        });
    }

    @Test
    public void tp() {
        base = "tp:1";
        withoutIncludeProvided(() -> {
            assertCompile(t(PROVIDED));
            assertRuntime();
            assertTesting(t(PROVIDED));
        });
        withIncludeProvided(() -> {
            assertCompile(t(PROVIDED));
            assertRuntime(t(PROVIDED));
            assertTesting(t(PROVIDED), base(PROVIDED));
        });
    }

    @Test
    public void tc() {
        base = "tc:1";
        independentOfIncludeProvided(() -> {
            assertCompile(t(COMPILE));
            assertRuntime(t(COMPILE));
            assertTesting(t(COMPILE), base(TEST));
        });
    }

    @Test
    public void tr() {
        base = "tr:1";
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime(t(RUNTIME));
            assertTesting(t(RUNTIME), base(TEST));
        });
    }

    @Test
    public void tt() {
        base = "tt:1";
        independentOfIncludeProvided(() -> {
            assertCompile();
            assertRuntime();
            assertTesting(t(TEST));
        });
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
            d.setScope(to.scope);
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    private void independentOfIncludeProvided(Runnable r) {
        var before = config.alwaysIncludeProvided;
        for (var flag : TRUE_FALSE) {
            config.alwaysIncludeProvided = flag;
            r.run();
        }
        config.alwaysIncludeProvided = before;
    }

    private void withoutIncludeProvided(Runnable r) {
        var before = config.alwaysIncludeProvided;
        config.alwaysIncludeProvided = false;
        r.run();
        config.alwaysIncludeProvided = before;
    }

    private void withIncludeProvided(Runnable r) {
        var before = config.alwaysIncludeProvided;
        config.alwaysIncludeProvided = true;
        r.run();
        config.alwaysIncludeProvided = before;
    }

    private void assertCompile(Dep... deps) {
        assertScope(COMPILE, deps);
    }

    private void assertRuntime(Dep... deps) {
        assertScope(RUNTIME, deps);
    }

    private void assertTesting(Dep... deps) {
        assertScope(TEST, deps);
    }

    private void assertScope(Scope scope, Dep... deps) {
        if (base == null) {
            throw new RuntimeException("base not set");
        }
        config.scope = scope;
        var gavsSet = Arrays.stream(deps).map(d -> d.coord).collect(Collectors.toCollection(LinkedHashSet::new));
        var gavs = new String[gavsSet.size()];
        gavs = gavsSet.toArray(gavs);

        var actuals = assertResolution(base, gavs).stream() //
                .map(rr -> String.format("%s:%s:%s:%s", rr.getGroupId(), rr.getArtifactId(), rr.version, rr.scope)) //
                .collect(Collectors.toCollection(TreeSet::new));
        var expecteds = Arrays.stream(deps) //
                .map(d -> {
                    var parts = d.coord.split(":");
                    return String.format("%s:%s:%s:%s", parts[0], parts[0], parts[1], d.scope);
                }) //
                .collect(Collectors.toCollection(TreeSet::new));

        if (!expecteds.equals(actuals)) {
            var sb = new StringBuilder();
            sb.append("Resolution has returned unexpected dependency scopes.\n\n");
            sb.append(config).append("\n\n");
            sb.append("Expected:\n");
            for (var e : expecteds) {
                sb.append("\t- ").append(e).append("\n");
            }
            sb.append("Was:\n");
            for (var a : actuals) {
                sb.append("\t- ").append(a).append("\n");
            }
            fail(sb.toString());
        }
    }

    @Override
    protected void addDangling() {
        for (var coord : new HashSet<>(danglingGAVs)) {
            add(coord);
        }
    }

    private static Dep base(Scope scope) {
        return $(BASE, scope);
    }

    private static Dep p(Scope scope) {
        return $("p:1", scope);
    }

    private static Dep s(Scope scope) {
        return $("s:1", scope);
    }

    private static Dep c(Scope scope) {
        return $("c:1", scope);
    }

    private static Dep r(Scope scope) {
        return $("r:1", scope);
    }

    private static Dep t(Scope scope) {
        return $("t:1", scope);
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