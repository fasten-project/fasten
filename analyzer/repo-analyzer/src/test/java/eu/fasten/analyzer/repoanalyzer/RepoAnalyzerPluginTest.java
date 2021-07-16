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

package eu.fasten.analyzer.repoanalyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.fasten.analyzer.repoanalyzer.repo.RepoAnalyzerFactory;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import org.dom4j.DocumentException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RepoAnalyzerPluginTest {

    private static RepoAnalyzerPlugin.RepoAnalyzerExtension plugin;

    @BeforeAll
    static void setUp() {
        plugin = new RepoAnalyzerPlugin.RepoAnalyzerExtension();
    }

    @Test
    void name() {
        assertEquals("RepoAnalyzerPlugin", plugin.name());
    }

    @Test
    void description() {
        assertEquals("Consumes records from RepoCloner and produces statistics" +
                " about tests present in the cloned repository", plugin.description());
    }

    @Test
    void consume() throws IOException, DocumentException, InterruptedException {
        assertTrue(plugin.produce().isEmpty());

        var repoPath = new File(Objects.requireNonNull(RepoAnalyzerPluginTest.class.getClassLoader()
                .getResource("simpleMavenRepo")).getFile()).getAbsolutePath();
        var json = new JSONObject();
        json.put("repoPath", repoPath);

        plugin.consume(json.toString());

        assertNull(plugin.getPluginError());

        var optResult = plugin.produce();
        assertTrue(optResult.isPresent());

        var result = optResult.orElse("");

        var repoAnalyzerFactory = new RepoAnalyzerFactory();
        var analyzer = repoAnalyzerFactory.getAnalyzer(repoPath);
        assertEquals(result, analyzer.analyze().toString());
    }
}