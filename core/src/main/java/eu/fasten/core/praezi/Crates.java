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


    public static native String resolve(String req, String versions);

    static {
        //IMPORTANT: cargo build https://github.com/jhejderup/semver-jni-rs and set the java.lib.path to it
        // to make it compile 
        System.loadLibrary("semver_jni_rs");
    }

    static final String CRATES_INDEX = "https://github.com/rust-lang/crates.io-index";
    static final String INDEX_DIR = getUsersHomeDir() + File.separator + "fasten/rust/index";
    static final String REV_ID = "b76c5ac";
    private Repository repo;
    private List<PackageVersion> releases;

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
            this.releases = parsePackageVersions();
            System.out.println("Successfully parsed the index!");

        } catch (IOException e) {
            System.err.println("Could not load the index at path " + CRATES_INDEX + " : " + e.getMessage());
        }

    }

    private static String getUsersHomeDir() {
        var users_home = System.getProperty("user.home");
        return users_home.replace("\\", "/"); // to support all platforms.
    }

    public List<PackageVersion> getPackageVersions() {
        return this.releases;
    }

    private List<PackageVersion> parsePackageVersions() {
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
                        obj.getJSONArray("deps")
                                .forEach(item -> {
                                    var o = (JSONObject) item;
                                    depz.add(new Dependency(new Package("cratesio", o.getString("name")), o.getString("req")));
                                });
                        return new PackageVersion(pkg, obj.getString("vers"), new Date(), depz, fns);
                    }).collect(Collectors.toList());


        } catch (IOException e) {
            System.err.println("Could not read index files at " + CRATES_INDEX + " : " + e.getMessage());
        }
        return new ArrayList<PackageVersion>();
    }


}
