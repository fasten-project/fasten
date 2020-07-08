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
    public void extractDependencyDataTest() {
        var expected = DependencyData.fromJSON(new JSONObject("{\n" +
                "   \"dependencyManagement\":{\n" +
                "      \"dependencies\":[\n" +
                "\n" +
                "      ]\n" +
                "   },\n" +
                "   \"dependencies\":[\n" +
                "      {\n" +
                "         \"groupId\":\"org.hamcrest\",\n" +
                "         \"scope\":\"\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"hamcrest-core\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\",\n" +
                "         \"version\":\"1.3\"\n" +
                "      }\n" +
                "   ]\n" +
                "}\n"));
        var actual = dataExtractor.extractDependencyData("junit", "junit", "4.12");
        assertEquals(expected, actual);
    }

    @Test
    public void extractDependencyDataWithParentPropertiesTest() {
        var expected = DependencyData.fromJSON(new JSONObject("{\"dependencyManagement\":{\"dependencies\":[]},\"dependencies\":[{\"groupId\":\"javax.sip\",\"scope\":\"provided\",\"classifier\":\"\",\"artifactId\":\"jain-sip-ri\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"*\"},{\"groupId\":\"org.mobicents.ha.javax.sip\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"restcomm-jain-sip-ha-core\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"1.6.6\"},{\"groupId\":\"com.hazelcast\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"hazelcast\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"3.4.1\"},{\"groupId\":\"log4j\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"log4j\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"1.2.17\"},{\"groupId\":\"junit\",\"scope\":\"test\",\"classifier\":\"\",\"artifactId\":\"junit\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"3.8.1\"},{\"groupId\":\"org.mobicents.tools\",\"scope\":\"test\",\"classifier\":\"\",\"artifactId\":\"sip-balancer-jar\",\"exclusions\":[{\"groupId\":\"org.jboss.netty\",\"artifactId\":\"netty\"},{\"groupId\":\"org.jboss.cache\",\"artifactId\":\"jbosscache-core\"},{\"groupId\":\"org.jboss.cache\",\"artifactId\":\"jbosscache-pojo\"},{\"groupId\":\"jgroups\",\"artifactId\":\"jgroups\"},{\"groupId\":\"com.sun.jdmk\",\"artifactId\":\"jmxtools\"}],\"optional\":false,\"type\":\"\",\"version\":\"8.1.147\"}]}"));
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
                "         \"groupId\":\"org.hamcrest\",\n" +
                "         \"scope\":\"\",\n" +
                "         \"classifier\":\"\",\n" +
                "         \"artifactId\":\"hamcrest-core\",\n" +
                "         \"exclusions\":[\n" +
                "\n" +
                "         ],\n" +
                "         \"optional\":false,\n" +
                "         \"type\":\"\",\n" +
                "         \"version\":\"1.3\"\n" +
                "      }\n" +
                "   ]\n" +
                "}\n"));
        var actualDependencyData = dataExtractor.extractDependencyData("junit", "junit", "4.12");
        var expectedRepoUrl = "http://github.com/junit-team/junit/tree/master";
        var actualRepoUrl = dataExtractor.extractRepoUrl("junit", "junit", "4.12");
        assertEquals(expectedRepoUrl, actualRepoUrl);
        assertEquals(expectedDependencyData, actualDependencyData);
    }
}
