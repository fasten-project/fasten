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
package eu.fasten.core.maven.data;

import static org.junit.Assert.assertSame;

import org.junit.jupiter.api.Test;

public class IdsTest {

    @Test
    public void gid() {
        var a = Ids.gid("a");
        var b = Ids.gid("a");
        assertSame(a, b);
    }

    @Test
    public void aid() {
        var a = Ids.aid("a");
        var b = Ids.aid("a");
        assertSame(a, b);
    }

    @Test
    public void version() {
        var a = Ids.version("1");
        var b = Ids.version("1");
        assertSame(a, b);
    }

    @Test
    public void versionConstraint() {
        var a = Ids.versionConstraint(new VersionConstraint("1"));
        var b = Ids.versionConstraint(new VersionConstraint("1"));
        assertSame(a, b);
    }

    @Test
    public void dep() {
        var a = Ids.dep(new Dependency("g", "a", "1"));
        var b = Ids.dep(new Dependency("g", "a", "1"));
        assertSame(a, b);
    }

    @Test
    public void gav() {
        var a = Ids.gav(new GAV("g", "a", "1"));
        var b = Ids.gav(new GAV("g", "a", "1"));
        assertSame(a, b);
    }

    @Test
    public void ga() {
        var a = Ids.ga(new GA("g", "a"));
        var b = Ids.ga(new GA("g", "a"));
        assertSame(a, b);
    }
}