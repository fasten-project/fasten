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
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class MavenProductTest {

    @Test
    public void defaultsInitNoArgs() {
        var sut = new MavenProduct();
        assertEquals(0, sut.id);
        assertNull(sut.groupId);
        assertNull(sut.artifactId);
    }

    @Test
    public void defaultsInit1() {
        var sut = new MavenProduct("g", "a");
        assertEquals(0, sut.id);
        assertEquals("g", sut.groupId);
        assertEquals("a", sut.artifactId);
    }

    @Test
    public void defaultsInit2() {
        var sut = new MavenProduct(1, "g", "a");
        assertEquals(1, sut.id);
        assertEquals("g", sut.groupId);
        assertEquals("a", sut.artifactId);
    }

    @Test
    public void equalityDefault() {
        var a = new MavenProduct();
        var b = new MavenProduct();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityActualValues() {
        var a = new MavenProduct(1, "g", "a");
        var b = new MavenProduct(1, "g", "a");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDespiteDifferentId() {
        var a = new MavenProduct(1, "g", "a");
        var b = new MavenProduct(2, "g", "a");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentGroup() {
        var a = new MavenProduct(1, "g", "a");
        var b = new MavenProduct(1, "g2", "a");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentArtifact() {
        var a = new MavenProduct(1, "g", "a");
        var b = new MavenProduct(1, "g", "a2");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hasToString() {
        var actual = new MavenProduct(1, "g", "a").toString();
        assertTrue(actual.contains(MavenProduct.class.getSimpleName()));
        assertTrue(actual.contains("\n"));
        assertTrue(actual.contains("@"));
        assertTrue(actual.contains("artifactId"));
    }
}