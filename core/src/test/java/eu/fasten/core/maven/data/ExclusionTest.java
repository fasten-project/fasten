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
        var sut = new Exclusion();
        assertNull(sut.getArtifactId());
        assertNull(sut.getGroupId());
    }

    @Test
    public void canSetGroupId() {
        var sut = new Exclusion();
        sut.setGroupId("g");
        assertEquals("g", sut.getGroupId());
    }

    @Test
    public void canSetArtifactId() {
        var sut = new Exclusion();
        sut.setArtifactId("a");
        assertEquals("a", sut.getArtifactId());
    }

    @Test
    public void staticInitializer() {
        var sut = Exclusion.init("g", "a");
        assertEquals("g", sut.getGroupId());
        assertEquals("a", sut.getArtifactId());
        assertNotEquals(0, sut.hashCode());
    }

    @Test
    public void equalityDefault() {
        var a = new Exclusion();
        var b = new Exclusion();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityNonDefault() {
        var a = getNonDefaultExclusion();
        var b = getNonDefaultExclusion();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentGroup() {
        var a = getNonDefaultExclusion();
        var b = getNonDefaultExclusion();
        b.setGroupId("g2");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDifferentArtifact() {
        var a = getNonDefaultExclusion();
        var b = getNonDefaultExclusion();
        b.setArtifactId("a2");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void setGroupChangesHashCode() {
        var sut = getNonDefaultExclusion();
        var a = sut.hashCode();
        sut.setGroupId("g2");
        var b = sut.hashCode();
        assertNotEquals(a, b);
    }

    @Test
    public void setArtifactChangesHashCode() {
        var sut = getNonDefaultExclusion();
        var a = sut.hashCode();
        sut.setArtifactId("a2");
        var b = sut.hashCode();
        assertNotEquals(a, b);
    }

    private static Exclusion getNonDefaultExclusion() {
        return Exclusion.init("g", "a");
    }

    @Test
    @SuppressWarnings("deprecation")
    public void legacyJson() {
        var sut = Exclusion.init("g", "a");
        assertEquals("g:a", sut.toJSON());
        var clone = Exclusion.fromJSON("g:a");
        assertEquals(sut, clone);
    }
}