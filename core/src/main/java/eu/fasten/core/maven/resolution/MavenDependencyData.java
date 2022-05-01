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

import eu.fasten.core.maven.data.GA;
import eu.fasten.core.maven.data.GAV;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyData {

    private Map<GA, Set<Pom>> pomsForGa = new HashMap<>();
    private Map<GAV, Pom> pomForGav = new HashMap<>();

    public synchronized void add(Pom pom) {
        var gav = pom.toGAV();
        pomForGav.put(gav, pom);
        put(pomsForGa, pom.toGA(), pom);
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
    public Pom find(GA ga, Set<VersionConstraint> vcs, long resolveAt) {

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

    protected synchronized Set<Pom> findGA(GA ga) {
        return pomsForGa.getOrDefault(ga, Set.of());
    }

    public synchronized void removeOutdatedPomRegistrations() {
        var registered = new HashSet<>(pomForGav.values());
        for (var poms : pomsForGa.values()) {
            var it = poms.iterator();
            while (it.hasNext()) {
                var pom = it.next();
                if (!registered.contains(pom)) {
                    System.out.printf("Cleaning-up %s ...\n", pom.toCoordinate());
                    it.remove();
                }
            }
        }
    }
}