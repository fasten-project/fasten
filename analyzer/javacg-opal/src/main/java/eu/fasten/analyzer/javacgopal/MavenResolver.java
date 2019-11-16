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

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenArtifactInfo;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import eu.fasten.core.data.RevisionCallGraph;

/**
 * For downloading, resolving and all operations related to maven artifacts.
 */
public class MavenResolver {

    /**
     * Maven coordinate as g:a:v e.g. "com.google.guava:guava:jar:28.1-jre"
     */
    public static class MavenCoordinate {
        private String groupID;
        private String artifactID;
        private String version;

        public MavenCoordinate() {
        }

        public MavenCoordinate(String groupID, String artifactID, String version) {
            this.groupID = groupID;
            this.artifactID = artifactID;
            this.version = version;
        }

        public String getProduct() {
            return groupID + "." + artifactID;
        }

        public String getCoordinate() {
            return groupID + ":" + artifactID + ":" + version;
        }

        public void setGroupID(String groupID) {
            this.groupID = groupID;
        }

        public void setArtifactID(String artifactID) {
            this.artifactID = artifactID;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getGroupID() {
            return groupID;
        }

        public String getArtifactID() {
            return artifactID;
        }

        public String getVersion() {
            return version;
        }
    }

    /**
     * Resolves the dependency tree of a given artifact.
     * @param mavenCoordinate Maven coordinate of an artifact.
     * @return A java List of a given artifact's dependencies in FastenJson Dependency format.
     */
    public static List<List<RevisionCallGraph.Dependency>> resolveDependencies(String mavenCoordinate) {

        MavenResolvedArtifact artifact = Maven.resolver().resolve(mavenCoordinate).withoutTransitivity().asSingle(MavenResolvedArtifact.class);

        List<List<RevisionCallGraph.Dependency>> dependencies = new ArrayList<>();

        for (MavenArtifactInfo i : artifact.getDependencies()) {
            RevisionCallGraph.Dependency dependency = new RevisionCallGraph.Dependency(
                "mvn",
                i.getCoordinate().getGroupId() +"."+ i.getCoordinate().getArtifactId(),
                Arrays.asList(new RevisionCallGraph.Constraint("[" + i.getCoordinate().getVersion() + "]")));
            dependencies.add((List<RevisionCallGraph.Dependency>) dependency);
            //TODO get the pom file from maven repository and extract version ranges.
        }

        return dependencies;
    }

    /**
     * Downloads and artifact and returns its file.
     * @param coordinate Maven coordinate indicating an artifact on maven repository.
     * @return Java File of the given coordinate.
     */
    public static File downloadArtifact(String coordinate) {

        return Maven.resolver().resolve(coordinate).withoutTransitivity().asSingleFile();

    }
}
