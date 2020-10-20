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

package eu.fasten.core.maven.utils;

import eu.fasten.core.data.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * The helper utility class for working with maven repositories and pom files.
 */
public class MavenUtilities {

    private static final Logger logger = LoggerFactory.getLogger(MavenUtilities.class);

    /**
     * The default pom's repository url.
     */
    private static String DEFAULT_REPO = "https://repo.maven.apache.org/maven2/";


    /**
     * Download pom file of the given coordinate.
     *
     * @param groupId    groupId of the artifact to find its dependencies
     * @param artifactId artifactId of the artifact to find its dependencies
     * @param version    version of the artifact to find its dependencies
     * @return an optional pom file instance
     */
    public static Optional<File> downloadPom(String groupId, String artifactId, String version) {
        List<String> mavenRepos = MavenUtilities.getRepos();
        return MavenUtilities.downloadPom(groupId, artifactId, version, mavenRepos);
    }

    /**
     * Download pom file of the given coordinate with a given set of maven repositories.
     *
     * @param groupId    groupId of the artifact to find its dependencies
     * @param artifactId artifactId of the artifact to find its dependencies
     * @param version    version of the artifact to find its dependencies
     * @param mavenRepos the list of predefined maven repositories
     * @return an optional pom file instance
     */
    public static Optional<File> downloadPom(String groupId, String artifactId, String version, List<String> mavenRepos) {
        for (var repo : mavenRepos) {
            var pomUrl = MavenUtilities.getPomUrl(groupId, artifactId, version, repo);
            Optional<File> pom;
            try {
                pom = httpGetToFile(pomUrl);
            } catch (FileNotFoundException | UnknownHostException | MalformedURLException e) {
                continue;
            }
            if (pom.isPresent()) {
                return pom;
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieve the list of available repositories from the environmental variables.
     * If not given, use DEFAULT_REPO.
     *
     * @return list of urls of available maven repositories
     */
    public static List<String> getRepos() {
        return System.getenv(Constants.mvnRepoEnvVariable) != null
                ? Arrays.asList(System.getenv(Constants.mvnRepoEnvVariable).split(";"))
                : Collections.singletonList(DEFAULT_REPO);
    }

    /**
     * The utility function for obtaining the url of the pom file of the given coordinates.
     *
     * @param groupId    groupId of the artifact to find its dependencies
     * @param artifactId artifactId of the artifact to find its dependencies
     * @param version    version of the artifact to find its dependencies
     * @param repo       repository url of the artifact
     * @return a string full URL to the anticipated pom file
     */
    private static String getPomUrl(String groupId, String artifactId, String version, String repo) {
        return repo + groupId.replace('.', '/') + "/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".pom";
    }

    /**
     * Utility function that stores the contents of GET request to a temporary file.
     * Used for downloading pom file.
     *
     * @param url The url of the wanted file.
     * @return a temporarily saved file.
     */
    private static Optional<File> httpGetToFile(String url)
            throws FileNotFoundException, UnknownHostException, MalformedURLException {
        logger.debug("HTTP GET: " + url);
        try {
            final var tempFile = Files.createTempFile("fasten", ".pom");
            final InputStream in = new URL(url).openStream();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            in.close();
            return Optional.of(new File(tempFile.toAbsolutePath().toString()));
        } catch (FileNotFoundException | MalformedURLException | UnknownHostException e) {
            logger.error("Could not find URL: {}", e.getMessage(), e);
            throw e;
        } catch (IOException e) {
            logger.error("Error getting file from URL: " + url, e);
            return Optional.empty();
        }
    }

}
