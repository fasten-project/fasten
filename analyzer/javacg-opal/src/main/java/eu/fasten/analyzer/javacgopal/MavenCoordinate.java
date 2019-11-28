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

/**
 * Maven coordinate as g:a:v e.g. "com.google.guava:guava:jar:28.1-jre"
 */
public class MavenCoordinate {
    final String MAVEN_REPO = "https://repo.maven.apache.org/maven2/";

    String groupID;
    String artifactID;
    String versionConstraint;
    String timestamp;

    public MavenCoordinate(String groupID, String artifactID, String version) {
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.versionConstraint = version;
    }

    public MavenCoordinate(String groupID, String artifactID, String version, String timestamp) {
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.versionConstraint = version;
        this.timestamp = timestamp;
    }

    public static MavenCoordinate fromString(String coords) {
        var coord = coords.split(":");
        return new MavenCoordinate(coord[0], coord[1], coord[2]);
    }

    public String getProduct() {
        return groupID + "." + artifactID;
    }

    public String getCoordinate() {
        return groupID + ":" + artifactID + ":" + versionConstraint;
    }

    public String toURL()  {
        StringBuilder url = new StringBuilder(MAVEN_REPO)
            .append(this.groupID.replace('.', '/'))
            .append("/")
            .append(this.artifactID)
            .append("/")
            .append(this.versionConstraint);
        return url.toString();
    }

    public String toJarUrl() {
        StringBuilder url = new StringBuilder(this.toURL())
            .append("/")
            .append(this.artifactID)
            .append("-")
            .append(this.versionConstraint)
            .append(".jar");
        return url.toString();
    }

    public String toPomUrl() {
        StringBuilder url = new StringBuilder(this.toURL())
            .append("/")
            .append(this.artifactID)
            .append("-")
            .append(this.versionConstraint)
            .append(".pom");
        return url.toString();
    }
}
