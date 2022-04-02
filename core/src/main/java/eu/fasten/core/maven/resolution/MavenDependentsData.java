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

import org.slf4j.Logger;

import eu.fasten.core.maven.data.MavenProduct;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.Revision;

public class MavenDependentsData {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(MavenDependentsData.class);

    // last kafka offset that is represented
    public long lastOffset = -1;

    public final Map<Revision, Pom> pomForRevision = new HashMap<>();
//    public final Map<MavenProduct, Set<PomAnalysisResultX>> pomsForProduct = new HashMap<>();
    // all entries in the value set depend on a version of the key
    public final Map<MavenProduct, Set<Pom>> dependentsForProduct = new HashMap<>();

    public void add(Pom pom) {

        pomForRevision.put(pom.toRevision(), pom);

//        var prod = pom.toProduct();
//        add(pomsForProduct, prod, pom);

        for (var abstractDep : pom.dependencies) {
            var depProduct = abstractDep.product();
            add(dependentsForProduct, depProduct, pom);
        }
    }

    private static <K, V> void add(Map<K, Set<V>> map, K key, V val) {
        if (map.containsKey(key)) {
            map.get(key).add(val);
        } else {
            var set = new HashSet<V>();
            set.add(val);
            map.put(key, set);
        }
    }
}