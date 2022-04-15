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

import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Exclusion;
import eu.fasten.core.maven.data.Pom;

public class MavenDependencyResolverExclusionTest extends AbstractMavenDependencyResolverTest {

    @Test
    public void happyPath() {
        add(BASE, $("a:1"));
        assertDepSet(BASE, "a:1");
    }

    @Test
    public void basicExclusion() {
        add(BASE, $("a:1", "b"));
        add("a:1", $("b:1"));
        assertDepSet(BASE, "a:1");
    }

    @Test
    public void exclusionInOtherBranch1() {
        add(BASE, $("a:1", "x"), $("b:1"));
        add("a:1", $("x:1"));
        add("b:1", $("x:2"));
        assertDepSet(BASE, "a:1", "b:1", "x:2");
    }

    @Test
    public void exclusionInOtherBranch2() {
        add(BASE, $("a:1"), $("b:1", "x"));
        add("a:1", $("x:1"));
        add("b:1", $("x:2"));
        assertDepSet(BASE, "a:1", "b:1", "x:1");
    }

    @Test
    public void exclusionPropagatesOverDependencies() {
        add(BASE, $("a:1", "x"));
        add("a:1", $("b:1"));
        add("b:1", $("c:1"));
        add("c:1", $("x:1"));
        assertDepSet(BASE, "a:1", "b:1", "c:1");
    }

    @Test
    public void exclusionsCumulateOverDependencies() {
        add(BASE, $("a:1", "x"));
        add("a:1", $("b:1", "y"));
        add("b:1", $("c:1"));
        add("c:1", $("x:1"), $("y:1"));
        assertDepSet(BASE, "a:1", "b:1", "c:1");
    }

    @Test
    public void doubleInclusionWithOneExclusion1() {
        add(BASE, $("b1:1", "x"), $("b2:1"));
        add("b1:1", $("c:1"));
        add("b2:1", $("c:1"));
        add("c:1", $("x:1"));
        assertDepSet(BASE, "b1:1", "b2:1", "c:1");
    }

    @Test
    public void doubleInclusionWithOneExclusion2() {
        add(BASE, $("b1:1"), $("b2:1", "x"));
        add("b1:1", $("c:1"));
        add("b2:1", $("c:1"));
        add("c:1", $("x:1"));
        assertDepSet(BASE, "b1:1", "b2:1", "c:1", "x:1");
    }

    private Dep $(String coord, String... excls) {
        var dep = new Dep();
        dep.coord = coord;
        for (var excl : excls) {
            dep.excls.add(excl);
        }
        return dep;
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
            for (var excl : to.excls) {
                d.exclusions.add(Exclusion.init(excl, excl));
            }
            pom.dependencies.add(d);
        }

        data.add(pom);
    }

    protected void addDangling() {
        for (var gav : new HashSet<>(danglingGAVs)) {
            add(gav);
        }
    }

    private class Dep {
        public String coord;
        public Set<String> excls = new HashSet<>();

        @Override
        public String toString() {
            return reflectionToString(this, MULTI_LINE_STYLE);
        }
    }
}