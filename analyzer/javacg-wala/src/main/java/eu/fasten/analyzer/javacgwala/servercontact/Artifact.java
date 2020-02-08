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


package eu.fasten.analyzer.javacgwala.servercontact;

import java.util.StringTokenizer;

public class Artifact {

    private long artifactNO;
    private String groupId;
    private String artifactId;
    private String version;
    private String date;

    public Artifact() {
    }

    /**
     * Construct an artifact.
     *
     * @param artifactNO Artifact number
     * @param firstName  Group ID
     * @param lastName   Artifact ID
     * @param version    Version
     * @param date       Date
     */
    public Artifact(long artifactNO, String firstName, String lastName,
                    String version, String date) {
        this.artifactNO = artifactNO;
        this.groupId = firstName;
        this.artifactId = lastName;
        this.version = version;
        this.date = date;
    }

    /**
     * Parse a csv-format-string representation of an artifact.
     *
     * @param csvStr String representing an artifact
     */
    public void parseString(String csvStr) {
        StringTokenizer st = new StringTokenizer(csvStr, ",");
        artifactNO = Integer.parseInt(st.nextToken());
        groupId = st.nextToken();
        artifactId = st.nextToken();
        version = st.nextToken();
        date = st.nextToken();
    }


    public long getArtifactNO() {
        return artifactNO;
    }

    public void setArtifactNO(long artifactNO) {
        this.artifactNO = artifactNO;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "Artifact{"
                + "artifactId=" + artifactId
                + ", groupId='" + groupId + '\''
                + ", lastName='" + artifactId + '\''
                + ", version='" + version + '\''
                + ", date='" + date + '\''
                + '}';
    }
}