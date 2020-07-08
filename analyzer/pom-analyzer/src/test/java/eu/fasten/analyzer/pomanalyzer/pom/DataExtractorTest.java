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
    public void extractDependencyDataWithPropertiesTest() {
        var expected = DependencyData.fromJSON(new JSONObject("{\"dependencyManagement\":{\"dependencies\":[]},\"dependencies\":[{\"groupId\":\"org.springframework.boot\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"spring-boot-starter-logging\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"*\"},{\"groupId\":\"com.squareup.okhttp3\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"okhttp\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"3.14.4\"},{\"groupId\":\"com.squareup.okhttp3\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"logging-interceptor\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"3.14.4\"},{\"groupId\":\"com.squareup.okhttp3\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"okhttp-tls\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"3.14.4\"},{\"groupId\":\"com.google.code.gson\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"gson\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"2.8.5\"},{\"groupId\":\"io.gsonfire\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"gson-fire\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"1.8.3\"},{\"groupId\":\"org.apache.commons\",\"scope\":\"\",\"classifier\":\"\",\"artifactId\":\"commons-lang3\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"*\"},{\"groupId\":\"org.junit.jupiter\",\"scope\":\"test\",\"classifier\":\"\",\"artifactId\":\"junit-jupiter-api\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"*\"},{\"groupId\":\"org.junit.jupiter\",\"scope\":\"test\",\"classifier\":\"\",\"artifactId\":\"junit-jupiter-engine\",\"exclusions\":[],\"optional\":false,\"type\":\"\",\"version\":\"*\"}]}\n"));
        var actual = dataExtractor.extractDependencyData("au.org.consumerdatastandards", "client", "1.1.1");
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
