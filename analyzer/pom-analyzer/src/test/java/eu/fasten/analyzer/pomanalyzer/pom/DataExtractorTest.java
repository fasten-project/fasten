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

package eu.fasten.analyzer.pomanalyzer.pom;

import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.DependencyData;
import eu.fasten.core.maven.data.DependencyManagement;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DataExtractorTest {

    private DataExtractor dataExtractor;

    @BeforeEach
    public void setup() {
        dataExtractor = new DataExtractor(MavenUtilities.getRepos());
    }

    @Test
    public void extractReleaseDateTest() {
        var expected = 1417709863000L;
        var actual = dataExtractor.extractReleaseDate("junit", "junit", "4.12", MavenUtilities.getRepos().get(0));
        assertEquals(expected, actual);
    }

    @Test
    public void extractRepoUrlTest() {
        var expected = "http://github.com/junit-team/junit/tree/master";
        var actual = dataExtractor.extractRepoUrl("junit", "junit", "4.12");
        assertEquals(expected, actual);
    }

    @Test
    public void getMavenCoordinateTest() {
        var expected = "junit:junit:4.12";
        var actual = dataExtractor.getMavenCoordinate("https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.pom");
        assertEquals(expected, actual);

        expected = "commons-logging:commons-logging:1.2";
        actual = dataExtractor.getMavenCoordinate("https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.pom");
        assertEquals(expected, actual);

        expected = "org.apache.commons:commons-lang3:3.11";
        actual = dataExtractor.getMavenCoordinate("https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.11/commons-lang3-3.11.pom");
        assertEquals(expected, actual);
    }

    @Test
    public void extractCommitTagTest() {
        var expected = "r4.12";
        var actual = dataExtractor.extractCommitTag("junit", "junit", "4.12");
        assertEquals(expected, actual);
    }

    @Test
    public void extractDependencyDataTest() {
        var expected = DependencyData.fromJSON(new JSONObject("{\n" +
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
        var actual = dataExtractor.extractDependencyData("junit", "junit", "4.12");
        assertEquals(expected, actual);
    }

    @Test
    public void extractDependencyDataWithParentPropertiesTest() {
        var expected = DependencyData.fromJSON(new JSONObject("{\n" +
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
                "               \"upperBound\":\"1.2.259\",\n" +
                "               \"lowerBound\":\"1.2.259\"\n" +
                "            }\n" +
                "         ],\n" +
                "         \"groupId\":\"javax.sip\",\n" +
                "         \"scope\":\"provided\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"jain-sip-ri\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\"\n" +
                "      },\n" +
                "      {\n" +
                "         \"versionConstraints\":[\n" +
                "            {\n" +
                "               \"isUpperHardRequirement\":false,\n" +
                "               \"isLowerHardRequirement\":false,\n" +
                "               \"upperBound\":\"1.6.6\",\n" +
                "               \"lowerBound\":\"1.6.6\"\n" +
                "            }\n" +
                "         ],\n" +
                "         \"groupId\":\"org.mobicents.ha.javax.sip\",\n" +
                "         \"scope\":\"\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"restcomm-jain-sip-ha-core\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\"\n" +
                "      },\n" +
                "      {\n" +
                "         \"versionConstraints\":[\n" +
                "            {\n" +
                "               \"isUpperHardRequirement\":false,\n" +
                "               \"isLowerHardRequirement\":false,\n" +
                "               \"upperBound\":\"3.4.1\",\n" +
                "               \"lowerBound\":\"3.4.1\"\n" +
                "            }\n" +
                "         ],\n" +
                "         \"groupId\":\"com.hazelcast\",\n" +
                "         \"scope\":\"\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"hazelcast\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\"\n" +
                "      },\n" +
                "      {\n" +
                "         \"versionConstraints\":[\n" +
                "            {\n" +
                "               \"isUpperHardRequirement\":false,\n" +
                "               \"isLowerHardRequirement\":false,\n" +
                "               \"upperBound\":\"1.2.17\",\n" +
                "               \"lowerBound\":\"1.2.17\"\n" +
                "            }\n" +
                "         ],\n" +
                "         \"groupId\":\"log4j\",\n" +
                "         \"scope\":\"\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"log4j\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\"\n" +
                "      },\n" +
                "      {\n" +
                "         \"versionConstraints\":[\n" +
                "            {\n" +
                "               \"isUpperHardRequirement\":false,\n" +
                "               \"isLowerHardRequirement\":false,\n" +
                "               \"upperBound\":\"3.8.1\",\n" +
                "               \"lowerBound\":\"3.8.1\"\n" +
                "            }\n" +
                "         ],\n" +
                "         \"groupId\":\"junit\",\n" +
                "         \"scope\":\"test\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"junit\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\"\n" +
                "      },\n" +
                "      {\n" +
                "         \"versionConstraints\":[\n" +
                "            {\n" +
                "               \"isUpperHardRequirement\":false,\n" +
                "               \"isLowerHardRequirement\":false,\n" +
                "               \"upperBound\":\"8.1.147\",\n" +
                "               \"lowerBound\":\"8.1.147\"\n" +
                "            }\n" +
                "         ],\n" +
                "         \"groupId\":\"org.mobicents.tools\",\n" +
                "         \"scope\":\"test\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"sip-balancer-jar\",\n" +
                "         \"exclusions\":[\n" +
                "            {\n" +
                "               \"groupId\":\"org.jboss.netty\",\n" +
                "               \"artifactId\":\"netty\"\n" +
                "            },\n" +
                "            {\n" +
                "               \"groupId\":\"org.jboss.cache\",\n" +
                "               \"artifactId\":\"jbosscache-core\"\n" +
                "            },\n" +
                "            {\n" +
                "               \"groupId\":\"org.jboss.cache\",\n" +
                "               \"artifactId\":\"jbosscache-pojo\"\n" +
                "            },\n" +
                "            {\n" +
                "               \"groupId\":\"jgroups\",\n" +
                "               \"artifactId\":\"jgroups\"\n" +
                "            },\n" +
                "            {\n" +
                "               \"groupId\":\"com.sun.jdmk\",\n" +
                "               \"artifactId\":\"jmxtools\"\n" +
                "            }\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\"\n" +
                "      }\n" +
                "   ]\n" +
                "}"));
        var actual = dataExtractor.extractDependencyData("org.mobicents.ha.javax.sip", "restcomm-jain-sip-ha-hazelcast-backend", "1.6.6");
        assertEquals(expected, actual);
    }

    @Test
    public void extractPackagingTypeTest() {
        var expected = "pom";
        var actual = dataExtractor.extractPackagingType("org.wso2.carbon.identity.inbound.auth.sts", "org.wso2.carbon.identity.sts.passive.server.feature", "5.2.9");
        assertEquals(expected, actual);
    }

    @Test
    public void extractPackagingTypeFromPropertiesTest() {
        var expected = "war";
        var actual = dataExtractor.extractPackagingType("org.graphity", "client", "1.1.3");
        assertEquals(expected, actual);
    }

    @Test
    public void extractAllDataTest() {
        var expectedDependencyData = DependencyData.fromJSON(new JSONObject("{\n" +
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
        var actualDependencyData = dataExtractor.extractDependencyData("junit", "junit", "4.12");
        var expectedRepoUrl = "http://github.com/junit-team/junit/tree/master";
        var actualRepoUrl = dataExtractor.extractRepoUrl("junit", "junit", "4.12");
        var expectedCommitTag = "r4.12";
        var actualCommitTag = dataExtractor.extractCommitTag("junit", "junit", "4.12");
        var expectedSourcesUrl = "https://repo.maven.apache.org/maven2/junit/junit/4.12/junit-4.12-sources.jar";
        var actualSourcesUrl = dataExtractor.generateMavenSourcesLink("junit", "junit", "4.12");
        var expectedPackagingType = "jar";
        var actualPackagingType = dataExtractor.extractPackagingType("junit", "junit", "4.12");
        var expectedProjectName = "JUnit";
        var actualProjectName = dataExtractor.extractProjectName("junit", "junit", "4.12");
        assertEquals(expectedRepoUrl, actualRepoUrl);
        assertEquals(expectedDependencyData, actualDependencyData);
        assertEquals(expectedCommitTag, actualCommitTag);
        assertEquals(expectedSourcesUrl, actualSourcesUrl);
        assertEquals(expectedPackagingType, actualPackagingType);
        assertEquals(expectedProjectName, actualProjectName);
    }

    @Test
    public void sourcesJarTest() {
        this.dataExtractor = new DataExtractor(List.of("https://google.com/", "https://repo.maven.apache.org/maven2/"));
        var expectedSourcesUrl = "https://repo.maven.apache.org/maven2/junit/junit/4.12/junit-4.12-sources.jar";
        var actualSourcesUrl = dataExtractor.generateMavenSourcesLink("junit", "junit", "4.12");
        assertEquals(expectedSourcesUrl, actualSourcesUrl);
    }

    @Test
    public void multipleReposTest() {
        this.dataExtractor = new DataExtractor(List.of("https://repo.maven.apache.org/maven2/"));
        var result = this.dataExtractor.extractDependencyData("abbot", "abbot", "1.4.0");
        assertEquals(new DependencyData(new DependencyManagement(new ArrayList<>()), List.of(new Dependency("junit", "junit", "4.8.2", new ArrayList<>(), "test", false, "", ""))), result);
        this.dataExtractor = new DataExtractor(List.of("https://repository.jboss.org/nexus/content/repositories/public-jboss/"));
        result = this.dataExtractor.extractDependencyData("abbot", "abbot", "1.4.0");
        assertEquals(new DependencyData(new DependencyManagement(new ArrayList<>()), List.of(new Dependency("junit", "junit", "4.8.2", new ArrayList<>(), "test", false, "", ""))), result);
        this.dataExtractor = new DataExtractor(List.of("https://repo.maven.apache.org/maven2/", "https://repository.jboss.org/nexus/content/repositories/public-jboss/"));
        result = this.dataExtractor.extractDependencyData("abbot", "abbot", "1.4.0");
        assertEquals(new DependencyData(new DependencyManagement(new ArrayList<>()), List.of(new Dependency("junit", "junit", "4.8.2", new ArrayList<>(), "test", false, "", ""))), result);
        this.dataExtractor = new DataExtractor(List.of("https://repository.jboss.org/nexus/content/repositories/public-jboss/", "https://repo.maven.apache.org/maven2/"));
        result = this.dataExtractor.extractDependencyData("abbot", "abbot", "1.4.0");
        assertEquals(new DependencyData(new DependencyManagement(new ArrayList<>()), List.of(new Dependency("junit", "junit", "4.8.2", new ArrayList<>(), "test", false, "", ""))), result);
    }

    @Test
    public void noProjectNameTest() {
        var result = dataExtractor.extractProjectName("com.alicp.jetcache", "jetcache-redis-lettuce", "2.5.13");
        assertNull(result);
    }

    @Test
    public void replaceDomTreeReferenceTest() throws DocumentException {
        var name = "fasten";
        var xml = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
                "<name>" + name + "</name>" +
                "</project>";
        var value = dataExtractor.replacePropertyReferences("${project.name}", new HashMap<>(), new SAXReader().read(new ByteArrayInputStream(xml.getBytes())).getRootElement());
        assertEquals(name, value);
    }

    @Test
    public void replaceSubStringReferenceTest() throws DocumentException {
        var xml = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" + "</project>";
        var map = new HashMap<String, String>();
        map.put("name", "FASTEN");
        var value = dataExtractor.replacePropertyReferences("Welcome to ${name} Project!", map, new SAXReader().read(new ByteArrayInputStream(xml.getBytes())).getRootElement());
        assertEquals("Welcome to FASTEN Project!", value);
    }

    @Test
    public void replaceMultipleSubStringReferenceTest() throws DocumentException {
        var xml = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" + "</project>";
        var map = new HashMap<String, String>();
        map.put("i", "1");
        map.put("j", "2");
        var value = dataExtractor.replacePropertyReferences("a${i}b${j}c", map, new SAXReader().read(new ByteArrayInputStream(xml.getBytes())).getRootElement());
        assertEquals("a1b2c", value);
    }

    @Test
    public void noReferenceTest() throws DocumentException {
        var xml = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" + "</project>";
        var map = new HashMap<String, String>();
        var str = "hello world";
        var value = dataExtractor.replacePropertyReferences(str, map, new SAXReader().read(new ByteArrayInputStream(xml.getBytes())).getRootElement());
        assertEquals(str, value);
    }

    @Test
    public void noReferenceValueTest() throws DocumentException {
        var xml = "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" + "</project>";
        var map = new HashMap<String, String>();
        var str = "hello ${world}";
        var value = dataExtractor.replacePropertyReferences(str, map, new SAXReader().read(new ByteArrayInputStream(xml.getBytes())).getRootElement());
        assertEquals(str, value);
    }

    @Test
    public void extractParentTest() {
        var expected = "org.apache.logging.log4j" + Constants.mvnCoordinateSeparator + "log4j" + Constants.mvnCoordinateSeparator + "2.13.3";
        var actual = dataExtractor.extractParentCoordinate("org.apache.logging.log4j", "log4j-api", "2.13.3");
        assertEquals(expected, actual);
    }
}
