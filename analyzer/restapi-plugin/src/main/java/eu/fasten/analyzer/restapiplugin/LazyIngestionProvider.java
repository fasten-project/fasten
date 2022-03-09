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

package eu.fasten.analyzer.restapiplugin;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.json.JSONObject;

import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.resolution.NativeMavenResolver;
import eu.fasten.core.maven.utils.MavenUtilities;

public class LazyIngestionProvider {

    private static boolean hasArtifactBeenIngested(String packageName, String version) {
        return KnowledgeBaseConnector.kbDao.isArtifactIngested(toKey(packageName, version));
    }

    private static String toKey(String packageName, String version) {
        var key = String.format("%s:jar:%s-PRIORITY", packageName, version);
        return key;
    }

    public static void ingestArtifactIfNecessary(String packageName, String version, String artifactRepo, Long date) throws IllegalArgumentException, IOException {
        var groupId = packageName.split(Constants.mvnCoordinateSeparator)[0];
        var artifactId = packageName.split(Constants.mvnCoordinateSeparator)[1];
        if(artifactRepo == null || artifactRepo.isEmpty()) {
        	artifactRepo = MavenUtilities.MAVEN_CENTRAL_REPO;
        }
        if (!MavenUtilities.mavenArtifactExists(groupId, artifactId, version, artifactRepo)) {
            throw new IllegalArgumentException("Maven artifact '" + packageName + ":" + version
                    + "' could not be found in the repository of '"
                    + (artifactRepo == null ? MavenUtilities.MAVEN_CENTRAL_REPO : artifactRepo) + "'."
                    + " Make sure the Maven coordinate and repository are correct");
        }
        if (!hasArtifactBeenIngested(packageName, version)) {
            var jsonRecord = new JSONObject();
            jsonRecord.put("groupId", groupId);
            jsonRecord.put("artifactId", artifactId);
            jsonRecord.put("version", version);
            jsonRecord.put("artifactRepository", artifactRepo);
            if (date != null && date > 0) {
            	// TODO why is the date necessary?
                jsonRecord.put("date", date);
            }
            if (KnowledgeBaseConnector.kafkaProducer != null && KnowledgeBaseConnector.ingestTopic != null) {
                KafkaWriter.sendToKafka(KnowledgeBaseConnector.kafkaProducer, KnowledgeBaseConnector.ingestTopic, jsonRecord.toString());
            }
        }
    }

    public static void ingestArtifactWithDependencies(String packageName, String version) throws IllegalArgumentException, IOException {
        var groupId = packageName.split(Constants.mvnCoordinateSeparator)[0];
        var artifactId = packageName.split(Constants.mvnCoordinateSeparator)[0];
        ingestArtifactIfNecessary(packageName, version, null, null);
        var mavenResolver = new NativeMavenResolver();
        var dependencies = mavenResolver.resolveDependencies(groupId + ":" + artifactId + ":" + version);
        ingestArtifactIfNecessary(packageName, version, null, null);
        dependencies.forEach(d -> {
            try {
                ingestArtifactIfNecessary(d.groupId + Constants.mvnCoordinateSeparator + d.artifactId, d.version.toString(), null, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public static void batchIngestArtifacts(List<IngestedArtifact> artifacts) throws IllegalArgumentException {
        var keys = artifacts.stream() //
                .map(a -> toKey(a.packageName, a.version)) //
                .collect(toList());
        var alreadyIngestedArtifacts = KnowledgeBaseConnector.kbDao.areArtifactsIngested(keys);
        artifacts = artifacts.stream()
                .filter(a -> !alreadyIngestedArtifacts.contains(toKey(a.packageName, a.version)))
                .collect(Collectors.toList());
        artifacts.forEach(a -> {
            var groupId = a.packageName.split(":")[0];
            var artifactId = a.packageName.split(":")[1];
            if (!MavenUtilities.mavenArtifactExists(groupId, artifactId, a.version, a.artifactRepo)) {
                throw new IllegalArgumentException("Maven artifact '" + a.packageName + ":" + a.version
                        + "' could not be found in the repository of '"
                        + (a.artifactRepo == null ? MavenUtilities.MAVEN_CENTRAL_REPO : a.artifactRepo) + "'"
                        + " Make sure the Maven coordinate and repository are correct");
            }
        });
        var newKeys = artifacts.stream()
                .map(a -> toKey(a.packageName, a.version))
                .collect(toSet());
        KnowledgeBaseConnector.kbDao.batchInsertIngestedArtifacts(newKeys, "");
        for (var artifact : artifacts) {
            if (KnowledgeBaseConnector.kafkaProducer != null && KnowledgeBaseConnector.ingestTopic != null) {
                var jsonRecord = new JSONObject();
                jsonRecord.put("groupId", artifact.packageName.split(Constants.mvnCoordinateSeparator)[0]);
                jsonRecord.put("artifactId", artifact.packageName.split(Constants.mvnCoordinateSeparator)[1]);
                jsonRecord.put("version", artifact.version);
                if (artifact.artifactRepo != null && !artifact.artifactRepo.isEmpty()) {
                    jsonRecord.put("artifactRepository", artifact.artifactRepo);
                }
                if (artifact.date != null && artifact.date > 0) {
                    jsonRecord.put("date", artifact.date);
                }
                KafkaWriter.sendToKafka(KnowledgeBaseConnector.kafkaProducer, KnowledgeBaseConnector.ingestTopic, jsonRecord.toString());
            }
        }
    }

    public static class IngestedArtifact {
        final String packageName;
        final String version;
        final String artifactRepo;
        final Long date;

        public IngestedArtifact(String packageName, String version, String artifactRepo, Long date) {
            this.packageName = packageName;
            this.version = version;
            this.artifactRepo = artifactRepo;
            this.date = date;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IngestedArtifact that = (IngestedArtifact) o;
            if (!Objects.equals(packageName, that.packageName))
                return false;
            if (!Objects.equals(version, that.version))
                return false;
            if (!Objects.equals(artifactRepo, that.artifactRepo))
                return false;
            return Objects.equals(date, that.date);
        }

        @Override
        public int hashCode() {
            int result = packageName != null ? packageName.hashCode() : 0;
            result = 31 * result + (version != null ? version.hashCode() : 0);
            result = 31 * result + (artifactRepo != null ? artifactRepo.hashCode() : 0);
            result = 31 * result + (date != null ? date.hashCode() : 0);
            return result;
        }
    }
}
