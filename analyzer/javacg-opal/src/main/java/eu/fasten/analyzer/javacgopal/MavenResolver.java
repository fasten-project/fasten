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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import eu.fasten.core.data.RevisionCallGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For downloading, resolving and all operations related to maven artifacts.
 */
public class MavenResolver {
    private static Logger logger = LoggerFactory.getLogger(OPALMethodAnalyzer.class);

    /**
     * Resolves the dependency tree of a given artifact.
     * @param mavenCoordinate Maven coordinate of an artifact.
     * @return A java List of a given artifact's dependencies in FastenJson Dependency format.
     */

    public static List<List<RevisionCallGraph.Dependency>> resolveDependencies(String mavenCoordinate) {

        var dependencies = new ArrayList<List<RevisionCallGraph.Dependency>>();

        try {
            var pom = new SAXReader().read(downloadPom(mavenCoordinate).orElseThrow(RuntimeException::new));

            for (var dep : pom.selectNodes("//dependency")) {
                System.out.println(dep.asXML());
            }
            var depNodes = pom.selectNodes("//dependency");

//            for (Node dependencyNode : depNodes) {
//                RevisionCallGraph.Dependency dependency = new RevisionCallGraph.Dependency(
//                    "mvn",
//                    i.getCoordinate().getGroupId() + "." + i.getCoordinate().getArtifactId(),
//                    Arrays.asList(new RevisionCallGraph.Constraint("[" + i.getCoordinate().getVersion() + "]")));
//                dependencies.add((List<RevisionCallGraph.Dependency>) dependency);
//            }

        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return dependencies;
    }

    public static Optional<String> downloadPom(String mavenCoordinate) {
        return httpGetToFile(MavenCoordinate.fromString(mavenCoordinate).toPomUrl()).
            flatMap(f -> fileToString(f));
    }

    public static Optional<File> downloadJar(String mavenCoordinate) {
        logger.debug("Downloading JAR for " + mavenCoordinate);
        return httpGetToFile(MavenCoordinate.fromString(mavenCoordinate).toJarUrl());
    }

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
            logger.error("Cannot read from file: " + f.toString() + ". Error: " + e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<File> httpGetToFile(String url)  {
        logger.debug("HTTP GET: " + url);

        try {
            //TODO: Download artifacts in configurable shared location
            var tempFile = Files.createTempFile("fasten", ".tmp");

            InputStream in = new URL(url).openStream();
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);

            return Optional.of(new File(tempFile.toAbsolutePath().toString()));

        } catch (Exception e){
            logger.error("Error retrieving URL: " + url + ". Error: " + e.getMessage());
            return Optional.empty();
        }
    }
}
