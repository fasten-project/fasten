/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.core.praezi;

import eu.fasten.core.data.Dependency;
import eu.fasten.core.data.Function;
import eu.fasten.core.data.Package;
import eu.fasten.core.data.PackageVersion;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Crates {


    static final String CRATES_INDEX = "https://github.com/rust-lang/crates.io-index";
    static final String INDEX_DIR = getUsersHomeDir() + File.separator + "fasten/rust/index";
    static final String REV_ID = "b76c5ac";
    static final String CG_STORE = "/Users/jhejderup/Desktop/fastengraphs";

    static {
        //IMPORTANT: cargo build https://github.com/jhejderup/semver-jni-rs and set the java.lib.path to it
        // to make it compile
        System.loadLibrary("semver_jni_rs");
    }

    private Repository repo;
    private List<PackageVersion> packageVersions;
    // package name -> [(dependent, version, constraint)]
    private HashMap<String, List<DependencyConstraint>> dependents;
    private HashMap<String, List<String>> releases;

    public Crates() {
        var indexDir = new File(INDEX_DIR);

        if (!indexDir.exists()) {
            try {
                var git = Git.cloneRepository()
                        .setURI(CRATES_INDEX)
                        .setDirectory(indexDir)
                        .call();
                git.checkout().setName(REV_ID).call();
                System.out.println("Successfully cloned the index and set to revision " + REV_ID);
            } catch (GitAPIException e) {
                System.err.println("Error Cloning index at path " + CRATES_INDEX + " : " + e.getMessage());
            }
        }
        var repositoryBuilder = new FileRepositoryBuilder();
        repositoryBuilder.setGitDir(indexDir);
        try {
            this.repo = repositoryBuilder.build();
            System.out.println("Successfully loaded the index!");
            this.packageVersions = parsePackageVersions();
            System.out.println("Successfully parsed the index!");

        } catch (IOException e) {
            System.err.println("Could not load the index at path " + CRATES_INDEX + " : " + e.getMessage());
        }

    }

    public static native String resolve(String req, String versions);

    private static String getUsersHomeDir() {
        var users_home = System.getProperty("user.home");
        return users_home.replace("\\", "/"); // to support all platforms.
    }

    public List<PackageVersion> getPackageVersions() {
        return this.packageVersions;
    }

    public HashMap<String, List<DependencyConstraint>> getDependents() {
        return this.dependents;
    }

    public HashMap<String, List<String>> getReleases() {
        return this.releases;
    }

    private boolean hasCallgraph(SimplePackageVersion pkg) {

        return new File(CG_STORE + File.separator + pkg.name + File.separator + pkg.version + File.separator + "callgraph.unmangled.graph").isFile();

    }


    public CallGraph createFastenGraph(HashMap<SimplePackageVersion, List<SimplePackageVersion>> map) {
        //fastengraph
        var fnodes = new HashMap<String, String>();
        var fedges = new HashMap<String, ArrayList<String>>();

        //Clone the map
        var mapCopy = map.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue));
        var hmapCopy = new HashMap<>(mapCopy);
        //Remove keys without call graphs
        hmapCopy.entrySet().removeIf(e -> !hasCallgraph(e.getKey()));
        //Remove values without call graphs
        hmapCopy.entrySet().forEach(e -> e.getValue().removeIf(v -> !hasCallgraph(v)));
        //Graph with call graphs
        hmapCopy.forEach((k, v) -> System.out.println("key: " + k + ", value: " + v));

//        hmapCopy.forEach((k, v) -> {
//            try {
//                fnodes.putAll(k.getCallGraph().nodes);
//                var mergedMap = Stream.of(fedges, k.getCallGraph().edges)
//                        .flatMap(m -> m.entrySet().stream())
//                        .collect(Collectors.toMap(
//                                Map.Entry::getKey,
//                                Map.Entry::getValue,
//                                (v1, v2) ->
//                                        Stream.concat(v1.stream(), v2.stream()).distinct().collect(Collectors.toList())));
//
//            } catch (IOException e) {
//            }
//        });


        return new CallGraph(fnodes, fedges);

    }

    //TODO: should add nodes without outgoing edges
    public HashMap<SimplePackageVersion, List<SimplePackageVersion>> createDependentGraph(String pkg, String version) {
        var graph = new HashMap<SimplePackageVersion, List<SimplePackageVersion>>();
        var releasesString = String.join(",", getReleases().get(pkg));
        var visited = new HashSet<DependencyConstraint>();
        var lst = getDependents().get(pkg);


        while (lst != null && lst.size() > 0) {
            var x = lst.remove(0);
            if (!visited.contains(x)) {
                visited.add(x);
                String res = resolve(x.versionConstraint, releasesString);

                if (res.equals(version)) {
                    var key = new SimplePackageVersion(pkg, version);
                    var value = new SimplePackageVersion(x.pkg, x.version);
                    if (graph.containsKey(key)) {
                        graph.get(key).add(value);
                    } else {
                        var l = new ArrayList<SimplePackageVersion>();
                        l.add(value);
                        graph.put(key, l);
                    }
                    var subgraph = createDependentGraph(x.pkg, x.version);

                    var mergedMap = Stream.of(graph, subgraph)
                            .flatMap(map -> map.entrySet().stream())
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue,
                                    (v1, v2) ->
                                            Stream.concat(v1.stream(), v2.stream()).distinct().collect(Collectors.toList())));
                    graph = new HashMap<>(mergedMap);

                }
            }
        }
        return graph;
    }

    private List<PackageVersion> parsePackageVersions() {

        dependents = new HashMap<>();
        releases = new HashMap<>();

        try (Stream<Path> paths = Files.walk(Paths.get(INDEX_DIR))) {

            var idxEntries = paths.filter(it -> !(it.toString().contains(".DS_Store") || it.toString().contains(".git") || it.toString().contains("config.json")))
                    .filter(Files::isRegularFile)
                    .flatMap(file -> {
                        try {
                            return Files.lines(file);
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    }).map(JSONObject::new).toArray(JSONObject[]::new);


            return Stream.of(idxEntries)
                    .map(obj -> {
                        var pkg = new Package("cratesio", obj.getString("name"));
                        var depz = new HashSet<Dependency>();
                        var fns = Collections.<Function>emptySet();

                        if (!releases.containsKey(obj.getString("name"))) {
                            var lst = new ArrayList<String>();
                            lst.add(obj.getString("vers"));
                            releases.put(obj.getString("name"), lst);
                        } else {
                            releases.get(obj.getString("name")).add(obj.getString("vers"));
                        }
                        obj.getJSONArray("deps")
                                .forEach(item -> {
                                    var o = (JSONObject) item;
                                    depz.add(new Dependency(new Package("cratesio", o.getString("name")), o.getString("req")));
                                    var depConstraint = new DependencyConstraint(obj.getString("name"), obj.getString("vers"), o.getString("req"));
                                    if (!dependents.containsKey(o.getString("name"))) {
                                        var lst = new ArrayList<DependencyConstraint>();
                                        lst.add(depConstraint);
                                        dependents.put(o.getString("name"), lst);
                                    } else {
                                        dependents.get(o.getString("name")).add(depConstraint);
                                    }
                                });
                        return new PackageVersion(pkg, obj.getString("vers"), new Date(), depz, fns);
                    }).collect(Collectors.toList());


        } catch (IOException e) {
            System.err.println("Could not read index files at " + CRATES_INDEX + " : " + e.getMessage());
        }
        return new ArrayList<PackageVersion>();
    }


}
