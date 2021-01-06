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

import eu.fasten.analyzer.repoclonerplugin.utils.GitCloner;
import eu.fasten.analyzer.repoclonerplugin.utils.HgCloner;
import eu.fasten.analyzer.repoclonerplugin.utils.SvnCloner;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.svn.core.SVNException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

public class RepoClonerPluginTest {

    private RepoClonerPlugin.RepoCloner repoCloner;
    private String baseDir;

    @BeforeEach
    public void setUp() throws IOException {
        repoCloner = new RepoClonerPlugin.RepoCloner();
        baseDir = Files.createTempDirectory("").toString();
        repoCloner.setBaseDir(baseDir);
        repoCloner.setTopic("fasten.POMAnalyzer.out");
    }

    @AfterEach
    public void teardown() throws IOException {
        FileUtils.deleteDirectory(Path.of(baseDir).toFile());
    }

    @Test
    public void consumeTest() {
        var json = new JSONObject("{\n" +
                "\t\"payload\": {\n" +
                "\t\t\"repoUrl\": \"https://github.com/fasten-project/fasten.git\",\n" +
                "\t\t\"artifactId\": \"fasten\",\n" +
                "\t\t\"groupId\": \"fasten-project\",\n" +
                "\t\t\"version\": \"1\",\n" +
                "\t\t\"commitTag\": \"123\",\n" +
                "\t\t\"sourcesUrl\": \"someURL\",\n" +
                "\t\t\"forge\": \"mvn\"\n" +
                "\t}\n" +
                "}");
        repoCloner.consume(json.toString());
        var repoPath = Paths.get(baseDir, "f", "fasten-project", "fasten").toAbsolutePath().toString();
        var expected = new JSONObject("{\n" +
                "\t\"artifactId\": \"fasten\",\n" +
                "\t\"groupId\": \"fasten-project\",\n" +
                "\t\"version\": \"1\",\n" +
                "\t\"repoPath\": \"" + repoPath + "\",\n" +
                "\t\"commitTag\": \"123\",\n" +
                "\t\"sourcesUrl\": \"someURL\",\n" +
                "\t\"repoUrl\": \"https://github.com/fasten-project/fasten.git\",\n" +
                "\t\"date\": -1,\n" +
                "\t\"repoType\": \"git\",\n" +
                "\t\"forge\": \"mvn\"\n" +
                "}").toString();
        var actual = repoCloner.produce().isPresent() ? repoCloner.produce().get() : null;
        assertEquals(expected, actual);
        assertTrue(new File(repoPath).exists());
        assertTrue(new File(repoPath).isDirectory());
    }

    @Test
    public void consumeOnlyCoordinateTest() {
        var json = new JSONObject("{\n" +
                "\t\"payload\": {\n" +
                "\t\t\"artifactId\": \"fasten\",\n" +
                "\t\t\"groupId\": \"fasten-project\",\n" +
                "\t\t\"version\": \"1\",\n" +
                "\t\t\"forge\": \"mvn\"\n" +
                "\t}\n" +
                "}");
        repoCloner.consume(json.toString());
        var expected = new JSONObject("{\n" +
                "\t\"artifactId\": \"fasten\",\n" +
                "\t\"groupId\": \"fasten-project\",\n" +
                "\t\"version\": \"1\",\n" +
                "\t\"repoPath\": \"\",\n" +
                "\t\"commitTag\": \"\",\n" +
                "\t\"sourcesUrl\": \"\",\n" +
                "\t\"repoUrl\": \"\",\n" +
                "\t\"date\": -1,\n" +
                "\t\"repoType\": \"\",\n" +
                "\t\"forge\": \"mvn\"\n" +
                "}").toString();
        var actual = repoCloner.produce().isPresent() ? repoCloner.produce().get() : null;
        assertEquals(expected, actual);
    }

    @Test
    public void cloneHgRepoTest() throws IOException, CancelledException, HgException {
        var gitCloner = Mockito.mock(GitCloner.class);
        var hgCloner = Mockito.mock(HgCloner.class);
        var svnCloner = Mockito.mock(SvnCloner.class);
        Mockito.when(hgCloner.cloneRepo("https://testurl.com", "name", "owner")).thenReturn("test/path");
        var result = repoCloner.cloneRepo("https://testurl.com", "name", "owner", gitCloner, hgCloner, svnCloner);
        assertEquals("test/path", result.getFirst());
    }

    @Test
    public void cloneGitRepoTest() throws GitAPIException, IOException {
        var gitCloner = Mockito.mock(GitCloner.class);
        var hgCloner = Mockito.mock(HgCloner.class);
        var svnCloner = Mockito.mock(SvnCloner.class);
        Mockito.when(gitCloner.cloneRepo(Mockito.anyString(), Mockito.eq("name"), Mockito.eq("owner"))).thenReturn("test/path");
        var result = repoCloner.cloneRepo("https://testurl.com/repo.git", "name", "owner", gitCloner, hgCloner, svnCloner);
        assertEquals("test/path", result.getFirst());
    }

    @Test
    public void cloneSvnRepoTest() throws SVNException {
        var gitCloner = Mockito.mock(GitCloner.class);
        var hgCloner = Mockito.mock(HgCloner.class);
        var svnCloner = Mockito.mock(SvnCloner.class);
        Mockito.when(svnCloner.cloneRepo(Mockito.anyString(), Mockito.eq("name"), Mockito.eq("owner"))).thenReturn("test/path");
        var result = repoCloner.cloneRepo("svn://testurl.com/repo", "name", "owner", gitCloner, hgCloner, svnCloner);
        assertEquals("test/path", result.getFirst());
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.POMAnalyzer.out"));
        assertEquals(topics, repoCloner.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.POMAnalyzer.out"));
        assertEquals(topics1, repoCloner.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        repoCloner.setTopic(differentTopic);
        assertEquals(topics2, repoCloner.consumeTopic());
    }

    @Test
    public void nameTest() {
        var name = "Repo Cloner Plugin";
        assertEquals(name, repoCloner.name());
    }

    @Test
    public void descriptionTest() {
        var description = "Repo Cloner Plugin. "
                + "Consumes a repository URL, "
                + "clones the repo to the provided directory building directory hierarchy"
                + "and produces the path to directory with repository.";
        assertEquals(description, repoCloner.description());
    }

    @Test
    public void versionTest() {
        var version = "0.1.2";
        assertEquals(version, repoCloner.version());
    }
}
