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

package eu.fasten.analyzer.javacgopal.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import eu.fasten.core.data.Constants;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maven coordinate as g:a:v e.g. "com.google.guava:guava:jar:28.1-jre".
 */
public class MavenCoordinate {

    private List<String> mavenRepos;

    private final String groupID;
    private final String artifactID;
    private final String versionConstraint;

    private final String packaging;

    public List<String> getMavenRepos() {
        return mavenRepos;
    }

    public void setMavenRepos(List<String> mavenRepos) {
        this.mavenRepos = mavenRepos;
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

    public String getPackaging() {
        return packaging;
    }

    /**
     * Construct MavenCoordinate form groupID, artifactID, and version.
     *
     * @param groupID    GroupID
     * @param artifactID ArtifactID
     * @param version    Version
     */
    public MavenCoordinate(final String groupID, final String artifactID, final String version,
                           final String packaging) {
        this.mavenRepos = System.getenv("MVN_REPO") != null
                ? Arrays.asList(System.getenv("MVN_REPO").split(";"))
                : Collections.singletonList("https://repo.maven.apache.org/maven2/");
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.versionConstraint = version;
        this.packaging = packaging;
    }

    /**
     * Construct MavenCoordinate form json.
     *
     * @param kafkaConsumedJson json representation of Meven coordinate
     */
    public MavenCoordinate(final JSONObject kafkaConsumedJson) throws JSONException {
        this.mavenRepos = System.getenv("MVN_REPO") != null
                ? Arrays.asList(System.getenv("MVN_REPO").split(";"))
                : Collections.singletonList("https://repo.maven.apache.org/maven2/");
        this.groupID = kafkaConsumedJson.getString("groupId");
        this.artifactID = kafkaConsumedJson.getString("artifactId");
        this.versionConstraint = kafkaConsumedJson.getString("version");
        this.packaging = kafkaConsumedJson.optString("packagingType", "jar");
    }

    /**
     * Convert string to MavenCoordinate.
     *
     * @param coords String representation of a coordinate
     * @return MavenCoordinate
     */
    public static MavenCoordinate fromString(final String coords, final String packaging) {
        var coordinate = coords.split(Constants.coordinatePartsJoin);
        return new MavenCoordinate(coordinate[0], coordinate[1], coordinate[2], packaging);
    }

    public String getProduct() {
        return groupID + Constants.coordinatePartsJoin + artifactID;
    }

    public String getCoordinate() {
        return groupID + Constants.coordinatePartsJoin + artifactID
                + Constants.coordinatePartsJoin + versionConstraint;
    }

    /**
     * Convert to URL.
     *
     * @return URL
     */
    public String toURL(String repo) {
        return repo + this.groupID.replace('.', '/') + "/" + this.artifactID
                + "/" + this.versionConstraint;
    }

    /**
     * Convert to product URL.
     *
     * @return product URL
     */
    public String toProductUrl(String repo, String extension) {
        return this.toURL(repo) + "/" + this.artifactID + "-" + this.versionConstraint
                + "." + extension;
    }

    /**
     * A set of methods for downloading POM and JAR files given Maven coordinates.
     */
    public static class MavenResolver {
        private static final Logger logger = LoggerFactory.getLogger(MavenResolver.class);
        private static final String[] packaging = {"jar", "war", "zip", "ear", "rar", "ejb", "par",
                "aar", "car", "nar", "kar"};
        private static final String[] defaultPackaging = {"zip", "aar", "tar.gz", "jar"};

        /**
         * Download a JAR file indicated by the provided Maven coordinate.
         *
         * @param mavenCoordinate A Maven coordinate in the for "groupId:artifactId:version"
         * @return A temporary file on the filesystem
         */
        public File downloadArtifact(final MavenCoordinate mavenCoordinate)
                throws FileNotFoundException {
            logger.debug("Downloading JAR for " + mavenCoordinate);
            var found = false;
            Optional<File> jar = Optional.empty();
            var repos = mavenCoordinate.getMavenRepos();
            for (int i = 0; i < repos.size(); i++) {
                try {
                    if (Arrays.asList(packaging).contains(mavenCoordinate.getPackaging())) {
                        found = true;
                        jar = httpGetFile(mavenCoordinate
                                .toProductUrl(repos.get(i), mavenCoordinate.getPackaging()));
                    }
                } catch (FileNotFoundException | MalformedURLException | UnknownHostException e) {
                    found = false;
                    logger.error("Could not find URL: {}", e.getMessage());
                }
                if (jar.isPresent()) {
                    return jar.get();
                } else if (found && i == repos.size() - 1) {
                    throw new RuntimeException();
                } else if (found) {
                    continue;
                }

                for (var s : defaultPackaging) {
                    try {
                        found = true;
                        jar = httpGetFile(mavenCoordinate.toProductUrl(repos.get(i), s));
                    } catch (FileNotFoundException | MalformedURLException | UnknownHostException e) {
                        found = false;
                        logger.error("Could not find URL: {}", e.getMessage());
                    }
                    if (jar.isPresent()) {
                        return jar.get();
                    } else if (found && i == repos.size() - 1) {
                        throw new RuntimeException();
                    } else if (found) {
                        break;
                    }
                }
            }
            throw new FileNotFoundException(
                    mavenCoordinate.toURL(mavenCoordinate.getMavenRepos().size() > 0
                            ? mavenCoordinate.getMavenRepos().get(0)
                            : "no repos specified") + " | "
                            + mavenCoordinate.getPackaging());
        }

        /**
         * Utility function that stores the contents of GET request to a temporary file.
         */
        private static Optional<File> httpGetFile(final String url) throws FileNotFoundException,
                MalformedURLException, UnknownHostException {
            try {
                logger.debug("HTTP GET: " + url);

                final var packaging = url.substring(url.lastIndexOf("."));
                final var tempFile = Files.createTempFile("fasten", packaging);

                final InputStream in = new URL(url).openStream();
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                in.close();

                return Optional.of(new File(tempFile.toAbsolutePath().toString()));
            } catch (FileNotFoundException | MalformedURLException | UnknownHostException e) {
                logger.error("Couldn't find an artifact: {}", url);
                throw e;
            } catch (IOException e) {
                logger.error("IO exception occurred while retrieving URL: {}", url);
                return Optional.empty();
            }
        }
    }
}
