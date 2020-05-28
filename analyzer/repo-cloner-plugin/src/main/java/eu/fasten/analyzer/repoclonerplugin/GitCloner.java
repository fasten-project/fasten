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

package eu.fasten.analyzer.repoclonerplugin;

import java.io.IOException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

public class GitCloner {

    private final String baseDir;

    public GitCloner(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Clones git repository from provided URL into hierarchical directory structure.
     *
     * @param artifact Name of the repository
     * @param repoUrl  URL at which the repository is located
     * @return Path to directory to which the repository was cloned
     * @throws GitAPIException if there was an error when cloning repository
     * @throws IOException     if could not create a directory for repository
     */
    public String cloneRepo(String artifact, String repoUrl) throws GitAPIException, IOException {
        if (repoUrl.endsWith("/")) {
            repoUrl = repoUrl.substring(0, repoUrl.length() - 1);
        }
        if (!repoUrl.endsWith(".git")) {
            repoUrl += ".git";
        }
        var dirHierarchy = new DirectoryHierarchyBuilder(baseDir);
        var dir = dirHierarchy.getDirectoryFromHierarchy(artifact);
        if (dir.exists()) {
            Git.open(dir).pull().call();
        } else {
            Git.cloneRepository().setURI(repoUrl).setDirectory(dir).call();
        }
        return dir.getAbsolutePath();
    }
}
