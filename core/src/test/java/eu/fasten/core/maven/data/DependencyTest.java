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
import static eu.fasten.core.maven.data.Scope.RUNTIME;
import static eu.fasten.core.maven.data.Scope.TEST;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class DependencyTest {

    @Test
    public void defaults() {
        var sut = new Dependency();
        assertEquals(0, sut.id);
        assertEquals("", sut.getGroupId());
        assertEquals("", sut.getArtifactId());

        assertEquals("", sut.getVersion());
        assertEquals(Set.of(), sut.getVersionConstraints());
        assertArrayEquals(new String[0], sut.getVersionConstraintsArr());

        assertEquals(Set.of(), sut.getExclusions());
        assertEquals("", sut.getClassifier());
        assertEquals("jar", sut.getPackagingType());
        assertEquals(COMPILE, sut.getScope());

        assertFalse(sut.isOptional());
    }

    @Test
    public void nonDefaults() {
        var sut = getSomeDep();
        assertEquals(0, sut.id);
        assertEquals("gid", sut.getGroupId());
        assertEquals("aid", sut.getArtifactId());

        assertEquals("(1.2.3,2.3.4]", sut.getVersion());
        assertEquals(Set.of(new VersionConstraint("(1.2.3,2.3.4]")), sut.getVersionConstraints());
        String[] x = sut.getVersionConstraintsArr();
        assertArrayEquals(new String[] { "(1.2.3,2.3.4]" }, x);

        assertEquals(Set.of(Exclusion.init("gid2", "aid2")), sut.getExclusions());
        assertEquals("sources", sut.getClassifier());
        assertEquals("pom", sut.getPackagingType());
        assertEquals(PROVIDED, sut.getScope());

        assertTrue(sut.isOptional());
    }

    @Test
    public void hasToString() {
        var actual = new Dependency().toString();
        assertTrue(actual.contains(Dependency.class.getSimpleName()));
        assertTrue(actual.contains("@"));
        assertTrue(actual.contains("\n"));
        assertTrue(actual.contains("versionConstraints"));
    }

    @Test
    public void toGA() {
        var actual = new Dependency("g", "a", "1.2.3").toGA();
        var expected = "g:a";
        assertEquals(expected, actual);
    }

    @Test
    public void equalityDefaults() {
        var a = new Dependency();
        var b = new Dependency();
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
    public void equalityDiffVersionsSet() {
        var a = getSomeDep();
        var b = getSomeDep();
        b.setVersionConstraints(VersionConstraint.parseVersionSpec("1.2.3"));
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffVersionsAdd() {
        var a = getSomeDep();
        var b = getSomeDep();
        b.addVersionConstraint(new VersionConstraint("1.2.3"));
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffExclusions() {
        var a = getSomeDep();
        var b = getSomeDep();
        b.addExclusion(Exclusion.init("gx", "ax"));
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffGroup() {
        var a = getSomeDep();
        var b = getSomeDep();
        b.setGroupId("g2");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffArtifact() {
        var a = getSomeDep();
        var b = getSomeDep();
        b.setArtifactId("a2");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffType() {
        var a = getSomeDep();
        var b = getSomeDep();
        b.setPackagingType("asd");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffClassifier() {
        var a = getSomeDep();
        var b = getSomeDep();
        b.setClassifier("asd");
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffScope() {
        var a = getSomeDep();
        var b = getSomeDep();
        b.setScope(TEST);
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalityDiffOptional() {
        var a = getSomeDep();
        var b = getSomeDep();
        b.setOptional(false);
        assertNotEquals(a, b);
        assertNotEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void hashCodeGroupId() {
        assertHashCodeChange(new Dependency(), d -> {
            d.setGroupId("asd");
        });
    }

    @Test
    public void hashCodeArtifactId() {
        assertHashCodeChange(new Dependency(), d -> {
            d.setArtifactId("asd");
        });
    }

    @Test
    public void hashCodeVersionSet() {
        assertHashCodeChange(new Dependency(), d -> {
            d.setVersionConstraints(VersionConstraint.parseVersionSpec("1.2.3"));
        });
    }

    @Test
    public void hashCodeVersionAdd() {
        assertHashCodeChange(new Dependency(), d -> {
            d.addVersionConstraint(new VersionConstraint("1.2.3"));
        });
    }

    @Test
    public void hashCodeAddExclusion() {
        assertHashCodeChange(new Dependency(), d -> {
            d.addExclusion(Exclusion.init("g", "a"));
        });
    }

    @Test
    public void hashCodeScope() {
        assertHashCodeChange(new Dependency(), d -> {
            d.setScope(RUNTIME);
        });
    }

    @Test
    public void hashCodeOptional() {
        assertHashCodeChange(new Dependency(), d -> {
            d.setOptional(true);
        });
    }

    @Test
    public void hashCodePackagingType() {
        assertHashCodeChange(new Dependency(), d -> {
            d.setPackagingType("foo");
        });
    }

    @Test
    public void hashCodeClassifier() {
        assertHashCodeChange(new Dependency(), d -> {
            d.setClassifier("sdfsdf");
        });
    }

    @Test
    public void versionIsImmutable() {
        var vcs = new Dependency().getVersionConstraints();
        assertThrows(UnsupportedOperationException.class, () -> {
            vcs.clear();
        });
    }

    @Test
    public void versionSetDoesNotChangeInstance() {
        var v1 = new VersionConstraint("1.2.3");
        var v2 = new VersionConstraint("2.3.4");

        var sut = new Dependency();
        sut.addVersionConstraint(v1);
        var a = sut.getVersionConstraints();
        sut.setVersionConstraints(Set.of(v2));
        var b = sut.getVersionConstraints();
        assertNotSame(a, b);
        assertTrue(a.contains(v2));
    }

    @Test
    public void exclusionSetIsImmutable() {
        var es = new Dependency().getExclusions();
        assertThrows(UnsupportedOperationException.class, () -> {
            es.clear();
        });
    }

    private static <T> void assertHashCodeChange(T obj, Consumer<T> c) {
        var a = obj.hashCode();
        assertNotEquals(0, a);
        c.accept(obj);
        var b = obj.hashCode();
        assertNotEquals(a, b);
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
                new Dependency("junit", "junit", "4.12", new HashSet<>(), COMPILE, false, "jar", ""));
    }

    @Test
    public void cannotUseEmptyPackagingType() {
        assertThrows(IllegalStateException.class, () -> {
            new Dependency("junit", "junit", "4.12", new HashSet<>(), COMPILE, false, "", "");
        });
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
        var a = someDep();
        var b = someDep();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    private Dependency someDep() {
        var vcs = new HashSet<VersionConstraint>();
        vcs.add(new VersionConstraint("(,1.0]"));
        vcs.add(new VersionConstraint("[1.2)"));
        var excls = new HashSet<Exclusion>();
        return new Dependency("g", "a", vcs, excls, COMPILE, true, "jar", "c");
    }

    public static Dependency getSomeDep() {
        return new Dependency("gid", "aid", Set.of(new VersionConstraint("(1.2.3,2.3.4]")),
                Set.of(Exclusion.init("gid2", "aid2")), PROVIDED, true, "pom", "sources");
    }
}