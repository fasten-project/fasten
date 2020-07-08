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

import eu.fasten.analyzer.pomanalyzer.pom.DataExtractor;
import eu.fasten.analyzer.pomanalyzer.pom.data.DependencyData;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class POMAnalyzerPlugin extends Plugin {

    public POMAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class POMAnalyzer implements KafkaPlugin, DBConnector {

        private String consumerTopic = "fasten.maven.pkg";
        private final Logger logger = LoggerFactory.getLogger(POMAnalyzer.class.getName());
        private Throwable pluginError = null;
        private static DSLContext dslContext;
        private String artifact = null;
        private String group = null;
        private String version = null;
        private String repoUrl = null;
        private DependencyData dependencyData = null;

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        @Override
        public void setDBConnection(DSLContext dslContext) {
            POMAnalyzer.dslContext = dslContext;
        }

        @Override
        public void consume(String record) {
            pluginError = null;
            artifact = null;
            group = null;
            version = null;
            repoUrl = null;
            dependencyData = null;
            logger.info("Consumed: " + record);
            var payload = new JSONObject(record).getJSONObject("payload");
            artifact = payload.getString("artifactId");
            group = payload.getString("groupId");
            version = payload.getString("version");
            var dataExtractor = new DataExtractor();
            repoUrl = dataExtractor.extractRepoUrl(group, artifact, version);
            dependencyData = dataExtractor.extractDependencyData(group, artifact, version);
            logger.info("Extracted repository URL " + repoUrl
                    + " from " + group + ":" + artifact + ":" + version);
        }

        @Override
        public Optional<String> produce() {
            if (repoUrl != null) {
                var json = new JSONObject();
                json.put("artifactId", artifact);
                json.put("groupId", group);
                json.put("version", version);
                json.put("repoUrl", repoUrl);
                json.put("dependencyData", dependencyData.toJSON());
                return Optional.of(json.toString());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getOutputPath() {
            return File.separator + artifact.charAt(0) + File.separator
                    + artifact + File.separator + artifact + "_" + group + "_" + version + ".json";
        }

        @Override
        public String name() {
            return "POM Analyzer plugin";
        }

        @Override
        public String description() {
            return "POM Analyzer plugin. Consumes Maven coordinate from Kafka topic, "
                    + "downloads pom.xml of that coordinate and analyzes it "
                    + "extracting relevant information such as dependency information "
                    + "and repository URL, then inserts that data into Metadata Database "
                    + "and produces it to Kafka topic.";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public Throwable getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }
    }
}
