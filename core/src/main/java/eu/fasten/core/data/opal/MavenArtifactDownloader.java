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
package eu.fasten.core.data.opal;

import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.Optional;

/**
 * A set of methods for downloading POM and JAR files given Maven coordinates.
 */
public class MavenArtifactDownloader {
    private static final Logger logger = LoggerFactory.getLogger(MavenArtifactDownloader.class);
    private static final String[] packaging = {"jar", "war", "zip", "ear", "rar", "ejb", "par",
            "aar", "car", "nar", "kar"};
    private static final String[] defaultPackaging = {"zip", "aar", "tar.gz", "jar"};
    private boolean foundPackage = false;
    private Optional<File> artifactFile = Optional.empty();
    private final MavenCoordinate mavenCoordinate;
    private final LinkedList<String> mavenRepos;
    private long startTime;

    /**
     * This method downloads the Maven artifact to a temporary file.
     *
     * @param mavenCoordinate A Maven coordinate in the for "groupId:artifactId:version"
     */
    public MavenArtifactDownloader(final MavenCoordinate mavenCoordinate) {
        this.mavenCoordinate = mavenCoordinate;
        this.mavenRepos = mavenCoordinate.getMavenRepos();
    }

    /**
     * This method downloads the Maven artifact to a given file.
     *
     * @param mavenCoordinate  A Maven coordinate in the for "groupId:artifactId:version"
     * @param artifactFilePath A path to store the downloaded Maven artifact to.
     */
    public MavenArtifactDownloader(final MavenCoordinate mavenCoordinate, final File artifactFilePath) {
        this.mavenCoordinate = mavenCoordinate;
        this.mavenRepos = mavenCoordinate.getMavenRepos();
        this.artifactFile = Optional.of(artifactFilePath);
        this.artifactFile.ifPresent(file -> file.getParentFile().mkdirs());
    }

    /**
     * It tries to download the Maven artifact with the specified extension. E.g. jar
     */
    private File trySpecifiedPackaging(int repoNumber) throws MissingArtifactException {
        try {
            if (artifactFile.isEmpty()) {
                artifactFile = httpGetFile(mavenCoordinate.toProductUrl());
            } else {
                httpGetToFile(mavenCoordinate.toProductUrl(), artifactFile.get());
            }
        } catch (MissingArtifactException e) {
            foundPackage = false;

            long duration = computeDurationInMs(startTime);
            logger.warn("[ARTIFACT-DOWNLOAD] [UNPROCESSED] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [" + e.getClass().getSimpleName() + "] Artifact couldn't be retrieved for repo: " + mavenRepos.get(repoNumber), e);
        }

        if (artifactFile.isPresent() && artifactFile.get().exists()) {
            long duration = computeDurationInMs(startTime);
            logger.info("[ARTIFACT-DOWNLOAD] [SUCCESS] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [NONE] Artifact retrieved from repo: " + mavenRepos.get(repoNumber));
            return artifactFile.get();
        } else if (foundPackage && repoNumber == mavenRepos.size() - 1) {
            throw new MissingArtifactException("Artifact couldn't be retrieved for repo: " + mavenRepos.get(repoNumber), null);
        }
        return null;
    }

    /**
     * It tries to download the artifact with default extensions as defined in `defaultPackaging`.
     *
     * @param repoNumber
     * @throws MissingArtifactException
     */
    private File tryDefaultPackaging(int repoNumber) throws MissingArtifactException {
        for (var s : defaultPackaging) {
            startTime = System.nanoTime();
            try {
                foundPackage = true;
                if (artifactFile.isEmpty()) {
                    artifactFile = httpGetFile(mavenCoordinate.toProductUrl());
                } else {
                    httpGetToFile(mavenCoordinate.toProductUrl(), artifactFile.get());
                }
            } catch (MissingArtifactException e) {
                foundPackage = false;

                long duration = computeDurationInMs(startTime);
                logger.warn("[ARTIFACT-DOWNLOAD] [UNPROCESSED] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [" + e.getClass().getSimpleName() + "] Artifact couldn't be retrieved for repo: " + mavenRepos.get(repoNumber), e);
            }

            if (artifactFile.isPresent() && artifactFile.get().exists()) {
                long duration = computeDurationInMs(startTime);
                logger.info("[ARTIFACT-DOWNLOAD] [SUCCESS] [" + duration + "] [" + mavenCoordinate.getCoordinate() + "] [NONE] Artifact retrieved from repo: " + mavenRepos.get(repoNumber));
                return artifactFile.get();
            } else if (foundPackage && repoNumber == mavenRepos.size() - 1) {
                throw new MissingArtifactException("Artifact couldn't be retrieved for repo: " + mavenRepos.get(repoNumber), null);
            } else if (foundPackage) {
                break;
            }
        }
        return null;
    }


    /**
     * Download a JAR file indicated by the provided Maven coordinate.
     *
     * @return A temporary file on the filesystem
     */
    public File downloadArtifact(String artifactRepo)
            throws MissingArtifactException {
        if (artifactRepo != null && !artifactRepo.isEmpty() && !artifactRepo.equals(MavenUtilities.MAVEN_CENTRAL_REPO)) {
            mavenRepos.addFirst(artifactRepo);
        }
        for (int i = 0; i < mavenRepos.size(); i++) {

            startTime = System.nanoTime();
            var jarFile = trySpecifiedPackaging(i);
            if (jarFile != null) {
                return jarFile;
            } else if (foundPackage) {
                continue;
            }

            jarFile = tryDefaultPackaging(i);
            if (jarFile != null) return jarFile;
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
        logger.info("Downloading artifact from URL: {}", url);
        Path tempFile = null;
        try {
            final var packaging = url.substring(url.lastIndexOf("."));
            tempFile = Files.createTempFile("fasten", packaging);

            final InputStream in = new URL(url).openStream();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            in.close();

            return Optional.of(new File(tempFile.toAbsolutePath().toString()));
        } catch (IOException e) {
            if (tempFile != null) {
                tempFile.toFile().delete();
            }
            throw new MissingArtifactException(e.getMessage(), e.getCause());
        }
    }

    /**
     * Utility function that stores the contents of GET request to a given file
     */
    private static void httpGetToFile(final String url, final File file) throws MissingArtifactException {
        logger.info("Downloading artifact from URL: {} to File: {}", url, file.getAbsolutePath());
        try {
            final InputStream in = new URL(url).openStream();
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            in.close();
        } catch (IOException e) {
            throw new MissingArtifactException(e.getMessage(), e.getCause());
        }
    }

    private long computeDurationInMs(long startTime) {
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1000000; // Compute duration in ms.
    }
}