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

package eu.fasten.analyzer.javacgopalv3;

import eu.fasten.analyzer.javacgopalv3.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopalv3.data.PartialCallGraph;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.plugins.KafkaPlugin;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OPALV3Plugin extends Plugin {

    public OPALV3Plugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class OPALV3 implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private String consumeTopic = "fasten.maven.pkg";
        private Throwable pluginError;
        private ExtendedRevisionCallGraph graph;
        private String outputPath;

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(new ArrayList<>(Collections.singletonList(consumeTopic)));
        }

        @Override
        public void consume(String kafkaRecord) {
            pluginError = null;
            try {
                var kafkaConsumedJson = new JSONObject(kafkaRecord);
                if (kafkaConsumedJson.has("payload")) {
                    kafkaConsumedJson = kafkaConsumedJson.getJSONObject("payload");
                }
                final var mavenCoordinate = getMavenCoordinate(kafkaConsumedJson);

                logger.info("Generating call graph for {}", mavenCoordinate.getCoordinate());
                this.graph = generateCallGraph(mavenCoordinate,
                        kafkaConsumedJson.optLong("date", -1));

                if (graph == null || graph.isCallGraphEmpty()) {
                    logger.warn("Empty call graph for {}", mavenCoordinate.getCoordinate());
                    return;
                }

                var groupId = graph.product.split(":")[0];
                var artifactId = graph.product.split(":")[1];
                var version = graph.version;
                var product = artifactId + "_" + groupId + "_" + version;

                var firstLetter = artifactId.substring(0, 1);

                outputPath = File.separator + "mvn" + File.separator
                        + firstLetter + File.separator
                        + artifactId + File.separator + product + ".json";

                logger.info("Call graph successfully generated for {}!",
                        mavenCoordinate.getCoordinate());

            } catch (Exception e) {
                setPluginError(e);
                logger.error("", e);
            }
        }

        /**
         * Generate an ExtendedRevisionCallGraph.
         *
         * @param mavenCoordinate Maven coordinate
         * @param timestamp       timestamp
         * @return Generated ExtendedRevisionCallGraph
         */
        public ExtendedRevisionCallGraph generateCallGraph(final MavenCoordinate mavenCoordinate,
                                                           final long timestamp) {
            try {
                return PartialCallGraph
                        .createExtendedRevisionCallGraph(mavenCoordinate, "", "CHA", timestamp);
            } catch (FileNotFoundException e) {
                setPluginError(e);
            }
            return null;
        }

        @Override
        public Optional<String> produce() {
            if (this.graph != null) {
                return Optional.of(graph.toJSON().toString());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getOutputPath() {
            return outputPath;
        }

        /**
         * Convert consumed JSON from Kafka to {@link MavenCoordinate}.
         *
         * @param kafkaConsumedJson Coordinate JSON
         * @return MavenCoordinate
         */
        public MavenCoordinate getMavenCoordinate(final JSONObject kafkaConsumedJson) {
            try {
                var groupId = kafkaConsumedJson.getString("groupId");
                var artifactId = kafkaConsumedJson.getString("artifactId");
                var version = kafkaConsumedJson.getString("version");
                var packaging = kafkaConsumedJson.optString("packagingType", "jar");

                return new MavenCoordinate(groupId, artifactId, version, packaging);

            } catch (JSONException e) {
                setPluginError(e);
                logger.error("Could not parse input coordinates: {}\n{}", kafkaConsumedJson, e);
            }
            return null;
        }

        @Override
        public void setTopic(String topicName) {
            this.consumeTopic = topicName;
        }

        @Override
        public String name() {
            return this.getClass().getCanonicalName();
        }

        @Override
        public String description() {
            return "Generates call graphs for Java packages";
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

        public void setPluginError(Throwable throwable) {
            this.pluginError = throwable;
        }

        @Override
        public void freeResource() {
        }

        @Override
        public String version() {
            return "0.0.1";
        }
    }
}
