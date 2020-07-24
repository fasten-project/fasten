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

import eu.fasten.analyzer.pomanalyzer.pom.data.DependencyData;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataExtractorTest {

    private DataExtractor dataExtractor;

    @BeforeEach
    public void setup() {
        dataExtractor = new DataExtractor();
    }

    @Test
    public void extractRepoUrlTest() {
        var expected = "http://github.com/junit-team/junit/tree/master";
        var actual = dataExtractor.extractRepoUrl("junit", "junit", "4.12");
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
        assertEquals(expectedRepoUrl, actualRepoUrl);
        assertEquals(expectedDependencyData, actualDependencyData);
        assertEquals(expectedCommitTag, actualCommitTag);
        assertEquals(expectedSourcesUrl, actualSourcesUrl);
    }
}
