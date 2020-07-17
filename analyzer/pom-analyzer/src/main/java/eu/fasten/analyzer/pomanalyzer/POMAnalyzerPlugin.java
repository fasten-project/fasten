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
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        private String commitTag = null;
        private boolean restartTransaction = false;
        private final int transactionRestartLimit = 3;
        private boolean processedRecord = false;

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
            commitTag = null;
            this.processedRecord = false;
            this.restartTransaction = false;
            logger.info("Consumed: " + record);
            var jsonRecord = new JSONObject(record);
            var payload = new JSONObject();
            if (jsonRecord.has("payload")) {
                payload = jsonRecord.getJSONObject("payload");
            } else {
                payload = jsonRecord;
            }
            artifact = payload.getString("artifactId");
            group = payload.getString("groupId");
            version = payload.getString("version");
            final var product = group + ":" + artifact + ":" + version;
            var dataExtractor = new DataExtractor();
            repoUrl = dataExtractor.extractRepoUrl(group, artifact, version);
            logger.info("Extracted repository URL " + repoUrl + " from " + product);
            dependencyData = dataExtractor.extractDependencyData(group, artifact, version);
            logger.info("Extracted dependency information from " + product);
            commitTag = dataExtractor.extractCommitTag(group, artifact, version);
            logger.info("Extracted commit tag from " + product);
            int transactionRestartCount = 0;
            do {
                try {
                    var metadataDao = new MetadataDao(dslContext);
                    dslContext.transaction(transaction -> {
                        metadataDao.setContext(DSL.using(transaction));
                        long id;
                        try {
                            id = saveToDatabase(group + "." + artifact, version, repoUrl,
                                    commitTag, dependencyData, metadataDao);
                        } catch (RuntimeException e) {
                            logger.error("Error saving data to the database: '" + product + "'", e);
                            processedRecord = false;
                            this.pluginError = e;
                            if (e instanceof DataAccessException) {
                                logger.info("Restarting transaction for '" + product + "'");
                                restartTransaction = true;
                            } else {
                                restartTransaction = false;
                            }
                            throw e;
                        }
                        if (getPluginError() == null) {
                            processedRecord = true;
                            restartTransaction = false;
                            logger.info("Saved data for " + product
                                    + " with package version ID = " + id);
                        }
                    });
                } catch (Exception expected) {
                }
                transactionRestartCount++;
            } while (restartTransaction && !processedRecord
                    && transactionRestartCount < transactionRestartLimit);
        }

        /**
         * Saves information extracted from POM into Metadata Database.
         *
         * @param product        groupId.artifactId
         * @param version        Version of the artifact
         * @param repoUrl        URL of the repository of the product
         * @param commitTag      Commit tag of the version of the artifact in the repository
         * @param dependencyData Dependency information from POM
         * @param metadataDao    Metadata Database Access Object
         * @return ID of the package version in the database
         */
        public long saveToDatabase(String product, String version, String repoUrl, String commitTag,
                                   DependencyData dependencyData, MetadataDao metadataDao) {
            final var packageId = metadataDao.insertPackage(product, "mvn", null, repoUrl, null);
            var packageVersionMetadata = new JSONObject();
            packageVersionMetadata.put("dependencyManagement",
                    (dependencyData.dependencyManagement != null)
                            ? dependencyData.dependencyManagement.toJSON() : null);
            packageVersionMetadata.put("commitTag", commitTag);
            final var packageVersionId = metadataDao.insertPackageVersion(packageId,
                    "OPAL", version, null, packageVersionMetadata);
            for (var dependency : dependencyData.dependencies) {
                var depProduct = dependency.groupId + "." + dependency.artifactId;
                final var depId = metadataDao.insertPackage(depProduct, "mvn", null, null, null);
                metadataDao.insertDependency(packageVersionId, depId,
                        dependency.getVersionConstraints(), dependency.toJSON());
            }
            return packageVersionId;
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
