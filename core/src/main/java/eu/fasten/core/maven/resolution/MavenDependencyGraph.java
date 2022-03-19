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

import eu.fasten.core.maven.data.PomAnalysisResult;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyGraph {

    public Map<String, Set<PomAnalysisResult>> pomForGa = new HashMap<>();
    public Map<String, PomAnalysisResult> pomForGav = new HashMap<>();

    public void add(PomAnalysisResult pom) {
        put(pomForGa, toGA(pom), pom);
        pomForGav.put(toGAV(pom), pom);
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

    public PomAnalysisResult find(String ga, Set<VersionConstraint> vcs) {
        for (var pom : pomForGa.getOrDefault(ga, Set.of())) {
            for (var vc : vcs) {
                if (vc.matches(pom.version)) {
                    return pom;
                }
            }
        }
        return null;
    }

    private static String toGA(PomAnalysisResult pom) {
        return String.format("%s:%s", pom.groupId, pom.artifactId);
    }

    private static String toGAV(PomAnalysisResult pom) {
        return String.format("%s:%s:%s", pom.groupId, pom.artifactId, pom.version);
    }
}