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

package eu.fasten.core.data.opal;

import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Maven coordinate as g:a:v e.g. "com.google.guava:guava:jar:28.1-jre".
 */
public class MavenCoordinate {

    private LinkedList<String> mavenRepos;

    private final String groupID;
    private final String artifactID;
    private final String versionConstraint;

    private final String packaging;

    public LinkedList<String> getMavenRepos() {
        return mavenRepos;
    }

    public void setMavenRepos(List<String> mavenRepos) {
        this.mavenRepos = new LinkedList<>(mavenRepos);
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
        this.mavenRepos = MavenUtilities.getRepos();
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.versionConstraint = version;
        this.packaging = packaging;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MavenCoordinate that = (MavenCoordinate) o;

        return this.getCoordinate().equals(that.getCoordinate());
    }

    @Override
    public int hashCode() {
        return this.getCoordinate().hashCode();
    }

    /**
     * Construct MavenCoordinate form json.
     *
     * @param kafkaConsumedJson json representation of Maven coordinate
     */
    public MavenCoordinate(final JSONObject kafkaConsumedJson) throws JSONException {
        this.mavenRepos = MavenUtilities.getRepos();
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
        var coordinate = coords.split(Constants.mvnCoordinateSeparator);
        return new MavenCoordinate(coordinate[0], coordinate[1], coordinate[2], packaging);
    }

    public String getProduct() {
        return groupID + Constants.mvnCoordinateSeparator + artifactID;
    }

    public String getCoordinate() {
        return groupID + Constants.mvnCoordinateSeparator + artifactID
                + Constants.mvnCoordinateSeparator + versionConstraint;
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
        public File downloadArtifact(final MavenCoordinate mavenCoordinate, String artifactRepo)
                throws MissingArtifactException {
            var found = false;
            Optional<File> jar = Optional.empty();
            var repos = mavenCoordinate.getMavenRepos();
            if (artifactRepo != null && !artifactRepo.isEmpty() && !artifactRepo.equals(MavenUtilities.MAVEN_CENTRAL_REPO)) {
                repos.addFirst(artifactRepo);
            }
            for (int i = 0; i < repos.size(); i++) {

                long startTime = System.nanoTime();

                try {
                    if (Arrays.asList(packaging).contains(mavenCoordinate.getPackaging())) {
                        found = true;
                        jar = httpGetFile(mavenCoordinate
                                .toProductUrl(repos.get(i), mavenCoordinate.getPackaging()));
                    }
                } catch (MissingArtifactException e) {
                    found = false;

                    long duration = computeDurationInMs(startTime);
                    logger.warn("[ARTIFACT-DOWNLOAD] [UNPROCESSED] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [" + e.getClass().getSimpleName() + "] Artifact couldn't be retrieved for repo: " + repos.get(i), e);
                }

                if (jar.isPresent()) {
                    long duration = computeDurationInMs(startTime);
                    logger.info("[ARTIFACT-DOWNLOAD] [SUCCESS] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [NONE] Artifact retrieved from repo: " + repos.get(i));
                    return jar.get();
                } else if (found && i == repos.size() - 1) {
                    throw new MissingArtifactException("Artifact couldn't be retrieved for repo: " + repos.get(i), null);
                } else if (found) {
                    continue;
                }

                for (var s : defaultPackaging) {
                    startTime = System.nanoTime();
                    try {
                        found = true;
                        jar = httpGetFile(mavenCoordinate.toProductUrl(repos.get(i), s));
                    } catch (MissingArtifactException e) {
                        found = false;

                        long duration = computeDurationInMs(startTime);
                        logger.warn("[ARTIFACT-DOWNLOAD] [UNPROCESSED] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [" + e.getClass().getSimpleName() + "] Artifact couldn't be retrieved for repo: " + repos.get(i), e);
                    }

                    if (jar.isPresent()) {long duration = computeDurationInMs(startTime);
                        logger.info("[ARTIFACT-DOWNLOAD] [SUCCESS] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [NONE] Artifact retrieved from repo: " + repos.get(i));
                        return jar.get();
                    } else if (found && i == repos.size() - 1) {
                        throw new MissingArtifactException("Artifact couldn't be retrieved for repo: " + repos.get(i), null);
                    } else if (found) {
                        break;
                    }
                }
            }
            throw new MissingArtifactException(
                    mavenCoordinate.toURL(mavenCoordinate.getMavenRepos().size() > 0
                            ? mavenCoordinate.getMavenRepos().get(0)
                            : "no repos specified") + " | "
                            + mavenCoordinate.getPackaging(), null);
        }

        /**
         * Utility function that stores the contents of GET request to a temporary file.
         */
        private static Optional<File> httpGetFile(final String url) throws MissingArtifactException {
            Path tempFile = null;
            try {
                    final var packaging = url.substring(url.lastIndexOf("."));
                    tempFile = Files.createTempFile("fasten", packaging);

                    final InputStream in = new URL(url).openStream();
                    Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    in.close();

                    return Optional.of(new File(tempFile.toAbsolutePath().toString()));
            } catch (IOException e) {
                    if (tempFile != null) {tempFile.toFile().delete();}
                    throw new MissingArtifactException(e.getMessage(), e.getCause());
            }
        }

        private long computeDurationInMs(long startTime) {
            long endTime = System.nanoTime();
            return (endTime - startTime) / 1000000; // Compute duration in ms.
        }
    }
}
