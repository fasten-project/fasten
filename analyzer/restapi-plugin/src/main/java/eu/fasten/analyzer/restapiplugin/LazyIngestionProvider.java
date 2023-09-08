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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.c0ps.maven.MavenUtilities;
import dev.c0ps.maven.data.GA;
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.resolution.NativeMavenResolver;
import eu.fasten.core.utils.HttpUtils;

public class LazyIngestionProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(LazyIngestionProvider.class);
    
    // TODO Consider making this a Spring @Component

    private static boolean hasArtifactBeenIngested(String packageName, String version) {
        switch(KnowledgeBaseConnector.forge){
            case Constants.mvnForge: {
                return KnowledgeBaseConnector.kbDao.isArtifactIngested(toMvnKey(packageName, version));
            }
            case Constants.pypiForge: {
                return KnowledgeBaseConnector.kbDao.isArtifactIngested(toPypiKey(packageName, version));
            }
            default:
                return false;
        }
    }


    private static String toMvnKey(String packageName, String version) {
        var key = String.format("%s:jar:%s-PRIORITY", packageName, version);
        return key;
    }

    private static String toPypiKey(String packageName, String version) {
        var key = String.format("%s:%s", packageName, version);
        return key;
    }

    public boolean ingestArtifactIfNecessary(String packageName, String version, String artifactRepo, Long date) {
        switch(KnowledgeBaseConnector.forge){
            case Constants.mvnForge: {
                return ingestMvnArtifactIfNecessary(packageName, version, artifactRepo, date);
            }
            case Constants.pypiForge: {
                return ingestPypiArtifactIfNecessary(packageName, version);
            }
            default:
                return false;
        }
    }

    /**
     * @return whether it was necessary to ingest artifact
     */
    public boolean ingestMvnArtifactIfNecessary(String packageName, String version, String artifactRepo, Long date) {

        if(hasArtifactBeenIngested(packageName, version)) {
                return false;
        }

        var ga = GA.fromString(packageName);
        if(artifactRepo == null || artifactRepo.isEmpty()) {
            artifactRepo = MavenUtilities.MAVEN_CENTRAL_REPO;
        }

        // way too expensive! this must be moved to maven resolver
//        if (!MavenUtilities.mavenArtifactExists(ga.groupId, ga.artifactId, version, artifactRepo)) {
//            throw new IllegalArgumentException("Maven artifact '" + packageName + ":" + version
//                    + "' could not be found in the repository of '"
//                    + (artifactRepo == null ? MavenUtilities.MAVEN_CENTRAL_REPO : artifactRepo) + "'."
//                    + " Make sure the Maven coordinate and repository are correct");
//        }

        var jsonRecord = new JSONObject();
        jsonRecord.put("groupId", ga.groupId);
        jsonRecord.put("artifactId", ga.artifactId);
        jsonRecord.put("version", version);
        jsonRecord.put("artifactRepository", artifactRepo);
        if (date != null && date > 0) {
            // TODO why is the date necessary?
            jsonRecord.put("date", date);
        }
        if (KnowledgeBaseConnector.kafkaProducer != null && KnowledgeBaseConnector.ingestTopic != null) {
            KafkaWriter.sendToKafka(KnowledgeBaseConnector.kafkaProducer, KnowledgeBaseConnector.ingestTopic, jsonRecord.toString());
        }
        return true;
    }
    
    /**
     * @return whether it was necessary to ingest artifact
     */
    public boolean ingestPypiArtifactIfNecessary(String packageName, String version) {
        var query = "https://pypi.org/pypi/" + packageName + "/json";
        String result;
        try {
            result = HttpUtils.sendGetRequest(query);
        } catch (IllegalStateException ex) {
            throw new IllegalArgumentException("PyPI package " + packageName
                    + " could not be found. Make sure the PyPI coordinate is correct");
        }
        if (!hasArtifactBeenIngested(packageName, version)) {

            //Check if the specific package version exists on PyPI
            JsonObject releases = JsonParser.parseString(result).getAsJsonObject()
                                    .get("releases").getAsJsonObject();
            if (!releases.has(version)) {
                throw new IllegalArgumentException("PyPI package version " + packageName + ":" + version
                + " could not be found. Make sure the PyPI coordinate is correct");
            }

            var projectInfo = new JSONObject(result);
            var jsonRecord = new JSONObject();
            jsonRecord.put("title", packageName + " " + version);
            jsonRecord.put("project", projectInfo);
            jsonRecord.put("ingested", true);
            KnowledgeBaseConnector.kbDao.insertIngestedArtifact(toPypiKey(packageName, version), "0.1.1-SNAPSHOT");
            if (KnowledgeBaseConnector.kafkaProducer != null && KnowledgeBaseConnector.ingestTopic != null) {
                KafkaWriter.sendToKafka(KnowledgeBaseConnector.kafkaProducer, KnowledgeBaseConnector.ingestTopic, jsonRecord.toString());
            }
            return true;
        }
        return false;
    }

    public void ingestArtifactWithDependencies(String packageName, String version) throws IllegalArgumentException, IOException {
        switch(KnowledgeBaseConnector.forge){
            case Constants.mvnForge: {
                ingestMvnArtifactWithDependencies(packageName, version);
                break;
            }
            case Constants.pypiForge: {
                ingestPypiArtifactWithDependencies(packageName, version);
                break;
            }
        }
    }

    public void ingestMvnArtifactWithDependencies(String packageName, String version) throws IllegalArgumentException {
        var ga = GA.fromString(packageName);
        ingestMvnArtifactIfNecessary(packageName, version, null, null);
        var mavenResolver = new NativeMavenResolver();
        var dependencies = mavenResolver.resolveDependencies(ga.groupId + ":" + ga.artifactId + ":" + version);
        ingestMvnArtifactIfNecessary(packageName, version, null, null);
        dependencies.forEach(d -> {
            ingestMvnArtifactIfNecessary(d.getGroupId() + Constants.mvnCoordinateSeparator + d.getArtifactId(), d.version.toString(), null, null);
        });
    }

    public void ingestPypiArtifactWithDependencies(String packageName, String version) throws IllegalArgumentException, IOException {
        var query = KnowledgeBaseConnector.dependencyResolverAddress+"/dependencies/"+packageName+"/"+version;
        var result = HttpUtils.sendGetRequest(query);
        result = result.replaceAll("\\s+","");
        JsonArray dependencyList = JsonParser.parseString(result).getAsJsonArray();
        for (var coordinate : dependencyList) {
            JsonObject jsonObject = coordinate.getAsJsonObject();
            String dependencyPackage = jsonObject.get("product").getAsString();
            String dependencyVersion = jsonObject.get("version").getAsString();
            ingestPypiArtifactIfNecessary(dependencyPackage, dependencyVersion);
        }
    }

    public void batchIngestArtifacts(List<IngestedArtifact> artifacts) throws IllegalArgumentException {
        var keys = artifacts.stream() //
                .map(a -> toMvnKey(a.packageName, a.version)) //
                .collect(toList());
        var alreadyIngestedArtifacts = KnowledgeBaseConnector.kbDao.areArtifactsIngested(keys);
        artifacts = artifacts.stream()
                .filter(a -> !alreadyIngestedArtifacts.contains(toMvnKey(a.packageName, a.version)))
                .collect(toList());
        // way too expensive! this must be moved to maven resolver
//        artifacts.forEach(a -> {
//            var ga = GA.fromString(a.packageName);
//            if (!MavenUtilities.mavenArtifactExists(ga.groupId, ga.artifactId, a.version, a.artifactRepo)) {
//                throw new IllegalArgumentException("Maven artifact '" + a.packageName + ":" + a.version
//                        + "' could not be found in the repository of '"
//                        + (a.artifactRepo == null ? MavenUtilities.MAVEN_CENTRAL_REPO : a.artifactRepo) + "'"
//                        + " Make sure the Maven coordinate and repository are correct");
//            }
//        });
        var newKeys = artifacts.stream()
                .map(a -> toMvnKey(a.packageName, a.version))
                .collect(toSet());
        KnowledgeBaseConnector.kbDao.batchInsertIngestedArtifacts(newKeys, "");
        for (var artifact : artifacts) {
            if (KnowledgeBaseConnector.kafkaProducer != null && KnowledgeBaseConnector.ingestTopic != null) {
                var jsonRecord = new JSONObject();
                var ga = GA.fromString(artifact.packageName);
                jsonRecord.put("groupId", ga.groupId);
                jsonRecord.put("artifactId", ga.artifactId);
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
