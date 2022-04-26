/*
 * Copyright 2021 Delft University of Technology
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

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class ExclusionTest {

    @Test
    public void defaultValues() {
        var sut = new Exclusion(null, null);
        assertNull(sut.artifactId);
        assertNull(sut.groupId);
    }

    @Test
    public void nonDefaultValues() {
        var sut = new Exclusion("g", "a");
        assertEquals("g", sut.groupId);
        assertEquals("a", sut.artifactId);
        assertNotEquals(0, sut.hashCode());
    }

    @Test
    public void equalityDefault() {
        var a = new Exclusion(null, null);
        var b = new Exclusion(null, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityNonDefault() {
        var a = new Exclusion("g", "a");
        var b = new Exclusion("g", "a");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentGroup() {
        var a = new Exclusion("g", "a");
        var b = new Exclusion("g2", "a");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentArtifact() {
        var a = new Exclusion("g", "a");
        var b = new Exclusion("g", "a2");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void legacyJson() {
        var sut = new Exclusion("g", "a");
        assertEquals("g:a", sut.toJSON());
        var clone = Exclusion.fromJSON("g:a");
        assertEquals(sut, clone);
    }
}