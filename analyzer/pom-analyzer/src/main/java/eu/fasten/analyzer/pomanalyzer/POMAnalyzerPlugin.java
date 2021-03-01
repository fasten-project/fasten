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
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.maven.data.DependencyData;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.core.plugins.KafkaPlugin;
import java.io.File;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.json.JSONException;
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

        private String consumerTopic = "fasten.mvn.pkg";
        private final Logger logger = LoggerFactory.getLogger(POMAnalyzer.class.getName());
        private Exception pluginError = null;
        private static DSLContext dslContext;
        private String artifact = null;
        private String group = null;
        private String version = null;
        private long date = -1L;
        private String repoUrl = null;
        private DependencyData dependencyData = null;
        private String commitTag = null;
        private String sourcesUrl = null;
        private String packagingType = null;
        private String projectName = null;
        private String parentCoordinate = null;
        private boolean restartTransaction = false;
        private boolean processedRecord = false;
        private String artifactRepository = null;

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.of(Collections.singletonList(consumerTopic));
        }

        @Override
        public void setTopic(String topicName) {
            this.consumerTopic = topicName;
        }

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            POMAnalyzer.dslContext = dslContexts.get(Constants.mvnForge);
        }

        @Override
        public void consume(String record) {
            pluginError = null;
            artifact = null;
            group = null;
            version = null;
            date = -1L;
            repoUrl = null;
            dependencyData = null;
            commitTag = null;
            sourcesUrl = null;
            packagingType = null;
            projectName = null;
            parentCoordinate = null;
            artifactRepository = null;
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
            try {
                artifact = payload.getString("artifactId").replaceAll("[\\n\\t ]", "");
                group = payload.getString("groupId").replaceAll("[\\n\\t ]", "");
                version = payload.getString("version").replaceAll("[\\n\\t ]", "");
                date = payload.optLong("date", -1L);
                artifactRepository = payload.optString("artifactRepository", MavenUtilities.MAVEN_CENTRAL_REPO);
            } catch (JSONException e) {
                logger.error("Malformed input: " + payload.toString(), e);
                this.pluginError = e;
                return;
            }
            var repos = MavenUtilities.getRepos();
            if (!artifactRepository.equals(MavenUtilities.MAVEN_CENTRAL_REPO)) {
                repos.addFirst(artifactRepository);
            }
            var pomUrl = payload.optString("pomUrl", null);
            final var product = group + Constants.mvnCoordinateSeparator + artifact
                    + Constants.mvnCoordinateSeparator + version;
            var dataExtractor = new DataExtractor(repos);
            try {
                if (pomUrl != null) {
                    var mavenCoordinate = dataExtractor.getMavenCoordinate(pomUrl);
                    logger.info("Extracted Maven coordinate: " + mavenCoordinate);
                    if (mavenCoordinate != null && !mavenCoordinate.contains("${")) {
                        group = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[0];
                        artifact = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[1];
                        version = mavenCoordinate.split(Constants.mvnCoordinateSeparator)[2];
                    }
                }
                if (date == -1) {
                    var releaseDate = dataExtractor.extractReleaseDate(group, artifact, version, artifactRepository);
                    if (releaseDate != null) {
                        date = releaseDate;
                    }
                }
                repoUrl = dataExtractor.extractRepoUrl(group, artifact, version);
                logger.info("Extracted repository URL " + repoUrl + " from " + product);
                dependencyData = dataExtractor.extractDependencyData(group, artifact, version);
                logger.info("Extracted dependency information from " + product);
                commitTag = dataExtractor.extractCommitTag(group, artifact, version);
                logger.info("Extracted commit tag from " + product);
                sourcesUrl = dataExtractor.generateMavenSourcesLink(group, artifact, version);
                logger.info("Generated link to Maven sources for " + product);
                packagingType = dataExtractor.extractPackagingType(group, artifact, version);
                logger.info("Extracted packaging type from " + product);
                projectName = dataExtractor.extractProjectName(group, artifact, version);
                logger.info("Extracted project name from " + product);
                parentCoordinate = dataExtractor.extractParentCoordinate(group, artifact, version);
                logger.info("Extracted parent coordinate from " + product);
            } catch (RuntimeException e) {
                logger.error("Error extracting data for " + product, e);
                this.pluginError = e;
                MavenUtilities.getRepos().remove(artifactRepository);
                return;
            }
            int transactionRestartCount = 0;
            do {
                try {
                    var metadataDao = new MetadataDao(dslContext);
                    dslContext.transaction(transaction -> {
                        metadataDao.setContext(DSL.using(transaction));
                        long id;
                        try {
                            id = saveToDatabase(group + Constants.mvnCoordinateSeparator + artifact,
                                    version, repoUrl, commitTag, sourcesUrl, packagingType, date,
                                    projectName, parentCoordinate, dependencyData, artifactRepository, metadataDao);
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
                    && transactionRestartCount < Constants.transactionRestartLimit);
            MavenUtilities.getRepos().remove(artifactRepository);
        }

        /**
         * Saves information extracted from POM into Metadata Database.
         *
         * @param product          groupId.artifactId
         * @param version          Version of the artifact
         * @param repoUrl          URL of the repository of the product
         * @param commitTag        Commit tag of the version of the artifact in the repository
         * @param sourcesUrl       Link to Maven sources Jar file
         * @param packagingType    Packaging type of the artifact
         * @param timestamp        Timestamp of the package
         * @param projectName      Project name to which artifact belongs
         * @param parentCoordinate Coordinate of the parent POM
         * @param dependencyData   Dependency information from POM
         * @param metadataDao      Metadata Database Access Object
         * @return ID of the package version in the database
         */
        public long saveToDatabase(String product, String version, String repoUrl, String commitTag,
                                   String sourcesUrl, String packagingType, long timestamp,
                                   String projectName, String parentCoordinate,
                                   DependencyData dependencyData, String artifactRepository, MetadataDao metadataDao) {
            final var packageId = metadataDao.insertPackage(product, Constants.mvnForge,
                    projectName, repoUrl, null);
            var packageVersionMetadata = new JSONObject();
            packageVersionMetadata.put("dependencyManagement",
                    (dependencyData.dependencyManagement != null)
                            ? dependencyData.dependencyManagement.toJSON() : null);
            packageVersionMetadata.put("commitTag", (commitTag != null) ? commitTag : "");
            packageVersionMetadata.put("sourcesUrl", (sourcesUrl != null) ? sourcesUrl : "");
            packageVersionMetadata.put("packagingType", (packagingType != null)
                    ? packagingType : "");
            packageVersionMetadata.put("parentCoordinate", (parentCoordinate != null)
                    ? parentCoordinate : "");
            Long artifactRepoId;
            if (artifactRepository == null) {
                artifactRepoId = null;
            } else if (artifactRepository.equals(MavenUtilities.MAVEN_CENTRAL_REPO)) {
                artifactRepoId = -1L;
            } else {
                artifactRepoId = metadataDao.insertArtifactRepository(artifactRepository);
            }
            final var packageVersionId = metadataDao.insertPackageVersion(packageId,
                    Constants.opalGenerator, version, artifactRepoId, null, this.getProperTimestamp(timestamp),
                    packageVersionMetadata);
            for (var dep : dependencyData.dependencies) {
                var depProduct = dep.groupId + Constants.mvnCoordinateSeparator + dep.artifactId;
                final var depId = metadataDao.insertPackage(depProduct, Constants.mvnForge);
                metadataDao.insertDependency(packageVersionId, depId,
                        dep.getVersionConstraints(), null, null, null, dep.toJSON());
            }
            return packageVersionId;
        }

        private Timestamp getProperTimestamp(long timestamp) {
            if (timestamp == -1) {
                return null;
            } else {
                if (timestamp / (1000L * 60 * 60 * 24 * 365) < 1L) {
                    return new Timestamp(timestamp * 1000);
                } else {
                    return new Timestamp(timestamp);
                }
            }
        }

        @Override
        public Optional<String> produce() {
            var json = new JSONObject();
            json.put("artifactId", artifact);
            json.put("groupId", group);
            json.put("version", version);
            json.put("date", date);
            json.put("repoUrl", (repoUrl != null) ? repoUrl : "");
            json.put("commitTag", (commitTag != null) ? commitTag : "");
            json.put("sourcesUrl", sourcesUrl);
            json.put("packagingType", packagingType);
            json.put("projectName", (projectName != null) ? projectName : "");
            json.put("parentCoordinate", (parentCoordinate != null) ? parentCoordinate : "");
            json.put("dependencyData", dependencyData.toJSON());
            json.put("forge", Constants.mvnForge);
            json.put("artifactRepository", artifactRepository);
            return Optional.of(json.toString());
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
            return "0.1.2";
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public Exception getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }
    }
}
