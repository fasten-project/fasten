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

package eu.fasten.analyzer.javacgopal;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.data.exceptions.EmptyCallGraphException;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.plugins.KafkaPlugin;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OPALPlugin extends Plugin {

    public OPALPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class OPAL implements KafkaPlugin {

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
            outputPath = null;
            graph = null;
            try {
                var kafkaConsumedJson = new JSONObject(kafkaRecord);
                if (kafkaConsumedJson.has("payload")) {
                    kafkaConsumedJson = kafkaConsumedJson.getJSONObject("payload");
                }
                final var mavenCoordinate = new MavenCoordinate(kafkaConsumedJson);

                logger.info("Generating call graph for {}", mavenCoordinate.getCoordinate());
                this.graph = PartialCallGraph.createExtendedRevisionCallGraph(mavenCoordinate,
                        "", "CHA", kafkaConsumedJson.optLong("date", -1));

                if (this.graph.isCallGraphEmpty()) {
                    throw new EmptyCallGraphException();
                }

                var groupId = graph.product.split(Constants.coordinatePartsJoin)[0];
                var artifactId = graph.product.split(Constants.coordinatePartsJoin)[1];
                var version = graph.version;
                var product = artifactId + "_" + groupId + "_" + version;

                var firstLetter = artifactId.substring(0, 1);

                outputPath = File.separator + Constants.mavenForge + File.separator
                        + firstLetter + File.separator
                        + artifactId + File.separator + product + ".json";

                logger.info("Call graph successfully generated for {}!",
                        mavenCoordinate.getCoordinate());

            } catch (Throwable e) {
                setPluginError(e);
                logger.error("", e);
            }
        }

        @Override
        public Optional<String> produce() {
            if (this.graph != null && !this.graph.isCallGraphEmpty()) {
                return Optional.of(graph.toJSON().toString());
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getOutputPath() {
            return outputPath;
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
            return "0.1.2";
        }
    }
}
