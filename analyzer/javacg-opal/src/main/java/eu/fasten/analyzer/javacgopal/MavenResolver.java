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

import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import eu.fasten.core.data.RevisionCallGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A set of methods for downloading POM and JAR files given Maven coordinates.
 */
public class MavenResolver {
    private static Logger logger = LoggerFactory.getLogger(MavenResolver.class);

    /**
     * Returns information about the dependencies of the indicated artifact.
     *
     * @param mavenCoordinate Maven coordinate of an artifact.
     * @return A java List of a given artifact's dependencies in FastenJson Dependency format.
     */
    public static List<List<RevisionCallGraph.Dependency>> resolveDependencies(String mavenCoordinate) {

        var dependencies = new ArrayList<List<RevisionCallGraph.Dependency>>();

        try {
            var pom = new SAXReader().read(
                new ByteArrayInputStream(
                    downloadPom(mavenCoordinate).orElseThrow(RuntimeException::new).getBytes()
                )
            );

            for (var depNode : pom.selectNodes("//*[local-name() = 'dependency']")) {
                var groupId = depNode.selectSingleNode("./*[local-name() = 'groupId']").getStringValue();
                var artifactId = depNode.selectSingleNode("./*[local-name() = 'artifactId']").getStringValue();
                var versionSpec = depNode.selectSingleNode("./*[local-name() = 'version']");

                String version;
                if (versionSpec != null) {
                    version = versionSpec.getStringValue();
                } else {
                    version = "*";
                }

                RevisionCallGraph.Dependency dependency = new RevisionCallGraph.Dependency(
                    "mvn",
                    groupId + "." + artifactId,
                    Arrays.asList(new RevisionCallGraph.Constraint(version, version)));
                var depList = new ArrayList<RevisionCallGraph.Dependency>();
                depList.add(dependency);
                dependencies.add(depList);
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: " + mavenCoordinate, e);
        }
        return dependencies;
    }

    /**
     * Download a POM file indicated by the provided Maven coordinate
     * @param mavenCoordinate A Maven coordinate in the for "groupId:artifactId:version"
     * @return The contents of the downloaded POM file as a string
     */
    public static Optional<String> downloadPom(String mavenCoordinate) {
        return httpGetToFile(MavenCoordinate.fromString(mavenCoordinate).toPomUrl(),".pom").
            flatMap(f -> fileToString(f));
    }

    /**
     * Download a JAR file indicated by the provided Maven coordinate
     *
     * @param mavenCoordinate A Maven coordinate in the for "groupId:artifactId:version"
     * @return A temporary file on the filesystem
     */
    public static Optional<File> downloadJar(String mavenCoordinate) {
        logger.debug("Downloading JAR for " + mavenCoordinate);
        return httpGetToFile(MavenCoordinate.fromString(mavenCoordinate).toJarUrl(),".jar");
    }

    /**
     * Utility function that reads the contents of a file to a String
     */
    private static Optional<String> fileToString(File f) {
        logger.trace("Loading file as string: " + f.toString());
        try {
            var fr = new BufferedReader(new FileReader(f));
            StringBuilder result = new StringBuilder();
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
    private static Optional<File> httpGetToFile(String url, String suffix)  {
        logger.debug("HTTP GET: " + url);

        try {
            //TODO: Download artifacts in configurable shared location
            var tempFile = Files.createTempFile("fasten", suffix);

            InputStream in = new URL(url).openStream();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);

            return Optional.of(new File(tempFile.toAbsolutePath().toString()));

        } catch (Exception e){
            logger.error("Error retrieving URL: " + url, e);
            return Optional.empty();
        }
    }

    /**
     * A utility method to get a POM file and its timestamp from a URL
     * Please note that this might not be the most efficient way but it works and can be improved
     * later.
     * @param fileURL
     * @param dest
     * @throws IOException
     */
    public Date getFileAndTimeStamp(String fileURL, String dest) throws IOException {

        String fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1);
        StringJoiner pathJoin = new StringJoiner(File.separator);
        dest = pathJoin.add(dest).add(fileName).toString();

        logger.debug("Filename: " + fileName + " | " + "dest: " + dest);

        URL url = new URL(fileURL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        Date timestamp = new Date(con.getLastModified());

        if(con.getResponseCode() == HttpURLConnection.HTTP_OK){

            logger.debug("Okay status!");

            BufferedReader input = new BufferedReader(new InputStreamReader(con.getInputStream()), 8192);
            BufferedWriter output = new BufferedWriter(new FileWriter(new File(dest)));

            String line;
            while((line = input.readLine()) != null) {
                output.write(line);
                output.newLine();
            }

            output.close();
        }
        con.disconnect();

        return timestamp;
    }


}
