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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyData {

    private Map<String, Set<Pom>> pomsForGa = new HashMap<>();
    private Map<String, Pom> pomForGav = new HashMap<>();

    public synchronized void add(Pom pom) {
        var gav = pom.toGAV();
        if (hasGAV(gav)) {
            remove(pom, gav);
        }
        pomForGav.put(gav, pom);
        put(pomsForGa, pom.toGA(), pom);
    }

    private void remove(Pom pom, String gav) {
        pomForGav.remove(gav);
        var it = pomsForGa.get(pom.toGA()).iterator();
        while (it.hasNext()) {
            var pom2 = it.next();
            if (hasEqualGAV(pom, pom2)) {
                it.remove();
            }
        }
    }

    private static boolean hasEqualGAV(Pom a, Pom b) {
        if (a.artifactId.equals(b.artifactId)) {
            if (a.groupId.equals(b.groupId)) {
                if (a.version.equals(b.version)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasGAV(String gav) {
        return pomForGav.containsKey(gav);
    }

    private static <K, V> void put(Map<K, Set<V>> map, K key, V value) {
        if (map.containsKey(key)) {
            map.get(key).add(value);
        } else {
            var values = new HashSet<V>();
            values.add(value);
            map.put(key, values);
        }
    }

    // no need for `synchronized`, the problematic part has been moved to `findGA`
    public Pom find(String ga, Set<VersionConstraint> vcs, long resolveAt) {

        DefaultArtifactVersion highest = null;
        Pom highestPom = null;

        for (var pom : findGA(ga)) {
            if (pom.releaseDate > resolveAt) {
                continue;
            }
            for (var vc : vcs) {
                if (vc.matches(pom.version)) {
                    var cur = new DefaultArtifactVersion(pom.version);
                    if (highest == null) {
                        highest = cur;
                        highestPom = pom;
                    } else {
                        if (cur.compareTo(highest) > 0) {
                            highest = cur;
                            highestPom = pom;
                        }
                    }
                }
            }
        }
        return highestPom;
    }

    private synchronized Set<Pom> findGA(String ga) {
        return pomsForGa.getOrDefault(ga, Set.of());
    }
}