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

import eu.fasten.analyzer.repoanalyzer.repo.RepoAnalyzerFactory;
import eu.fasten.core.plugins.KafkaPlugin;
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

public class RepoAnalyzerPlugin extends Plugin {

    public RepoAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class RepoAnalyzerExtension implements KafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(RepoAnalyzerPlugin.class);

        private String consumerTopic = "fasten.RepoCloner.out";
        private Exception pluginError;
        private JSONObject statistics;

        @Override
        public void consume(String record) {
            try {
                statistics = null;
                pluginError = null;
                long startTime = System.nanoTime();

                var json = new JSONObject(record);
                if (json.has("payload")) {
                    if (json.get("payload").toString().isEmpty()) {
                        var e = new RuntimeException("Empty payload");
                        setPluginError(e);
                        logger.error("[RECORD-PARSING] [FAILED] [-1] [NONE] " +
                                "[" + e.getClass().getSimpleName() + "]" +
                                "[" + e.getMessage() + "]", e);
                        return;
                    }
                    json = json.getJSONObject("payload");
                }

                final String repoPath = json.getString("repoPath");
                if (repoPath.isEmpty()) {
                    throw new IllegalArgumentException("Empty repo path");
                }

                final var coordinate =
                        json.has("groupId") && json.has("artifactId") && json.has("version")
                                ? json.get("groupId") + ":" + json.get("artifactId") + ":" + json.get("version")
                                : "UNKNOWN-ARTIFACT";

                var repoAnalyzerFactory = new RepoAnalyzerFactory();
                var analyzer = repoAnalyzerFactory.getAnalyzer(repoPath);
                this.statistics = analyzer.analyze();

                long endTime = System.nanoTime();
                long duration = (endTime - startTime) / 1000000;
                logger.info("[REPO-ANALYSIS] [SUCCESS] [" + duration + "] [" + coordinate + "] [NONE]");
            } catch (JSONException | IllegalArgumentException e) {
                logger.error("[RECORD-PARSING] [FAILED] [-1] [NONE] " +
                        "[" + e.getClass().getSimpleName() + "]" +
                        "[" + e.getMessage() + "]", e);
                setPluginError(e);
            } catch (Exception e) {
                logger.error("[REPO-ANALYSIS] [FAILED] [-1] [NONE] " +
                        "[" + e.getClass().getSimpleName() + "]" +
                        "[" + e.getMessage() + "]", e);
                setPluginError(e);
            }
        }

        @Override
        public Optional<String> produce() {
            return statistics != null ? Optional.of(statistics.toString()) : Optional.empty();
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public void setPluginError(Exception throwable) {
            this.pluginError = throwable;
        }

        @Override
        public Exception getPluginError() {
            return pluginError;
        }

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        @Override
        public String getOutputPath() {
            return null;
        }

        @Override
        public String name() {
            return "RepoAnalyzerPlugin";
        }

        @Override
        public String description() {
            return "Consumes records from RepoCloner and produces statistics " +
                    "about tests present in the cloned repository";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void freeResource() {

        }
    }
}
