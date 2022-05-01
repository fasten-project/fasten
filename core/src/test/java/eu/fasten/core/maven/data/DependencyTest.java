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

import static eu.fasten.core.maven.data.Scope.COMPILE;
import static eu.fasten.core.maven.data.Scope.PROVIDED;
import static eu.fasten.core.maven.data.Scope.TEST;
import static eu.fasten.core.maven.data.VersionConstraint.parseVersionSpec;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class DependencyTest {

    @Test
    public void defaults() {
        var sut = new Dependency("a", "b", "1");
        assertEquals("a", sut.groupId);
        assertEquals("b", sut.artifactId);

        assertEquals("1", sut.getVersion());
        assertEquals(Set.of(new VersionConstraint("1")), sut.getVersionConstraints());
        assertArrayEquals(new String[] { "1" }, sut.getVersionConstraintsArr());

        assertEquals(Set.of(), sut.getExclusions());
        assertEquals("", sut.getClassifier());
        assertEquals("jar", sut.getPackagingType());
        assertEquals(COMPILE, sut.getScope());

        assertFalse(sut.isOptional());
    }

    @Test
    public void nonDefaults() {
        var sut = getSomeDep();
        assertEquals("gid", sut.groupId);
        assertEquals("aid", sut.artifactId);

        assertEquals("(1.2.3,2.3.4]", sut.getVersion());
        assertEquals(Set.of(new VersionConstraint("(1.2.3,2.3.4]")), sut.getVersionConstraints());
        assertArrayEquals(new String[] { "(1.2.3,2.3.4]" }, sut.getVersionConstraintsArr());

        assertEquals(Set.of(new Exclusion("gid2", "aid2")), sut.getExclusions());
        assertEquals("sources", sut.getClassifier());
        assertEquals("pom", sut.getPackagingType());
        assertEquals(PROVIDED, sut.getScope());

        assertTrue(sut.isOptional());
    }

    @Test
    public void noCrashOnNullPackagingType() {
        var sut = new Dependency("gid", "aid", Set.of(new VersionConstraint("(1.2.3,2.3.4]")),
                Set.of(new Exclusion("gid2", "aid2")), PROVIDED, true, null, "sources");
        assertEquals("jar", sut.getPackagingType());
    }

    @Test
    public void noCrashOnEmptyPackagingType() {
        var sut = new Dependency("gid", "aid", Set.of(new VersionConstraint("(1.2.3,2.3.4]")),
                Set.of(new Exclusion("gid2", "aid2")), PROVIDED, true, "", "sources");
        assertEquals("jar", sut.getPackagingType());
    }

    @Test
    public void noCrashOnNullClassifier() {
        var sut = new Dependency("gid", "aid", Set.of(new VersionConstraint("(1.2.3,2.3.4]")),
                Set.of(new Exclusion("gid2", "aid2")), PROVIDED, true, "pom", null);
        assertEquals("", sut.getClassifier());
    }

    @Test
    public void hasToString() {
        var actual = getSomeDep().toString();
        assertTrue(actual.contains(Dependency.class.getSimpleName()));
        assertTrue(actual.contains("@"));
        assertTrue(actual.contains("\n"));
        assertTrue(actual.contains("versionConstraints"));
    }

    @Test
    public void toGA() {
        var actual = new Dependency("g", "a", "1.2.3").toGA();
        var expected = new GA("g", "a");
        assertEquals(expected, actual);
    }

    @Test
    public void toGAIsStable() {
        var a = new Dependency("g", "a", "1.2.3").toGA();
        var b = new Dependency("g", "a", "1.2.3").toGA();
        assertSame(a, b);
    }

    @Test
    public void equalityDefaults() {
        var a = new Dependency("g", "a", "1");
        var b = new Dependency("g", "a", "1");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityNonDefaults() {
        var a = getSomeDep();
        var b = getSomeDep();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffVersions() {
        var a = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        var b = new Dependency("g", "a", Set.of(vc(1), vc(2)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffExclusions() {
        var a = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        var b = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1), ex(2)), COMPILE, true, "jar", "c");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffGroup() {
        var a = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        var b = new Dependency("g2", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffArtifact() {
        var a = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        var b = new Dependency("g", "a2", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffType() {
        var a = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        var b = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "war", "c");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffClassifier() {
        var a = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        var b = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "d");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffScope() {
        var a = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        var b = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), TEST, true, "jar", "c");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffOptional() {
        var a = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        var b = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, false, "jar", "c");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void dependencyTest() {
        var expected = new Dependency("junit", "junit", "4.11");
        var json = expected.toJSON();
        var actual = Dependency.fromJSON(json);
        assertEquals(expected, actual);
    }

    @Test
    public void equalsTest() {
        Assertions.assertEquals( //
                new Dependency("junit", "junit", "4.12"), //
                new Dependency("junit", "junit", parseVersionSpec("4.12"), new HashSet<>(), COMPILE, false, "jar", ""));
    }

    @Test
    public void jsonRoundtripViaObj() {
        var a = getSomeDep();
        var jsonObj = a.toJSON();
        var b = Dependency.fromJSON(jsonObj);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void jsonRoundtripViaString() {
        var a = getSomeDep();
        var json = a.toJSON().toString();
        var jsonObj = new JSONObject(json);
        var b = Dependency.fromJSON(jsonObj);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void jsonHasRightFields() {

        var expected = new HashMap<String, Class<?>>();
        expected.put("artifactId", String.class);
        expected.put("groupId", String.class);
        expected.put("type", String.class);
        expected.put("versionConstraints", JSONArray.class);
        expected.put("exclusions", JSONArray.class);
        expected.put("scope", Scope.class);
        expected.put("classifier", String.class);
        expected.put("optional", Boolean.class);

        var actual = getSomeDep().toJSON();
        for (var expectedField : expected.keySet()) {
            var expectedType = expected.get(expectedField);

            if (!actual.has(expectedField)) {
                fail(String.format("Resulting json object is missing field '%s'", expectedField));
            }
            var obj = actual.get(expectedField);
            assertNotNull(obj);
            var objType = obj.getClass();
            if (!expectedType.isAssignableFrom(objType)) {
                fail(String.format("Expected type %s, but was %s", expectedType, objType));
            }
        }
    }

    @Test
    public void equality() {
        var a = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        var b = new Dependency("g", "a", Set.of(vc(1)), Set.of(ex(1)), COMPILE, true, "jar", "c");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    private Exclusion ex(int i) {
        return new Exclusion("g" + i, "a" + i);
    }

    private VersionConstraint vc(int v) {
        return new VersionConstraint("" + v);
    }

    public static Dependency getSomeDep() {
        return new Dependency("gid", "aid", Set.of(new VersionConstraint("(1.2.3,2.3.4]")),
                Set.of(new Exclusion("gid2", "aid2")), PROVIDED, true, "pom", "sources");
    }
}