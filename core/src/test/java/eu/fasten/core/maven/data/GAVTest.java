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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class GAVTest {

    @Test
    public void init() {
        var sut = new GAV("g", "a", "1");
        assertEquals("g", sut.groupId);
        assertEquals("a", sut.artifactId);
        assertEquals("1", sut.version);
    }

    @Test
    public void equality() {
        var a = new GAV("g", "a", "1");
        var b = new GAV("g", "a", "1");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffGroup() {
        var a = new GAV("g", "a", "1");
        var b = new GAV("g2", "a", "1");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffArtifact() {
        var a = new GAV("g", "a", "1");
        var b = new GAV("g", "a2", "1");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffVersion() {
        var a = new GAV("g", "a", "1");
        var b = new GAV("g", "a", "2");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hasToString() {
        assertEquals("g:a:1", new GAV("g", "a", "1").toString());
    }
}