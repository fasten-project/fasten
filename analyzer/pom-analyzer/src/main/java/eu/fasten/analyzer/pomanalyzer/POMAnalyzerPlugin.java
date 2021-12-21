/*
 * Copyright 2021 Delft University of Technology
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.analyzer.pomanalyzer;

import java.io.File;
import java.sql.Timestamp;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.plugins.AbstractKafkaPlugin;
import eu.fasten.core.plugins.DBConnector;


public class POMAnalyzerPlugin extends Plugin {

    public POMAnalyzerPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class POMAnalyzer extends AbstractKafkaPlugin implements DBConnector {
        
        private final Logger logger = LoggerFactory.getLogger(POMAnalyzer.class.getName());
        private static DSLContext dslContext;
        private boolean restartTransaction = false;
        private boolean processedRecord = false;

        private LinkedList<MavenCoordinate> workingSet = new LinkedList<>();
        private List<PomAnalyzerData> results = new LinkedList<>();
        

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            POMAnalyzer.dslContext = dslContexts.get(Constants.mvnForge);
        }
        


//        private void handleWorkingSet(KafkaRecordKind kafkaRecordKind) {
//            if (plugin.getWorkingSet().isPresent()) {
//                while (!plugin.getWorkingSet().get().isEmpty()) {
//                    // The plug-in might spend a lot of time in processing its working set,
//                    // therefore, we need to keep the Kafka consumers alive
//                    sendHeartBeat(connNorm);
//                    if (!prioTopics.isEmpty()) {
//                        sendHeartBeat(connPrio);
//                    }
//
//                    var record = plugin.getWorkingSet().get().pop();
//                    logger.info("Read working set message from " + kafkaRecordKind.toString() + " topic");
//                    processRecord(new ConsumerRecord<>(plugin.name() + "_working_set", 0, 0,
//                            "", record), System.currentTimeMillis() / 1000L, kafkaRecordKind);
//                    logger.info("Successfully processed working set message from " + kafkaRecordKind.toString() + " topic");
//                }
//            }
//        }
        
        
        public void consumeNew(String record) {
            var jsonRecord = new JSONObject(record);
            var payload = new JSONObject();
            if (jsonRecord.has("payload")) {
                 payload = jsonRecord.getJSONObject("payload");
             } else {
                 payload = jsonRecord;
             }
             try {
            	 var group = payload.getString("groupId").replaceAll("[\\n\\t ]", "");
                 var artifact = payload.getString("artifactId").replaceAll("[\\n\\t ]", "");
                 var version = payload.getString("version").replaceAll("[\\n\\t ]", "");
                 MavenCoordinate coord = new MavenCoordinate(group, artifact, version, null);
                 

                 
                 // TODO this is incomplete handling, as it 
                 var artifactRepository = payload.optString("artifactRepository", MavenUtilities.MAVEN_CENTRAL_REPO);
                 var repos = MavenUtilities.getRepos();
                 if (!artifactRepository.equals(MavenUtilities.MAVEN_CENTRAL_REPO)) {
                     repos.addFirst(artifactRepository);
                 }

                 
                 List<MavenCoordinate> deps = resolveDependencySet(coord);
                 
                 work(coord);
                 for(MavenCoordinate mc : deps) {
                	 work(mc);
                 }
                 
                 MavenUtilities.getRepos().remove(artifactRepository);
                 
             } catch (JSONException e) {
                 logger.error("Malformed input: " + payload.toString(), e);
                 this.pluginError = e;
                 return;
             }
        	
        	
        }
        
        private List<MavenCoordinate> resolveDependencySet(MavenCoordinate coord) {
        	// TODO use shrinkwrap
        	return null;
        }

		private void work(MavenCoordinate coord) {
        	// ...
        }
        
        
        @Override
        public void consume(String record) {
        	
        	
        	
//        	 convert record to MavenCoord
//        	 use shrinkwrap to resolve dependency set as List<MavenCoordinate>
//        	 foreach (coord : depset + requested artuifact) {
//        		 work (coord)
//        	 }
        	 
        	 workingSet.add(new MavenCoordinate("g", "a", "v", "p"));
        	    
        	
        	
        	
        	
        	
            pluginError = null;
            
            var data = new PomAnalyzerData();
            results.add(data);
            
            this.processedRecord = false;
            this.restartTransaction = false;
            logger.info("Consuming: " + record);
            var jsonRecord = new JSONObject(record);
            var payload = new JSONObject();
            if (jsonRecord.has("payload")) {
                payload = jsonRecord.getJSONObject("payload");
            } else {
                payload = jsonRecord;
            }
            try {
                data.artifact = payload.getString("artifactId").replaceAll("[\\n\\t ]", "");
                data.group = payload.getString("groupId").replaceAll("[\\n\\t ]", "");
                data.version = payload.getString("version").replaceAll("[\\n\\t ]", "");
               data.date = payload.optLong("date", -1L);
                data.artifactRepository = payload.optString("artifactRepository", MavenUtilities.MAVEN_CENTRAL_REPO);
            } catch (JSONException e) {
                logger.error("Malformed input: " + payload.toString(), e);
                this.pluginError = e;
                return;
            }
            var repos = MavenUtilities.getRepos();
            if (!data.artifactRepository.equals(MavenUtilities.MAVEN_CENTRAL_REPO)) {
                repos.addFirst(data.artifactRepository);
            }
            var pomUrl = payload.optString("pomUrl", null);
            
            
            
            
            
            
            final var product = data.group + Constants.mvnCoordinateSeparator + data.artifact
                    + Constants.mvnCoordinateSeparator + data.version;
            var dataExtractor = new DataExtractor(repos);
            try {
                if (pomUrl != null) {
                    var mavenCoordinate = dataExtractor.getMavenCoordinate(pomUrl);
                    logger.info("Extracted Maven coordinate: " + mavenCoordinate);
                    if (mavenCoordinate != null && !mavenCoordinate.contains("${")) {
                        String[] parts = mavenCoordinate.split(Constants.mvnCoordinateSeparator);
						data.group = parts[0];
                        data.artifact = parts[1];
                        data.version = parts[2];
                    }
                }
                if (data.date == -1) {
                    data.date = dataExtractor.extractReleaseDate(data.group, data.artifact, data.version, data.artifactRepository);
                }
                data.repoUrl = dataExtractor.extractRepoUrl(data.group, data.artifact, data.version);
                logger.info("Extracted repository URL " + data.repoUrl + " from " + product);
                data.dependencyData = dataExtractor.extractDependencyData(data.group, data.artifact, data.version);
                logger.info("Extracted dependency information from " + product);
                data.commitTag = dataExtractor.extractCommitTag(data.group, data.artifact, data.version);
                logger.info("Extracted commit tag from " + product);
                data.sourcesUrl = dataExtractor.generateMavenSourcesLink(data.group, data.artifact, data.version);
                logger.info("Generated link to Maven sources for " + product);
                data.packagingType = dataExtractor.extractPackagingType(data.group, data.artifact, data.version);
                logger.info("Extracted packaging type from " + product);
                data.projectName = dataExtractor.extractProjectName(data.group, data.artifact, data.version);
                logger.info("Extracted project name from " + product);
                data.parentCoordinate = dataExtractor.extractParentCoordinate(data.group, data.artifact, data.version);
                logger.info("Extracted parent coordinate from " + product);
            } catch (RuntimeException e) {
                logger.error("Error extracting data for " + product, e);
                this.pluginError = e;
                MavenUtilities.getRepos().remove(data.artifactRepository);
                return;
            }
            int transactionRestartCount = 0;
            do {
                try {
                    var metadataDao = new MetadataDao(dslContext);
                    addDependenciesToWorkingSet(data, metadataDao);

                    dslContext.transaction(transaction -> {
                        metadataDao.setContext(DSL.using(transaction));
                        long id;
                        try {
                        	
                        	id = saveToDatabase(data, metadataDao);
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
                    logger.error("Database transaction failed due to {} and retried for {} times(s)", expected.getMessage(), transactionRestartCount);
                }
                transactionRestartCount++;
            } while (restartTransaction && !processedRecord
                    && transactionRestartCount < Constants.transactionRestartLimit);
            
            MavenUtilities.getRepos().remove(data.artifactRepository);
        }

        public long saveToDatabase(PomAnalyzerData d, MetadataDao metadataDao) {
        	String product = d.group + Constants.mvnCoordinateSeparator + d.artifact; 
            final var packageId = metadataDao.insertPackage(product, Constants.mvnForge,
                    d.projectName, d.repoUrl, null);
            var packageVersionMetadata = new JSONObject();
            packageVersionMetadata.put("dependencyManagement",
                    (d.dependencyData.dependencyManagement != null)
                            ? d.dependencyData.dependencyManagement.toJSON() : null);
            packageVersionMetadata.put("dependencies",
                    (d.dependencyData.dependencies != null)
                            ? new JSONArray(d.dependencyData.dependencies) : null);
            packageVersionMetadata.put("commitTag", (d.commitTag != null) ? d.commitTag : "");
            packageVersionMetadata.put("sourcesUrl", (d.sourcesUrl != null) ? d.sourcesUrl : "");
            packageVersionMetadata.put("packagingType", (d.packagingType != null)
                    ? d.packagingType : "");
            packageVersionMetadata.put("parentCoordinate", (d.parentCoordinate != null)
                    ? d.parentCoordinate : "");
            Long artifactRepoId;
            if (d.artifactRepository == null) {
                artifactRepoId = null;
            } else if (d.artifactRepository.equals(MavenUtilities.MAVEN_CENTRAL_REPO)) {
                artifactRepoId = -1L;
            } else {
                artifactRepoId = metadataDao.insertArtifactRepository(d.artifactRepository);
            }
            final var packageVersionId = metadataDao.insertPackageVersion(packageId,
                    Constants.opalGenerator, d.version, artifactRepoId, null, this.getProperTimestamp(d.date),
                    packageVersionMetadata);
            for (var dep : d.dependencyData.dependencies) {
                var depProduct = dep.groupId + Constants.mvnCoordinateSeparator + dep.artifactId;
                final var depId = metadataDao.insertPackage(depProduct, Constants.mvnForge);
                metadataDao.insertDependency(packageVersionId, depId,
                        dep.getVersionConstraints(), null, null, null, dep.toJSON());
            }
            return packageVersionId;
        }

        private void addDependenciesToWorkingSet(PomAnalyzerData data, MetadataDao metadataDao) {
        	
        	for (var dep : data.dependencyData.dependencies) {
        		var depProduct = dep.groupId + Constants.mvnCoordinateSeparator + dep.artifactId;
        		
	            var depVersions = dep.getVersion().split(Pattern.quote(","));
	            for (String v : depVersions) {
	                if (MavenUtilities.mavenArtifactExists(dep.groupId, dep.artifactId, v, null) &&
	                        (metadataDao.getPackageVersion(depProduct, v) == null ||
	                                !metadataDao.isArtifactIngested(depProduct, v))) {
	                    
	                	var coordinate = new MavenCoordinate(dep.groupId, dep.artifactId, v, null);
						this.workingSet.add(coordinate);
	                    logger.info("Added dependency {} to the working set for further processing", coordinate);
	                } else {
	                    logger.warn("Dependency {}:{} already exists in KB or doesn't exist on Maven Central", depProduct, v);
	                }
	            }
        	}
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
        public List<SingleRecord> produceMultiple() {
        	var res = new LinkedList<SingleRecord>();
        	for(var data : results) {
        		res.add(serialize(data));
        	}
        	return res;
        }
        
        private static SingleRecord serialize(PomAnalyzerData d) {
            var json = new JSONObject();
            json.put("artifactId", d.artifact);
            json.put("groupId", d.group);
            json.put("version", d.version);
            json.put("packagingType", d.packagingType);

            json.put("date", d.date);
            json.put("repoUrl", (d.repoUrl != null) ? d.repoUrl : "");
            json.put("commitTag", (d.commitTag != null) ? d.commitTag : "");
            json.put("sourcesUrl", d.sourcesUrl);
            json.put("projectName", (d.projectName != null) ? d.projectName : "");
            json.put("parentCoordinate", (d.parentCoordinate != null) ? d.parentCoordinate : "");
            json.put("dependencyData", d.dependencyData.toJSON());
            json.put("forge", Constants.mvnForge);
            json.put("artifactRepository", d.artifactRepository);
            
            // TODO add sha or md5 hash
            
            var res = new SingleRecord();
            res.payload = json.toString();
            res.outputPath = getOutputPath(d);
            
            return res;
        }

        private static String getOutputPath(PomAnalyzerData d) {
        	// TODO use group
        	return File.separator + d.artifact.charAt(0) + File.separator
                    + d.artifact + File.separator + d.artifact + "_" + d.group + "_" + d.version + ".json";
		}
    }
}