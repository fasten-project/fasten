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

package eu.fasten.analyzer.repoclonerplugin.utils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
     * @param repoUrl URL at which the repository is located
     * @return Path to directory to which the repository was cloned
     * @throws GitAPIException if there was an error when cloning repository
     * @throws IOException     if could not create a directory for repository
     */
    public String cloneRepo(String repoUrl) throws GitAPIException, IOException {
        if (repoUrl.contains("github.com/")) {
            return this.cloneGithubRepo(repoUrl);
        } else {
            var dirHierarchy = new DirectoryHierarchyBuilder(baseDir);
            var urlParts = repoUrl.split("/");
            var repoOwner = urlParts[1];
            StringBuilder repoName = new StringBuilder();
            for (var i = 2; i < urlParts.length; i++) {
                repoName.append(urlParts[i]);
                if (i < urlParts.length - 1) {
                    repoName.append("/");
                }
            }
            var dir = dirHierarchy.getDirectoryFromHierarchy(repoOwner, repoName.toString());
            Git.cloneRepository().setURI(repoUrl).setDirectory(dir).call();
            return dir.getAbsolutePath();
        }
    }

    private String cloneGithubRepo(String repoUrl) throws GitAPIException, IOException {
        if (repoUrl.endsWith("/")) {
            repoUrl = repoUrl.substring(0, repoUrl.length() - 1);
        }
        var urlParts = repoUrl.split("/");
        String branch = null;
        if (urlParts.length > 2 && urlParts[urlParts.length - 2].equals("tree")) {
            branch = urlParts[urlParts.length - 1];
            repoUrl = String.join("/", Arrays.copyOf(urlParts, urlParts.length - 2));
        }
        urlParts = repoUrl.split("/");
        if (!repoUrl.endsWith(".git")) {
            repoUrl += ".git";
        }
        var repoName = urlParts[urlParts.length - 1].split(".git")[0];
        var repoOwner = urlParts[urlParts.length - 2];
        var dirHierarchy = new DirectoryHierarchyBuilder(baseDir);
        var dir = dirHierarchy.getDirectoryFromHierarchy(repoOwner, repoName);
        if (dir.exists()) {
            var pull = Git.open(dir).pull();
            if (branch != null) {
                pull = pull.setRemoteBranchName(branch);
            }
            pull.call();
        } else {
            var clone = Git.cloneRepository().setURI(repoUrl).setDirectory(dir);
            if (branch != null) {
                clone = clone.setBranch(branch);
            }
            clone.call();
        }
        return dir.getAbsolutePath();
    }
}
