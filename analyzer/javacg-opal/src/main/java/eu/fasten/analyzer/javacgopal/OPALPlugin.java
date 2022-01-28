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

import static eu.fasten.analyzer.javacgopal.data.CGAlgorithm.CHA;
import static eu.fasten.analyzer.javacgopal.data.CallPreservationStrategy.ONLY_STATIC_CALLSITES;
import static eu.fasten.core.maven.utils.MavenUtilities.MAVEN_CENTRAL_REPO;
import static java.lang.System.currentTimeMillis;

import java.io.File;
import java.util.Optional;

import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.analyzer.javacgopal.data.OPALPartialCallGraphConstructor;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.EmptyCallGraphException;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.plugins.AbstractKafkaPlugin;

public class OPALPlugin extends Plugin {

    public OPALPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class OPAL extends AbstractKafkaPlugin {

        private final Logger logger = LoggerFactory.getLogger(getClass());

        private PartialJavaCallGraph graph;
        private String outputPath;

        @Override
        public void consume(String kafkaRecord, ProcessingLane l) {
            logger.info("Consuming {}", kafkaRecord);
            
            pluginError = null;
            outputPath = null;
            graph = null;

            var json = new JSONObject(kafkaRecord);
            if (json.has("payload")) {
                json = json.getJSONObject("payload");
            }
            var artifactRepository = fixResetAndGetArtifactRepo(json);
            final var mavenCoordinate = new MavenCoordinate(json);
            long startTime = System.currentTimeMillis();
            try {
                // Generate CG and measure construction duration.
                logger.info("[CG-GENERATION] [UNPROCESSED] [-1] [" + mavenCoordinate.getCoordinate() + "] [NONE] ");
                long date = json.optLong("releaseDate", -1);
				this.graph = OPALPartialCallGraphConstructor.createExtendedRevisionJavaCallGraph(mavenCoordinate,
                        CHA, date, artifactRepository, ONLY_STATIC_CALLSITES);
                long duration = currentTimeMillis() - startTime; 

                if (this.graph.isCallGraphEmpty()) {
                    throw new EmptyCallGraphException();
                }

                var groupId = graph.product.split(Constants.mvnCoordinateSeparator)[0];
                var artifactId = graph.product.split(Constants.mvnCoordinateSeparator)[1];
                var version = graph.version;
                // TODO Use less confusing terminology, as graph.product != product (gid:aid vs. gid_aid_ver)
                var product = artifactId + "_" + groupId + "_" + version;

                var firstLetter = artifactId.substring(0, 1);

                outputPath = File.separator + Constants.mvnForge + File.separator
                        + firstLetter + File.separator
                        + artifactId + File.separator + product + ".json";

                logger.info("[CG-GENERATION] [SUCCESS] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [NONE] ");

            } catch (OPALException | EmptyCallGraphException e) {
                setError(mavenCoordinate, startTime, e, "CG-GENERATION");
            } catch (MissingArtifactException e) {
                setError(mavenCoordinate, startTime, e, "ARTIFACT-DOWNLOAD");
            }
        }

        private static String fixResetAndGetArtifactRepo(JSONObject json) {
            var repo = json.optString("artifactRepository", MAVEN_CENTRAL_REPO);
            if(!repo.endsWith("/")) {
                repo = repo + "/";
            }
            json.put("artifactRepository", repo);
            return repo;
        }

        private void setError(final MavenCoordinate mavenCoordinate, long startTime, Exception e, String part) {
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1000000; // Compute duration in ms.
            logger.error("[" + part + "] [FAILED] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [" + e.getClass().getSimpleName() + "] " + e.getMessage(), e);
            this.pluginError = e;
        }

        @Override
        public Optional<String> produce() {
            if (this.graph != null && !this.graph.isCallGraphEmpty()) {
                return Optional.of(JSONUtils.toJSONString(graph));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public String getOutputPath() {
            return outputPath;
        }

        @Override
        public boolean isStaticMembership() {
            return true; // The OPAL plugin relies on static members in a consumer group (using a K8s StatefulSet).
        }

        @Override
        public long getMaxConsumeTimeout() {
            return Integer.MAX_VALUE; // It can take very long to generate OPAL CG's.
        }

        @Override
        public long getSessionTimeout() {
            return 1800000; // Due to static membership we also want to tune the session timeout to 30 minutes.
        }
    }
}
