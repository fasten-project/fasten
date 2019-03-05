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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;

public class Crates {
    static final String CRATES_INDEX = "https://github.com/rust-lang/crates.io-index";
    static final String INDEX_DIR = "fasten/rust/index";
    static final String REV_ID = "b76c5ac";
    private Repository repo;

    private static String getUsersHomeDir() {
        var users_home = System.getProperty("user.home");
        return users_home.replace("\\", "/"); // to support all platforms.
    }

    public Crates() {
        var indexPath = getUsersHomeDir() + File.separator + INDEX_DIR ;
        var indexDir = new File(indexPath);
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
            repo = repositoryBuilder.build();
            System.out.println("Successfully loaded the index!");
        } catch (IOException e) {
            System.err.println("Could not load the index at path " + CRATES_INDEX + " : " + e.getMessage());
        }

    }


}
