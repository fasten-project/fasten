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

package eu.fasten.analyzer.pomanalyzer;

import eu.fasten.core.maven.data.DependencyData;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Collections;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class POMAnalyzerPluginTest {

    private POMAnalyzerPlugin.POMAnalyzer pomAnalyzer;

    @BeforeEach
    public void setup() {
        pomAnalyzer = new POMAnalyzerPlugin.POMAnalyzer();
        pomAnalyzer.setTopic("fasten.mvn.pkg");
    }

    @Test
    public void consumeFromDifferentRepoTest() {
        var record = new JSONObject("{" +
                "\"payload\": {" +
                "\"artifactId\": \"common\"," +
                "\"groupId\": \"android.arch.core\"," +
                "\"version\": \"1.1.1\"," +
                "\"artifactRepository\": \"https://dl.google.com/android/maven2/\"" +
                "}}").toString();
        pomAnalyzer.consume(record);
        var output = pomAnalyzer.produce();
        assertTrue(output.isPresent());
        System.out.println(output.get());
        var expected = "{\"date\":1569025448000,\"repoUrl\":\"http://source.android.com\",\"groupId\":\"android.arch.core\",\"version\":\"1.1.1\",\"parentCoordinate\":\"\",\"artifactRepository\":\"https://dl.google.com/android/maven2/\",\"forge\":\"mvn\",\"sourcesUrl\":\"https://dl.google.com/android/maven2/android/arch/core/common/1.1.1/common-1.1.1-sources.jar\",\"artifactId\":\"common\",\"dependencyData\":{\"dependencyManagement\":{\"dependencies\":[]},\"dependencies\":[{\"versionConstraints\":[{\"isUpperHardRequirement\":false,\"isLowerHardRequirement\":false,\"upperBound\":\"4.12\",\"lowerBound\":\"4.12\"}],\"groupId\":\"junit\",\"scope\":\"test\",\"classifier\":\"\",\"artifactId\":\"junit\",\"exclusions\":[],\"optional\":false,\"type\":\"\"},{\"versionConstraints\":[{\"isUpperHardRequirement\":false,\"isLowerHardRequirement\":false,\"upperBound\":\"2.7.6\",\"lowerBound\":\"2.7.6\"}],\"groupId\":\"org.mockito\",\"scope\":\"test\",\"classifier\":\"\",\"artifactId\":\"mockito-core\",\"exclusions\":[],\"optional\":false,\"type\":\"\"},{\"versionConstraints\":[{\"isUpperHardRequirement\":false,\"isLowerHardRequirement\":false,\"upperBound\":\"26.1.0\",\"lowerBound\":\"26.1.0\"}],\"groupId\":\"com.android.support\",\"scope\":\"compile\",\"classifier\":\"\",\"artifactId\":\"support-annotations\",\"exclusions\":[],\"optional\":false,\"type\":\"\"}]},\"projectName\":\"Android Arch-Common\",\"commitTag\":\"\",\"packagingType\":\"jar\"}";
        assertEquals(expected, output.get());
    }

    @Test
    public void consumeTest() {
        var record = new JSONObject("{" +
                "\"payload\": {" +
                "\"artifactId\": \"junit\"," +
                "\"groupId\": \"junit\"," +
                "\"version\": \"4.12\"" +
                "}}").toString();
        var repoUrl = "http://github.com/junit-team/junit/tree/master";
        var sourcesUrl = "https://repo.maven.apache.org/maven2/junit/junit/4.12/junit-4.12-sources.jar";
        var packagingType = "jar";
        var projectName = "JUnit";
        var dependencyData = DependencyData.fromJSON(new JSONObject("{\n" +
                "   \"dependencyManagement\":{\n" +
                "      \"dependencies\":[\n" +
                "\n" +
                "      ]\n" +
                "   },\n" +
                "   \"dependencies\":[\n" +
                "      {\n" +
                "         \"versionConstraints\":[\n" +
                "            {\n" +
                "               \"isUpperHardRequirement\":false,\n" +
                "               \"isLowerHardRequirement\":false,\n" +
                "               \"upperBound\":\"1.3\",\n" +
                "               \"lowerBound\":\"1.3\"\n" +
                "            }\n" +
                "         ],\n" +
                "         \"groupId\":\"org.hamcrest\",\n" +
                "         \"scope\":\"\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"hamcrest-core\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\"\n" +
                "      }\n" +
                "   ]\n" +
                "}"));
        pomAnalyzer.consume(record);
        var output = pomAnalyzer.produce();
        assertTrue(output.isPresent());
        var json = new JSONObject(output.get());
        assertEquals("junit", json.getString("artifactId"));
        assertEquals("junit", json.getString("groupId"));
        assertEquals("4.12", json.getString("version"));
        assertEquals(repoUrl, json.getString("repoUrl"));
        assertEquals(sourcesUrl, json.getString("sourcesUrl"));
        assertEquals(packagingType, json.getString("packagingType"));
        assertEquals(projectName, json.getString("projectName"));
        assertEquals(dependencyData, DependencyData.fromJSON(json.getJSONObject("dependencyData")));
    }

    @Test
    public void saveToDatabaseTest() {
        var metadataDao = Mockito.mock(MetadataDao.class);
        var repoUrl = "http://github.com/junit-team/junit/tree/master";
        var sourcesUrl = "https://repo.maven.apache.org/maven2/junit/junit/4.12/junit-4.12-sources.jar";
        var packagingType = "jar";
        var projectName = "JUnit";
        var dependencyData = DependencyData.fromJSON(new JSONObject("{\n" +
                "   \"dependencyManagement\":{\n" +
                "      \"dependencies\":[\n" +
                "\n" +
                "      ]\n" +
                "   },\n" +
                "   \"dependencies\":[\n" +
                "      {\n" +
                "         \"versionConstraints\":[\n" +
                "            {\n" +
                "               \"isUpperHardRequirement\":false,\n" +
                "               \"isLowerHardRequirement\":false,\n" +
                "               \"upperBound\":\"1.3\",\n" +
                "               \"lowerBound\":\"1.3\"\n" +
                "            }\n" +
                "         ],\n" +
                "         \"groupId\":\"org.hamcrest\",\n" +
                "         \"scope\":\"\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"hamcrest-core\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\"\n" +
                "      }\n" +
                "   ]\n" +
                "}"));
        var commitTag = "f8a34a";
        var artifactRepository = "maven central";
        final var packageId = 1L;
        Mockito.when(metadataDao.insertPackage("junit:junit", Constants.mvnForge, projectName, repoUrl, null))
                .thenReturn(packageId);
        final var artifactRepoId = -1L;
        Mockito.when(metadataDao.insertArtifactRepository(artifactRepository)).thenReturn(artifactRepoId);
        final var packageVersionId = 0L;
        var packageVersionMetadata = new JSONObject();
        packageVersionMetadata.put("dependencyManagement",
                dependencyData.dependencyManagement.toJSON());
        packageVersionMetadata.put("commitTag", commitTag);
        packageVersionMetadata.put("sourcesUrl", sourcesUrl);
        packageVersionMetadata.put("packagingType", packagingType);
        packageVersionMetadata.put("parentCoordinate", "");
        Mockito.when(metadataDao.insertPackageVersion(packageId, Constants.opalGenerator, "4.12", artifactRepoId, null, null, packageVersionMetadata))
                .thenReturn(packageVersionId);
        final var dependencyId = 16L;
        Mockito.when(metadataDao.insertPackage("org.hamcrest:hamcrest-core", Constants.mvnForge, null, null, null))
                .thenReturn(dependencyId);
        var result = pomAnalyzer.saveToDatabase("junit:junit", "4.12", repoUrl, commitTag, sourcesUrl, packagingType, -1, projectName, null, dependencyData, artifactRepository, metadataDao);
        assertEquals(packageVersionId, result);
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.mvn.pkg"));
        assertEquals(topics, pomAnalyzer.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.mvn.pkg"));
        assertEquals(topics1, pomAnalyzer.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        pomAnalyzer.setTopic(differentTopic);
        assertEquals(topics2, pomAnalyzer.consumeTopic());
    }

    @Test
    public void nameTest() {
        var name = "POM Analyzer plugin";
        assertEquals(name, pomAnalyzer.name());
    }

    @Test
    public void descriptionTest() {
        var description = "POM Analyzer plugin. Consumes Maven coordinate from Kafka topic, "
                + "downloads pom.xml of that coordinate and analyzes it "
                + "extracting relevant information such as dependency information "
                + "and repository URL, then inserts that data into Metadata Database "
                + "and produces it to Kafka topic.";
        assertEquals(description, pomAnalyzer.description());
    }

    @Test
    public void versionTest() {
        var version = "0.1.2";
        assertEquals(version, pomAnalyzer.version());
    }
}
