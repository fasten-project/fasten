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

package eu.fasten.analyzer.repoclonerplugin;

import eu.fasten.analyzer.repoclonerplugin.utils.GitCloner;
import eu.fasten.core.plugins.DataWriter;
import eu.fasten.core.plugins.KafkaPlugin;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepoClonerPlugin extends Plugin {

    public RepoClonerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class RepoCloner implements KafkaPlugin, DataWriter {

        private String consumerTopic = "fasten.POMAnalyzer.out";
        private Throwable pluginError = null;
        private final Logger logger = LoggerFactory.getLogger(RepoCloner.class.getName());
        private String repoPath = null;
        private String artifact = null;
        private String group = null;
        private String version = null;
        private static String baseDir = "";
        private String outputPath = null;

        @Override
        public void setBaseDir(String baseDir) {
            RepoCloner.baseDir = baseDir;
        }

        String getRepoPath() {
            return repoPath;
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
        public Optional<String> produce() {
            var json = new JSONObject();
            if (artifact != null && !artifact.isEmpty()) {
                json.put("artifactId", artifact);
            }
            if (group != null && !group.isEmpty()) {
                json.put("groupId", group);
            }
            if (version != null && !version.isEmpty()) {
                json.put("version", version);
            }
            if (repoPath != null && !repoPath.isEmpty()) {
                json.put("repoPath", repoPath);
            }
            if (json.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(json.toString());
            }
        }

        @Override
        public String getOutputPath() {
            return this.outputPath;
        }

        @Override
        public void consume(String record) {
            this.pluginError = null;
            this.artifact = null;
            this.group = null;
            this.version = null;
            this.repoPath = null;
            var json = new JSONObject(record).getJSONObject("payload");
            artifact = json.optString("artifactId");
            group = json.optString("groupId");
            version = json.optString("version");
            String product = null;
            if (artifact != null && !artifact.isEmpty()
                    && group != null && !group.isEmpty()) {
                product = group + ":" + artifact + ((version == null) ? "" : ":" + version);
            }
            outputPath = File.separator
                    + (artifact == null ? "" : artifact.charAt(0) + File.separator)
                    + artifact + File.separator
                    + (product == null ? "unknown" : product.replace(":", "_")) + ".json";
            var repoUrl = json.optString("repoUrl");
            if (repoUrl != null && !repoUrl.isEmpty()) {
                try {
                    var gitCloner = new GitCloner(baseDir);
                    cloneRepo(repoUrl, gitCloner);
                } catch (GitAPIException | IOException e) {
                    logger.error("Error cloning repository"
                            + (product == null ? "" : "for '" + product + "'")
                            + " from " + repoUrl, e);
                    this.pluginError = e;
                    return;
                }
                if (getPluginError() == null) {
                    logger.info("Cloned repository"
                            + (product == null ? "" : "of '" + product + "'")
                            + " from " + repoUrl + " to " + repoPath);
                }
            } else {
                logger.info("Repository URL not found");
            }
        }

        public void cloneRepo(String repoUrl, GitCloner gitCloner)
                throws GitAPIException, IOException {
            repoPath = gitCloner.cloneRepo(repoUrl);
        }

        @Override
        public String name() {
            return "Repo Cloner Plugin";
        }

        @Override
        public String description() {
            return "Repo Cloner Plugin. "
                    + "Consumes a repository URL, "
                    + "clones the repo to the provided directory building directory hierarchy"
                    + "and produces the path to directory with repository.";
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
