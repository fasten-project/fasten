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

import java.security.InvalidParameterException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import eu.fasten.core.maven.data.PomAnalysisResult;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenDependencyGraph {

    private Map<String, Set<PomAnalysisResult>> pomsForGa = new HashMap<>();
    private Map<String, PomAnalysisResult> pomForGav = new HashMap<>();

    public synchronized void add(PomAnalysisResult pom) {
        var gav = toGAV(pom);
        if (hasGAV(gav)) {
            // TODO might be too strict for practice (e.g., same GAV in multiple repos )
            throw new InvalidParameterException("GAV exists");
        }
        pomForGav.put(gav, pom);
        put(pomsForGa, toGA(pom), pom);
    }

    public synchronized boolean hasGAV(String gav) {
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

    public PomAnalysisResult find(String ga, Set<VersionConstraint> vcs, long timestamp) {

        DefaultArtifactVersion highest = null;
        PomAnalysisResult highestPom = null;

        for (var pom : findGA(ga)) {
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

    private synchronized Set<PomAnalysisResult> findGA(String ga) {
        return pomsForGa.getOrDefault(ga, Set.of());
    }

    public PomAnalysisResult find(String gav) {
        var parts = gav.split(":");
        var ga = parts[0] + ":" + parts[1];
        var vc = new VersionConstraint(parts[2]);
        return find(ga, Set.of(vc), new Date().getTime());
    }

    private static String toGA(PomAnalysisResult pom) {
        return String.format("%s:%s", pom.groupId, pom.artifactId);
    }

    private static String toGAV(PomAnalysisResult pom) {
        return String.format("%s:%s:%s", pom.groupId, pom.artifactId, pom.version);
    }
}