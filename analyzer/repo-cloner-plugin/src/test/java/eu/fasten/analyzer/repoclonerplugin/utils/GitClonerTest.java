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

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GitClonerTest {

    private GitCloner gitCloner;
    private String baseDir;

    @BeforeEach
    public void setup() throws IOException {
        this.baseDir = Files.createTempDirectory("").toString();
        this.gitCloner = new GitCloner(baseDir);
    }

    @AfterEach
    public void teardown() throws IOException {
        FileUtils.deleteDirectory(Path.of(baseDir).toFile());
    }

    @Test
    public void cloneRepoTest() throws GitAPIException, IOException {
        var repo = Path.of(baseDir, "f/fasten-project/fasten").toFile();
        var path = gitCloner.cloneRepo("https://github.com/fasten-project/fasten.git", "fasten", "fasten-project");
        Assertions.assertEquals(repo.getAbsolutePath(), path);
        Assertions.assertTrue(repo.exists());
        Assertions.assertTrue(repo.isDirectory());
    }

    @Test
    public void cloneRepoWithoutExtensionTest() throws GitAPIException, IOException {
        var repo = Path.of(baseDir, "f/fasten-project/fasten").toFile();
        var path = gitCloner.cloneRepo("https://github.com/fasten-project/fasten", "fasten", "fasten-project");
        Assertions.assertEquals(repo.getAbsolutePath(), path);
        Assertions.assertTrue(repo.exists());
        Assertions.assertTrue(repo.isDirectory());
    }

    @Test
    public void cloneRepoWithoutExtensionWithSlashTest() throws GitAPIException, IOException {
        var repo = Path.of(baseDir, "f/fasten-project/fasten").toFile();
        var path = gitCloner.cloneRepo("https://github.com/fasten-project/fasten/", "fasten", "fasten-project");
        Assertions.assertEquals(repo.getAbsolutePath(), path);
        Assertions.assertTrue(repo.exists());
        Assertions.assertTrue(repo.isDirectory());
    }

    @Test
    public void cloneRepoWithBranchTest() throws GitAPIException, IOException {
        var repo = Path.of(baseDir, "f/fasten-project/fasten").toFile();
        var path = gitCloner.cloneRepo("https://github.com/fasten-project/fasten/tree/master/", "fasten", "fasten-project");
        Assertions.assertEquals(repo.getAbsolutePath(), path);
        Assertions.assertTrue(repo.exists());
        Assertions.assertTrue(repo.isDirectory());
    }

    @Test
    public void cloneRepoWithSshTest() throws GitAPIException, IOException {
        var repo = Path.of(baseDir, "d/delors/opal").toFile();
        var path = gitCloner.cloneRepo("git@bitbucket.org:delors/opal.git", "opal", "delors");
        Assertions.assertEquals(repo.getAbsolutePath(), path);
        Assertions.assertTrue(repo.exists());
        Assertions.assertTrue(repo.isDirectory());
    }
}
