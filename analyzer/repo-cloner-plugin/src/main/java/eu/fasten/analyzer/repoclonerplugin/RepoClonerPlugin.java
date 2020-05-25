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

import eu.fasten.core.plugins.KafkaPlugin;
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
    public static class RepoCloner implements KafkaPlugin<String, String> {

        private String consumerTopic = "fasten.MetadataDBExtension.out";
        private Throwable pluginError = null;
        private final Logger logger = LoggerFactory.getLogger(RepoCloner.class.getName());
        private String repoPath = null;
        private String baseDir = "";

        public void setBaseDir(String baseDir) {
            this.baseDir = baseDir;
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
            if (repoPath == null) {
                return Optional.empty();
            } else {
                return Optional.of(repoPath);
            }
        }

        @Override
        public void consume(String record) {
            this.pluginError = null;
            this.repoPath = null;
            var json = new JSONObject(record).getJSONObject("payload");
            var gitCloner = new GitCloner(baseDir);
            var artifact = json.getString("artifact");
            var repoUrl = json.getString("repoUrl");
            try {
                cloneRepo(artifact, repoUrl, gitCloner);
            } catch (GitAPIException | IOException e) {
                logger.error("Error cloning repository for '" + artifact + "'", e);
                this.pluginError = e;
                return;
            }
            if (getPluginError() == null) {
                logger.info("Cloned the repo of '" + artifact + "' to " + repoPath);
            }
        }

        public void cloneRepo(String artifact, String repoUrl, GitCloner gitCloner)
                throws GitAPIException, IOException {
            repoPath = gitCloner.cloneRepo(artifact, repoUrl);
        }

        @Override
        public String name() {
            return "Repo Cloner Plugin";
        }

        @Override
        public String description() {
            return "Repo Cloner Plugin. "
                    + "Consumes GitHub repository URL, clones the repo to the provided directory,"
                    + " updates Metadata Database with file information "
                    + "and produces path to directory where the repo was cloned to.";
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
