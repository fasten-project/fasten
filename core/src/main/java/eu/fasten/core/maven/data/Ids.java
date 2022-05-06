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
package eu.fasten.core.maven.data;

import java.util.HashMap;
import java.util.Map;

public class Ids {

    private static final Map<String, String> GIDS = new HashMap<>();
    private static final Map<String, String> AIDS = new HashMap<>();
    private static final Map<String, String> VERSIONS = new HashMap<>();
    private static final Map<VersionConstraint, VersionConstraint> VCS = new HashMap<>();
    private static final Map<Dependency, Dependency> DEPS = new HashMap<>();
    private static final Map<GAV, GAV> GAVS = new HashMap<>();
    private static final Map<GA, GA> GAS = new HashMap<>();

    public static String gid(String gid) {
        if (GIDS.containsKey(gid)) {
            return GIDS.get(gid);
        } else {
            GIDS.put(gid, gid);
            return gid;
        }
    }

    public static String aid(String aid) {
        if (AIDS.containsKey(aid)) {
            return AIDS.get(aid);
        } else {
            AIDS.put(aid, aid);
            return aid;
        }
    }

    public static String version(String version) {
        if (VERSIONS.containsKey(version)) {
            return VERSIONS.get(version);
        } else {
            VERSIONS.put(version, version);
            return version;
        }
    }

    public static Dependency dep(Dependency d) {
        if (DEPS.containsKey(d)) {
            return DEPS.get(d);
        } else {
            DEPS.put(d, d);
            return d;
        }
    }

    public static GA ga(GA ga) {
        if (GAS.containsKey(ga)) {
            return GAS.get(ga);
        } else {
            GAS.put(ga, ga);
            return ga;
        }
    }

    public static GAV gav(GAV gav) {
        if (GAVS.containsKey(gav)) {
            return GAVS.get(gav);
        } else {
            GAVS.put(gav, gav);
            return gav;
        }
    }

    public static VersionConstraint versionConstraint(VersionConstraint vc) {
        if (VCS.containsKey(vc)) {
            return VCS.get(vc);
        } else {
            VCS.put(vc, vc);
            return vc;
        }
    }
}