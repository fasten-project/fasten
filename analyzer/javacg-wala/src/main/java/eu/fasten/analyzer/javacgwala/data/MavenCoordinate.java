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

package eu.fasten.analyzer.javacgwala.data;

import eu.fasten.core.data.RevisionCallGraph;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maven coordinate as g:a:v e.g. "com.google.guava:guava:jar:28.1-jre"
 */
public class MavenCoordinate {
    final String MAVEN_REPO = "https://repo.maven.apache.org/maven2/";

    private String groupID;
    private String artifactID;
    private String versionConstraint;
    private String timestamp;

    public void setGroupID(final String groupID) {
        this.groupID = groupID;
    }

    public void setArtifactID(final String artifactID) {
        this.artifactID = artifactID;
    }

    public void setVersionConstraint(final String versionConstraint) {
        this.versionConstraint = versionConstraint;
    }

    public void setTimestamp(final String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMAVEN_REPO() {
        return MAVEN_REPO;
    }

    public String getGroupID() {
        return groupID;
    }

    public String getArtifactID() {
        return artifactID;
    }

    public String getVersionConstraint() {
        return versionConstraint;
    }

    public MavenCoordinate(final String groupID, final String artifactID, final String version) {
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.versionConstraint = version;
    }

    public MavenCoordinate(final String groupID, final String artifactID, final String version, final String timestamp) {
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.versionConstraint = version;
        this.timestamp = timestamp;
    }

    public static MavenCoordinate fromString(final String coords) {
        var coord = coords.split(":");
        return new MavenCoordinate(coord[0], coord[1], coord[2]);
    }

    public String getProduct() {
        return groupID + "." + artifactID;
    }

    public String getCoordinate() {
        return groupID + ":" + artifactID + ":" + versionConstraint;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String toURL() {
        final StringBuilder url = new StringBuilder(MAVEN_REPO)
                .append(this.groupID.replace('.', '/'))
                .append("/")
                .append(this.artifactID)
                .append("/")
                .append(this.versionConstraint);
        return url.toString();
    }

    public String toJarUrl() {
        final StringBuilder url = new StringBuilder(this.toURL())
                .append("/")
                .append(this.artifactID)
                .append("-")
                .append(this.versionConstraint)
                .append(".jar");
        return url.toString();
    }

    public String toPomUrl() {
        final StringBuilder url = new StringBuilder(this.toURL())
                .append("/")
                .append(this.artifactID)
                .append("-")
                .append(this.versionConstraint)
                .append(".pom");
        return url.toString();
    }

    /**
     * A set of methods for downloading POM and JAR files given Maven coordinates.
     */
    public static class MavenResolver {
        private static Logger logger = LoggerFactory.getLogger(MavenResolver.class);

        /**
         * Returns information about the dependencies of the indicated artifact.
         *
         * @param mavenCoordinate Maven coordinate of an artifact.
         * @return A java List of a given artifact's dependencies in FastenJson Dependency format.
         */
        public static List<List<RevisionCallGraph.Dependency>> resolveDependencies(final String mavenCoordinate) {

            final var dependencies = new ArrayList<List<RevisionCallGraph.Dependency>>();

            try {
                final var pom = new SAXReader().read(
                        new ByteArrayInputStream(
                                downloadPom(mavenCoordinate).orElseThrow(RuntimeException::new).getBytes()
                        )
                );
                List<Node> profiles = pom.selectNodes("//*[local-name() = 'profile']");

                if (profiles.size() > 0) {
                    for (var profile : profiles) {
                        var dependenciesNode =
                                profile.selectSingleNode("//*[local-name() ='dependencies']");
                        dependencies.add(resolveDependencies(dependenciesNode));
                    }
                } else {
                    var dependenciesNode =
                            pom.selectSingleNode("//*[local-name() ='dependencies']");
                    dependencies.add(resolveDependencies(dependenciesNode));
                }

            } catch (DocumentException | FileNotFoundException e) {
                logger.error("Error parsing POM file for: " + mavenCoordinate, e);
            }
            return dependencies;
        }

        /**
         * Return a list of dependencies in given profile node or of the entire project if profiles
         * were not present in pom.xml.
         *
         * @param node Dependencies node from profile or entire project
         * @return List of dependencies
         */
        private static List<RevisionCallGraph.Dependency> resolveDependencies(Node node) {
            final var depList = new ArrayList<RevisionCallGraph.Dependency>();

            for (var depNode : node.selectNodes("./*[local-name() = 'dependency']")) {
                final var groupId = depNode.selectSingleNode("./*[local-name() = 'groupId']").getStringValue();
                final var artifactId = depNode.selectSingleNode("./*[local-name() = 'artifactId']").getStringValue();
                final var versionSpec = depNode.selectSingleNode("./*[local-name() = 'version']");

                final String version;
                if (versionSpec != null) {
                    version = versionSpec.getStringValue();
                } else {
                    version = "*";
                }

                final RevisionCallGraph.Dependency dependency = new RevisionCallGraph.Dependency(
                        "mvn",
                        groupId + "." + artifactId,
                        Collections.singletonList(new RevisionCallGraph.Constraint(version, version)));
                depList.add(dependency);
            }

            return depList;
        }

        /**
         * Download a POM file indicated by the provided Maven coordinate
         *
         * @param mavenCoordinate A Maven coordinate in the for "groupId:artifactId:version"
         * @return The contents of the downloaded POM file as a string
         */
        public static Optional<String> downloadPom(final String mavenCoordinate) throws FileNotFoundException {
            return httpGetToFile(fromString(mavenCoordinate).toPomUrl(), ".pom").
                    flatMap(f -> fileToString(f));
        }

        /**
         * Download a JAR file indicated by the provided Maven coordinate
         *
         * @param mavenCoordinate A Maven coordinate in the for "groupId:artifactId:version"
         * @return A temporary file on the filesystem
         */
        public static Optional<File> downloadJar(final String mavenCoordinate) throws FileNotFoundException {
            logger.debug("Downloading JAR for " + mavenCoordinate);
            return httpGetToFile(fromString(mavenCoordinate).toJarUrl(), ".jar");
        }

        /**
         * Utility function that reads the contents of a file to a String
         */
        private static Optional<String> fileToString(final File f) {
            logger.trace("Loading file as string: " + f.toString());
            try {
                final var fr = new BufferedReader(new FileReader(f));
                final StringBuilder result = new StringBuilder();
                String line;
                while ((line = fr.readLine()) != null) {
                    result.append(line);
                }
                fr.close();
                return Optional.of(result.toString());

            } catch (IOException e) {
                logger.error("Cannot read from file: " + f.toString(), e);
                return Optional.empty();
            }
        }

        /**
         * Utility function that stores the contents of GET request to a temporary file
         */
        private static Optional<File> httpGetToFile(final String url, final String suffix) throws FileNotFoundException {
            logger.debug("HTTP GET: " + url);

            try {
                //TODO: Download artifacts in configurable shared location
                final var tempFile = Files.createTempFile("fasten", suffix);

                final InputStream in = new URL(url).openStream();
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                in.close();

                return Optional.of(new File(tempFile.toAbsolutePath().toString()));
            } catch (FileNotFoundException e) {
                logger.error("Could not find URL: " + url, e);
                throw e;
            } catch (Exception e) {
                logger.error("Error retrieving URL: " + url, e);
                return Optional.empty();
            }
        }

        /**
         * A utility method to get a POM file and its timestamp from a URL
         * Please note that this might not be the most efficient way but it works and can be improved
         * later.
         *
         * @param fileURL
         * @param dest
         * @throws IOException
         */
        public Date getFileAndTimeStamp(final String fileURL, final String dest) throws IOException {

            final String fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1);
            final StringJoiner pathJoin = new StringJoiner(File.separator);
            final var destFile = pathJoin.add(dest).add(fileName).toString();

            logger.debug("Filename: " + fileName + " | " + "dest: " + destFile);

            final URL url = new URL(fileURL);
            final HttpURLConnection con = (HttpURLConnection) url.openConnection();

            final Date timestamp = new Date(con.getLastModified());

            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {

                logger.debug("Okay status!");

                final BufferedReader input = new BufferedReader(new InputStreamReader(con.getInputStream()), 8192);
                final BufferedWriter output = new BufferedWriter(new FileWriter(new File(destFile)));

                String line;
                while ((line = input.readLine()) != null) {
                    output.write(line);
                    output.newLine();
                }

                output.close();
            }
            con.disconnect();

            return timestamp;
        }


    }
}
