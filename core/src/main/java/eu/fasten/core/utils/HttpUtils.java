/*
 * Copyright 2022 Delft University of Technology
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
package eu.fasten.core.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.c0ps.maven.MavenUtilities;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    /**
     * Utility function that stores the contents of GET request to a temporary file. Used for downloading pom file.
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
        return downloadPomFile(url).flatMap(f -> {
            try {
                return Optional.of(FileUtils.readFileToString(f, StandardCharsets.UTF_8));
            } catch (IOException e) {
                return Optional.empty();
            }
        }).orElse(null);
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
     * Download pom file of the given coordinate.
     *
     * @param groupId    groupId of the artifact to find its dependencies
     * @param artifactId artifactId of the artifact to find its dependencies
     * @param version    version of the artifact to find its dependencies
     * @return an optional pom file instance
     */
    public static Optional<File> downloadPom(String groupId, String artifactId, String version) {
        return downloadPom(groupId, artifactId, version, List.of(MavenUtilities.MAVEN_CENTRAL_REPO));
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
            var pomUrl = getPomUrl(groupId.trim(), artifactId.trim(), version.trim(), repo);
            try {
                File pom = httpGetToFile(pomUrl);
                return Optional.of(pom);
            } catch (IOException e2) {
                continue;
            }
        }
        return Optional.empty();
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
        return repo + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".pom";
    }

}