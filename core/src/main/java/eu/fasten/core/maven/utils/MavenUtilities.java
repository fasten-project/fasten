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
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The helper utility class for working with maven repositories and pom files.
 */
public class MavenUtilities {

    private static final Logger logger = LoggerFactory.getLogger(MavenUtilities.class);

    /**
     * The default pom's repository url.
     */
    public static String MAVEN_CENTRAL_REPO = "https://repo.maven.apache.org/maven2/";

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
            var pomUrl = MavenUtilities.getPomUrl(groupId.trim(), artifactId.trim(), version.trim(), repo);
            try {
                File pom = httpGetToFile(pomUrl);
                return Optional.of(pom);
            } catch (IOException e2) {
                continue;
            }
        }
        return Optional.empty();
    }

    public static Optional<File> downloadPomFile(String pomUrl) {
        try {
            File pom = httpGetToFile(pomUrl);
            return Optional.of(pom);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Retrieve the list of available repositories from the environmental variables.
     * If not given, use DEFAULT_REPO.
     *
     * @return list of urls of available maven repositories
     */
    public static LinkedList<String> getRepos() {
        return System.getenv(Constants.mvnRepoEnvVariable) != null
                ? new LinkedList<>(Arrays.asList(System.getenv(Constants.mvnRepoEnvVariable).split(";")))
                : new LinkedList<>(Collections.singleton(MAVEN_CENTRAL_REPO));
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
    public static String getPomUrl(String groupId, String artifactId, String version, String repo) {
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
    private static File httpGetToFile(String url) throws IOException {
        logger.debug("HTTP GET: " + url);
        try {
            final var tempFile = Files.createTempFile("fasten", ".pom");

            try (ResponseBody response = getHttpResponse(url); InputStream in = response.byteStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            // TODO why this complicated construct and not just return tempFile?
            return tempFile.toAbsolutePath().toFile();
        } catch (ConnectException e) {
            // After downloading ~50-60K POMs, there will be a lot of CLOSE_WAIT connections,
            // at some point the plug-in runs out of source ports to use. Therefore, we need to crash so that
            // Kubernetes will restart the plug-in to kill CLOSE_WAIT connections.
            throw new Error("Failing execution, typically due to many CLOSE_WAIT connections", e);
        } catch (IOException e) {
            logger.error("Error getting file from URL: " + url, e);
            throw e;
        }
    }

    private static ResponseBody getHttpResponse(String url) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).addHeader("Connection", "close").build();
        Call call = client.newCall(request);

        Response response = null;
        try {
            response = call.execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ResponseBody body = Objects.requireNonNull(response.body());
        if (response.code() == 200) {
            return response.body();
        } else {
            body.close();
            throw new IllegalStateException("unexpected query result");
        }
    }

    public static String sendGetRequest(String url) {
        return MavenUtilities.downloadPomFile(url).flatMap(MavenUtilities::fileToString).orElse(null);
    }

    /**
     * Utility function that reads the contents of a file to a String.
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
     * Force-deletes the file or directory.
     *
     * @param file File to be deleted
     */
    public static void forceDeleteFile(File file) {
        if (file == null) {
            return;
        }
        try {
            FileUtils.forceDelete(file);
        } catch (IOException ignored) {
        } finally {
            if (file.exists()) {
                file.delete();
            }
        }
    }

    public static boolean mavenArtifactExists(String groupId, String artifactId, String version, String artifactRepo) {
        if (artifactRepo == null || artifactRepo.isEmpty()) {
            artifactRepo = MAVEN_CENTRAL_REPO;
        }
        var url = getPomUrl(groupId, artifactId, version, artifactRepo);
        try {
            httpGetToFile(url);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

}
