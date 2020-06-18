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

package eu.fasten.analyzer.pomanalyzer.pom;

import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DataExtractor {

    private List<String> mavenRepos;
    private static final Logger logger = LoggerFactory.getLogger(DataExtractor.class);

    public DataExtractor() {
        this.mavenRepos = Collections.singletonList("https://repo.maven.apache.org/maven2/");
    }

    public String extractRepoUrl(String artifactId, String groupId, String version) {
        String repoUrl = null;
        try {
            var pom = new SAXReader().read(new ByteArrayInputStream(
                    this.downloadPom(artifactId, groupId, version)
                            .orElseThrow(RuntimeException::new).getBytes()));
            var scm = pom.getRootElement().selectSingleNode("./*[local-name()='scm']");
            if (scm != null) {
                var url = scm.selectSingleNode("./*[local-name()='url']");
                repoUrl = url.getText();
            }
        } catch (FileNotFoundException | DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        }
        return repoUrl;
    }

    private Optional<String> downloadPom(String artifactId, String groupId, String version)
            throws FileNotFoundException {
        for (var repo : this.getMavenRepos()) {
            var pomUrl = this.getPomUrl(artifactId, groupId, version, repo);
            var pom = httpGetToFile(pomUrl).flatMap(DataExtractor::fileToString);
            if (pom.isPresent()) {
                return pom;
            }
        }
        return Optional.empty();
    }

    public List<String> getMavenRepos() {
        return mavenRepos;
    }

    public void setMavenRepos(List<String> mavenRepos) {
        this.mavenRepos = mavenRepos;
    }

    private String getPomUrl(String artifactId, String groupId, String version, String repo) {
        return repo + groupId.replace('.', '/') + "/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".pom";
    }

    /**
     * Utility function that stores the contents of GET request to a temporary file.
     */
    private static Optional<File> httpGetToFile(String url) throws FileNotFoundException {
        logger.debug("HTTP GET: " + url);
        try {
            final var tempFile = Files.createTempFile("fasten", ".pom");
            final InputStream in = new URL(url).openStream();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            in.close();
            return Optional.of(new File(tempFile.toAbsolutePath().toString()));
        } catch (FileNotFoundException e) {
            logger.error("Could not find URL: " + url);
            throw e;
        } catch (Exception e) {
            logger.error("Error retrieving URL: " + url);
            return Optional.empty();
        }
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
}
