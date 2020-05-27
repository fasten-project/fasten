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

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
                "\t\t\"artifact\": \"junit\",\n" +
                "\t\t\"group\": \"junit\",\n" +
                "\t\t\"version\": \"4.11\",\n" +
                "\t\t\"repoUrl\": \"https://github.com/junit-team/junit4.git\",\n" +
                "\t\t\"jarUrl\": \"https://search.maven.org/remotecontent?filepath=junit/junit/4.11/junit-4.11-sources.jar\"\n" +
                "\t}\n" +
                "}");
        repoCloner.consume(json.toString());
        var repoPath = Paths.get(baseDir, "mvn", "j", "junit").toAbsolutePath().toString();
        var jarPath = Paths.get(baseDir, "mvn", "j", "junit-junit-4.11", "junit-junit-4.11.jar").toAbsolutePath().toString();
        var expected = new JSONObject("{\n" +
                "\t\"artifact\": \"junit\",\n" +
                "\t\"group\": \"junit\",\n" +
                "\t\"version\": \"4.11\",\n" +
                "\t\"repoPath\": \"" + repoPath + "\",\n" +
                "\t\"jarPath\": \"" + jarPath + "\"\n" +
                "}").toString();
        var actual = repoCloner.produce().isPresent() ? repoCloner.produce().get() : null;
        assertEquals(expected, actual);
        assertTrue(new File(repoPath).exists());
        assertTrue(new File(repoPath).isDirectory());
        assertTrue(new File(jarPath).exists());
        assertTrue(new File(jarPath).isFile());
    }

    @Test
    public void consumeWithoutJarTest() {
        var json = new JSONObject("{\n" +
                "\t\"payload\": {\n" +
                "\t\t\"artifact\": \"junit\",\n" +
                "\t\t\"group\": \"junit\",\n" +
                "\t\t\"version\": \"4.11\",\n" +
                "\t\t\"repoUrl\": \"https://github.com/junit-team/junit4.git\"\n" +
                "\t}\n" +
                "}");
        repoCloner.consume(json.toString());
        var repoPath = Paths.get(baseDir, "mvn", "j", "junit").toAbsolutePath().toString();
        var jarPath = Paths.get(baseDir, "mvn", "j", "junit-junit-4.11", "junit-junit-4.11.jar").toAbsolutePath().toString();
        var expected = new JSONObject("{\n" +
                "\t\"artifact\": \"junit\",\n" +
                "\t\"group\": \"junit\",\n" +
                "\t\"version\": \"4.11\",\n" +
                "\t\"repoPath\": \"" + repoPath + "\"\n" +
                "}").toString();
        var actual = repoCloner.produce().isPresent() ? repoCloner.produce().get() : null;
        assertEquals(expected, actual);
        assertTrue(new File(repoPath).exists());
        assertTrue(new File(repoPath).isDirectory());
        assertFalse(new File(jarPath).exists());
    }

    @Test
    public void consumeWithoutRepoTest() {
        var json = new JSONObject("{\n" +
                "\t\"payload\": {\n" +
                "\t\t\"artifact\": \"junit\",\n" +
                "\t\t\"group\": \"junit\",\n" +
                "\t\t\"version\": \"4.11\",\n" +
                "\t\t\"jarUrl\": \"https://search.maven.org/remotecontent?filepath=junit/junit/4.11/junit-4.11-sources.jar\"\n" +
                "\t}\n" +
                "}");
        repoCloner.consume(json.toString());
        var repoPath = Paths.get(baseDir, "mvn", "j", "junit").toAbsolutePath().toString();
        var jarPath = Paths.get(baseDir, "mvn", "j", "junit-junit-4.11", "junit-junit-4.11.jar").toAbsolutePath().toString();
        var expected = new JSONObject("{\n" +
                "\t\"artifact\": \"junit\",\n" +
                "\t\"group\": \"junit\",\n" +
                "\t\"version\": \"4.11\",\n" +
                "\t\"jarPath\": \"" + jarPath + "\"\n" +
                "}").toString();
        var actual = repoCloner.produce().isPresent() ? repoCloner.produce().get() : null;
        assertEquals(expected, actual);
        assertFalse(new File(repoPath).exists());
        assertTrue(new File(jarPath).exists());
        assertTrue(new File(jarPath).isFile());
    }

    @Test
    public void consumeOnlyCoordinateTest() {
        var json = new JSONObject("{\n" +
                "\t\"payload\": {\n" +
                "\t\t\"artifact\": \"junit\",\n" +
                "\t\t\"group\": \"junit\",\n" +
                "\t\t\"version\": \"4.11\"\n" +
                "\t}\n" +
                "}");
        repoCloner.consume(json.toString());
        var repoPath = Paths.get(baseDir, "mvn", "j", "junit").toAbsolutePath().toString();
        var jarPath = Paths.get(baseDir, "mvn", "j", "junit-junit-4.11", "junit-junit-4.11.jar").toAbsolutePath().toString();
        var expected = new JSONObject("{\n" +
                "\t\"artifact\": \"junit\",\n" +
                "\t\"group\": \"junit\",\n" +
                "\t\"version\": \"4.11\"\n" +
                "}").toString();
        var actual = repoCloner.produce().isPresent() ? repoCloner.produce().get() : null;
        assertEquals(expected, actual);
        assertFalse(new File(repoPath).exists());
        assertFalse(new File(jarPath).exists());
    }

    @Test
    public void produceWithoutConsumeTest() {
        var optionalResult = repoCloner.produce();
        assertTrue(optionalResult.isEmpty());
    }

    @Test
    public void cloneRepoTest() throws GitAPIException, IOException {
        var gitCloner = Mockito.mock(GitCloner.class);
        Mockito.when(gitCloner.cloneRepo(Mockito.anyString(), Mockito.anyString())).thenReturn("test/path");
        repoCloner.cloneRepo("test", "https://testurl.com", gitCloner);
        assertEquals("test/path", repoCloner.getRepoPath());
    }

    @Test
    public void downloadJarTest() throws IOException {
        var jarDownloader = Mockito.mock(JarDownloader.class);
        Mockito.when(jarDownloader.downloadJarFile(Mockito.anyString(), Mockito.anyString())).thenReturn("test/path/sources.jar");
        repoCloner.downloadJar("test", "https://testurl.com/sources.jar", jarDownloader);
        assertEquals("test/path/sources.jar", repoCloner.getJarPath());
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
                + "Consumes GitHub repository URL and JAR file URL (if present), "
                + "clones the repo and downloads the JAR file to the provided directory "
                + "and produces path to directory with repository and path to JAR file.";
        assertEquals(description, repoCloner.description());
    }

    @Test
    public void versionTest() {
        var version = "0.0.1";
        assertEquals(version, repoCloner.version());
    }
}
