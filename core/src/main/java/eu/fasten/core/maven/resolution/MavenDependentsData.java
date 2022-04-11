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
import java.util.stream.Collectors;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;

public class MavenDependentsData {

    private final Map<String, Pom> pomForGAV = new HashMap<>();
    private final Map<String, Set<Pom>> dependentsForGA = new HashMap<>();

    public synchronized void add(Pom pom) {
        var gav = toGAV(pom);
        pomForGAV.put(gav, pom);
        for (var dep : pom.dependencies) {
            var depGA = toGA(dep);
            addDependent(depGA, pom);
        }
    }

    private void addDependent(String depGA, Pom pom) {
        Set<Pom> poms;
        if (!dependentsForGA.containsKey(depGA)) {
            poms = new HashSet<Pom>();
            dependentsForGA.put(depGA, poms);
        } else {
            poms = dependentsForGA.get(depGA);
            var it = poms.iterator();
            while (it.hasNext()) {
                var pom2 = it.next();
                if (pom.toCoordinate().equals(pom2.toCoordinate())) {
                    it.remove();
                }
            }
        }
        poms.add(pom);
    }

    public synchronized Pom findPom(String gav, long resolveAt) {
        var pom = pomForGAV.get(gav);
        if (pom != null && pom.releaseDate <= resolveAt) {
            return pom;
        }
        return null;
    }

    public synchronized Set<Pom> findPotentialDependents(String ga, long resolveAt) {
        var dpds = dependentsForGA.getOrDefault(ga, Set.of());
        return dpds.stream() //
                .filter(d -> d.releaseDate <= resolveAt) //
                .collect(Collectors.toSet());
    }

    private static String toGA(Dependency dep) {
        return String.format("%s:%s", dep.groupId, dep.artifactId);
    }

    private static String toGAV(Pom pom) {
        return String.format("%s:%s:%s", pom.groupId, pom.artifactId, pom.version);
    }
}