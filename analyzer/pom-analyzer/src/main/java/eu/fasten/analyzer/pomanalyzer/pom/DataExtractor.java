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

import eu.fasten.analyzer.pomanalyzer.pom.data.Dependency;
import eu.fasten.analyzer.pomanalyzer.pom.data.DependencyData;
import eu.fasten.analyzer.pomanalyzer.pom.data.DependencyManagement;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataExtractor {

    private final List<String> mavenRepos;
    private static final Logger logger = LoggerFactory.getLogger(DataExtractor.class);

    private String mavenCoordinate = null;
    private String pomContents = null;
    private Pair<String, Pair<Map<String, String>, List<DependencyManagement>>> resolutionMetadata = null;

    public DataExtractor() {
        this.mavenRepos = System.getenv("MVN_REPO") != null
                ? Arrays.asList(System.getenv("MVN_REPO").split(";"))
                : Collections.singletonList("https://repo.maven.apache.org/maven2/");
    }

    public DataExtractor(List<String> mavenRepos) {
        this.mavenRepos = mavenRepos;
    }

    /**
     * Generates link to Maven sources jar file for certain Maven coordinate.
     *
     * @param groupId    groupId of the coordinate
     * @param artifactId artifactId of the coordinate
     * @param version    version of the coordinate
     * @return Link to Maven sources jar file
     */
    public String generateMavenSourcesLink(String groupId, String artifactId, String version) {
        for (var repo : this.mavenRepos) {
            var url = repo + groupId.replace('.', '/') + "/" + artifactId + "/"
                    + version + "/" + artifactId + "-" + version + "-sources.jar";
            try {
                int status = sendGetRequest(url);
                if (status == 200) {
                    return url;
                } else if (status != 404) {
                    break;
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Error sending GET request to " + url, e);
                break;
            }
        }
        return "";
    }

    private int sendGetRequest(String url) throws IOException, InterruptedException {
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
        var request = HttpRequest.newBuilder().GET().uri(URI.create(url)).build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    /**
     * Extracts packaging type from POM of certain Maven coordinate.
     *
     * @param groupId    groupId of the coordinate
     * @param artifactId artifactId of the coordinate
     * @param version    version of the coordinate
     * @return Extracted packaging as String (default is "jar")
     */
    public String extractPackagingType(String groupId, String artifactId, String version) {
        String packaging = "jar";
        try {
            var pom = getPomRootElement(groupId, artifactId, version);
            updateResolutionMetadata(groupId, artifactId, version, pom);
            var properties = this.resolutionMetadata.getRight().getLeft();
            var packagingNode = pom.selectSingleNode("./*[local-name()='packaging']");
            if (packagingNode != null) {
                packaging = replacePropertyReferences(packagingNode.getText(), properties, pom);
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        }
        return packaging;
    }

    /**
     * Extracts project name from POM of certain Maven coordinate.
     *
     * @param groupId    groupId of the coordinate
     * @param artifactId artifactId of the coordinate
     * @param version    version of the coordinate
     * @return Extracted project name as String
     */
    public String extractProjectName(String groupId, String artifactId, String version) {
        String name = null;
        try {
            var pom = getPomRootElement(groupId, artifactId, version);
            updateResolutionMetadata(groupId, artifactId, version, pom);
            var properties = this.resolutionMetadata.getRight().getLeft();
            var nameNode = pom.selectSingleNode("./*[local-name()='name']");
            if (nameNode != null) {
                name = replacePropertyReferences(nameNode.getText(), properties, pom);
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        }
        return name;
    }

    /**
     * Replaces all property references with their actual values.
     *
     * @param ref        String that can contain property references
     * @param properties Properties extracted from the artifact
     * @param pom        POM root element for accessing DOM tree
     * @return String that has all property references substituted with their values
     */
    public String replacePropertyReferences(String ref, Map<String, String> properties, Element pom) {
        var propertyIndexes = getPropertyReferencesIndexes(ref);
        if (propertyIndexes == null) {
            return ref;
        }
        var refBuilder = new StringBuilder();
        var i = 0;
        for (var property : propertyIndexes) {
            var found = false;
            refBuilder.append(ref, i, property[0]);
            var refValue = ref.substring(property[0] + 2, property[1]);
            if (properties.containsKey(refValue)) {
                refValue = properties.get(refValue);
                found = true;
            } else {
                var path = refValue.replaceFirst("project\\.", "");
                var pathParts = path.split("\\.");
                Node node = pom;
                for (var nodeName : pathParts) {
                    if (node == null) {
                        break;
                    }
                    node = node.selectSingleNode("./*[local-name()='" + nodeName + "']");
                }
                if (node != null) {
                    refValue = node.getText();
                    found = true;
                } else if (path.equals("groupId") || path.equals("version")) {
                    path = "parent." + path;
                    pathParts = path.split("\\.");
                    node = pom;
                    for (var nodeName : pathParts) {
                        if (node == null) {
                            break;
                        }
                        node = node.selectSingleNode("./*[local-name()='" + nodeName + "']");
                    }
                    if (node != null) {
                        refValue = node.getText();
                        found = true;
                    }
                }
            }
            if (!found) {
                refValue = "${" + refValue + "}";
            }
            refBuilder.append(refValue);
            i = property[1] + 1;
        }
        if (i < ref.length()) {
            refBuilder.append(ref, i, ref.length());
        }
        return refBuilder.toString();
    }

    private int[][] getPropertyReferencesIndexes(String ref) {
        if (!ref.contains("${")) {
            return null;
        }
        var numProperties = StringUtils.countMatches(ref, "${");
        var indexes = new int[numProperties][2];
        int count = 0;
        int i = 0;
        while (count < numProperties && i < ref.length()) {
            while (!ref.startsWith("${", i) && i < ref.length()) {
                i++;
            }
            indexes[count][0] = i;
            while (!ref.startsWith("}", i) && i < ref.length()) {
                i++;
            }
            indexes[count][1] = i;
            count++;
        }
        return indexes;
    }

    /**
     * Extracts repository URL from POM of certain Maven coordinate.
     *
     * @param groupId    groupId of the coordinate
     * @param artifactId artifactId of the coordinate
     * @param version    version of the coordinate
     * @return Extracted repository URL as String
     */
    public String extractRepoUrl(String groupId, String artifactId, String version) {
        String repoUrl = null;
        try {
            var pom = getPomRootElement(groupId, artifactId, version);
            var scm = pom.selectSingleNode("./*[local-name()='scm']");
            if (scm != null) {
                var url = scm.selectSingleNode("./*[local-name()='url']");
                if (url != null) {
                    repoUrl = url.getText();
                }
            }
            if (repoUrl != null) {
                updateResolutionMetadata(groupId, artifactId, version, pom);
                var properties = this.resolutionMetadata.getRight().getLeft();
                repoUrl = replacePropertyReferences(repoUrl, properties, pom);
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        }
        return repoUrl;
    }

    private Element getPomRootElement(String groupId, String artifactId, String version)
            throws FileNotFoundException, DocumentException {
        var pomByteStream =
                (groupId + ":" + artifactId + ":" + version).equals(this.mavenCoordinate)
                        ? new ByteArrayInputStream(this.pomContents.getBytes())
                        : new ByteArrayInputStream(this.downloadPom(artifactId, groupId, version)
                        .orElseThrow(FileNotFoundException::new).getBytes());
        return new SAXReader().read(pomByteStream).getRootElement();
    }

    private void updateResolutionMetadata(String groupId, String artifactId, String version, Element pom) {
        if (this.resolutionMetadata == null || !this.resolutionMetadata.getLeft().equals(groupId + ":" + artifactId + ":" + version)) {
            var metadata = this.extractDependencyResolutionMetadata(pom);
            this.resolutionMetadata = new ImmutablePair<>(groupId + ":" + artifactId + ":" + version, metadata);
        }
    }

    /**
     * Extracts commit tag from POM of certain Maven coordinate.
     *
     * @param groupId    groupId of the coordinate
     * @param artifactId artifactId of the coordinate
     * @param version    version of the coordinate
     * @return Extracted commit tag representing certain version in repository
     */
    public String extractCommitTag(String groupId, String artifactId, String version) {
        String commitTag = null;
        try {
            var pom = getPomRootElement(groupId, artifactId, version);
            var scm = pom.selectSingleNode("./*[local-name()='scm']");
            if (scm != null) {
                var tag = scm.selectSingleNode("./*[local-name()='tag']");
                commitTag = (tag != null) ? tag.getText() : null;
            }
            if (commitTag != null) {
                updateResolutionMetadata(groupId, artifactId, version, pom);
                var properties = this.resolutionMetadata.getRight().getLeft();
                commitTag = replacePropertyReferences(commitTag, properties, pom);
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        }
        return commitTag;
    }

    /**
     * Extracts dependency information (dependencyManagement and list of dependencies)
     * from certain Maven coordinate.
     *
     * @param groupId    groupId of the coordinate
     * @param artifactId artifactId of the coordinate
     * @param version    version of the coordinate
     * @return Extracted dependency information as DependencyData
     */
    public DependencyData extractDependencyData(String groupId, String artifactId, String version) {
        DependencyData dependencyData = new DependencyData(
                new DependencyManagement(new ArrayList<>()), new ArrayList<>());
        try {
            var pom = getPomRootElement(groupId, artifactId, version);
            updateResolutionMetadata(groupId, artifactId, version, pom);
            var versionResolutionData = this.resolutionMetadata.getRight();
            var properties = versionResolutionData.getLeft();
            var parentDependencyManagements = versionResolutionData.getRight();
            for (int i = 0; i < parentDependencyManagements.size(); i++) {
                var depManagement = parentDependencyManagements.get(i);
                var resolvedDependencies = resolveDependencies(depManagement.dependencies,
                        properties, new ArrayList<>(), pom);
                parentDependencyManagements.set(i, new DependencyManagement(resolvedDependencies));
            }
            var dependencyManagementNode = pom.selectSingleNode("./*[local-name()='dependencyManagement']");
            DependencyManagement dependencyManagement;
            if (dependencyManagementNode != null) {
                var dependenciesNode = dependencyManagementNode
                        .selectSingleNode("./*[local-name()='dependencies']");
                var dependencies = extractDependencies(dependenciesNode);
                dependencies = this.resolveDependencies(dependencies, properties,
                        parentDependencyManagements, pom);
                dependencyManagement = new DependencyManagement(dependencies);
            } else {
                dependencyManagement = new DependencyManagement(new ArrayList<>());
            }
            var dependenciesNode = pom.selectSingleNode("./*[local-name()='dependencies']");
            var dependencies = extractDependencies(dependenciesNode);
            dependencies = this.resolveDependencies(dependencies, properties,
                    parentDependencyManagements, pom);
            dependencyData = new DependencyData(dependencyManagement, dependencies);
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + ":" + artifactId + ":" + version);
        }
        return dependencyData;
    }

    private List<Dependency> resolveDependencies(List<Dependency> dependencies,
                                                 Map<String, String> properties,
                                                 List<DependencyManagement> depManagements,
                                                 Element pom) {
        var resolvedDependencies = new ArrayList<Dependency>();
        for (var dependency : dependencies) {
            if (dependency.versionConstraints.get(0).lowerBound.equals("*")) {
                for (var depManagement : depManagements) {
                    for (var parentDep : depManagement.dependencies) {
                        if (parentDep.artifactId.equals(dependency.artifactId)
                                && parentDep.groupId.equals(dependency.groupId)) {
                            resolvedDependencies.add(new Dependency(
                                    dependency.artifactId,
                                    dependency.groupId,
                                    parentDep.versionConstraints,
                                    dependency.exclusions,
                                    dependency.scope,
                                    dependency.optional,
                                    dependency.type,
                                    dependency.classifier
                            ));
                        }
                    }
                }
                if (dependency.versionConstraints.get(0).lowerBound.equals("*")) {
                    resolvedDependencies.add(new Dependency(
                            dependency.artifactId,
                            dependency.groupId,
                            replacePropertyReferences("${project.version}", properties, pom),
                            dependency.exclusions,
                            dependency.scope,
                            dependency.optional,
                            dependency.type,
                            dependency.classifier
                    ));
                }
            } else if (dependency.versionConstraints.get(0).lowerBound.startsWith("$")) {
                var property = dependency.versionConstraints.get(0).lowerBound;
                var version = "";
                var value = replacePropertyReferences(property, properties, pom);
                if (!value.equals(property)) {
                    version = value;
                }
                resolvedDependencies.add(new Dependency(
                        dependency.artifactId,
                        dependency.groupId,
                        version,
                        dependency.exclusions,
                        dependency.scope,
                        dependency.optional,
                        dependency.type,
                        dependency.classifier
                ));
            } else {
                resolvedDependencies.add(dependency);
            }
        }
        for (int i = 0; i < resolvedDependencies.size(); i++) {
            var dep = resolvedDependencies.get(i);
            var resolvedArtifact = dep.artifactId;
            if (dep.artifactId.contains("$")) {
                resolvedArtifact = replacePropertyReferences(dep.artifactId, properties, pom);
            }
            var resolvedGroup = dep.groupId;
            if (dep.groupId.contains("$")) {
                resolvedGroup = replacePropertyReferences(dep.groupId, properties, pom);
            }
            var resolvedExclusions = dep.exclusions;
            for (int j = 0; j < resolvedExclusions.size(); j++) {
                var exclusion = dep.exclusions.get(j);
                var resolvedExclusionGroup = exclusion.groupId;
                if (exclusion.groupId.contains("$")) {
                    resolvedExclusionGroup = replacePropertyReferences(
                            exclusion.groupId, properties, pom);
                }
                var resolvedExclusionArtifact = exclusion.artifactId;
                if (exclusion.artifactId.contains("$")) {
                    resolvedExclusionArtifact = replacePropertyReferences(
                            exclusion.artifactId, properties, pom);
                }
                resolvedExclusions.set(j, new Dependency.Exclusion(
                        resolvedExclusionArtifact, resolvedExclusionGroup
                ));
            }
            var resolvedScope = dep.scope;
            if (dep.scope.contains("$")) {
                resolvedScope = replacePropertyReferences(dep.scope, properties, pom);
            }
            var resolvedType = dep.type;
            if (dep.type.contains("$")) {
                resolvedType = replacePropertyReferences(dep.type, properties, pom);
            }
            var resolvedClassifier = dep.classifier;
            if (dep.classifier.contains("$")) {
                resolvedClassifier = replacePropertyReferences(dep.classifier, properties, pom);
            }
            resolvedDependencies.set(i, new Dependency(
                    resolvedArtifact,
                    resolvedGroup,
                    dep.versionConstraints,
                    resolvedExclusions,
                    resolvedScope,
                    dep.optional,
                    resolvedType,
                    resolvedClassifier
            ));
        }
        return resolvedDependencies;
    }

    private Pair<Map<String, String>, List<DependencyManagement>> extractDependencyResolutionMetadata(Node pomRoot) {
        Map<String, String> properties = new HashMap<>();
        var dependencyManagements = new ArrayList<DependencyManagement>();
        var profilesRoot = pomRoot.selectSingleNode("./*[local-name() ='profiles']");
        if (profilesRoot != null) {
            for (final var profile : profilesRoot.selectNodes("*")) {
                var activationNode = profile.selectSingleNode("./*[local-name() ='activation']");
                if (activationNode != null) {
                    var activeByDefault = activationNode
                            .selectSingleNode("./*[local-name() ='activeByDefault']");
                    if (activeByDefault != null && activeByDefault.getText().equals("true")) {
                        var propertiesRoot = profile
                                .selectSingleNode("./*[local-name() ='properties']");
                        if (propertiesRoot != null) {
                            for (final var property : propertiesRoot.selectNodes("*")) {
                                properties.put(property.getName(), property.getStringValue());
                            }
                        }
                    }
                }
            }
        }
        var propertiesRoot = pomRoot.selectSingleNode("./*[local-name() ='properties']");
        if (propertiesRoot != null) {
            for (final var property : propertiesRoot.selectNodes("*")) {
                properties.put(property.getName(), property.getStringValue());
            }
        }
        var parentNode = pomRoot.selectSingleNode("./*[local-name() ='parent']");
        if (parentNode != null) {
            var parentGroup = parentNode
                    .selectSingleNode("./*[local-name() ='groupId']").getText();
            var parentArtifact = parentNode
                    .selectSingleNode("./*[local-name() ='artifactId']").getText();
            var parentVersion = parentNode
                    .selectSingleNode("./*[local-name() ='version']").getText();
            try {
                var parentPom = new SAXReader().read(new ByteArrayInputStream(
                        this.downloadPom(parentArtifact, parentGroup, parentVersion)
                                .orElseThrow(FileNotFoundException::new).getBytes())).getRootElement();
                var parentMetadata = this.extractDependencyResolutionMetadata(parentPom);
                var parentProperties = parentMetadata.getLeft();
                for (var entry : parentProperties.entrySet()) {
                    properties.put(entry.getKey(), entry.getValue());
                }
                var dependencyManagementNode = parentPom
                        .selectSingleNode("./*[local-name()='dependencyManagement']");
                DependencyManagement dependencyManagement;
                if (dependencyManagementNode != null) {
                    var dependenciesNode = dependencyManagementNode
                            .selectSingleNode("./*[local-name()='dependencies']");
                    var dependencies = extractDependencies(dependenciesNode);
                    dependencyManagement = new DependencyManagement(dependencies);
                } else {
                    dependencyManagement = new DependencyManagement(new ArrayList<>());
                }
                dependencyManagements.add(dependencyManagement);
                dependencyManagements.addAll(parentMetadata.getRight());
            } catch (DocumentException e) {
                logger.error("Error parsing POM file for: "
                        + parentGroup + ":" + parentArtifact + ":" + parentVersion);
            } catch (FileNotFoundException e) {
                logger.error("Error downloading POM file for: "
                        + parentGroup + ":" + parentArtifact + ":" + parentVersion);
            }
        }
        return new ImmutablePair<>(properties, dependencyManagements);
    }

    private List<Dependency> extractDependencies(Node dependenciesNode) {
        ArrayList<Dependency> dependencies = new ArrayList<>();
        if (dependenciesNode != null) {
            for (var dependencyNode : dependenciesNode
                    .selectNodes("./*[local-name()='dependency']")) {
                var artifactNode = dependencyNode
                        .selectSingleNode("./*[local-name()='artifactId']");
                var groupNode = dependencyNode
                        .selectSingleNode("./*[local-name()='groupId']");
                var versionNode = dependencyNode
                        .selectSingleNode("./*[local-name()='version']");
                var exclusionsNode = dependencyNode
                        .selectSingleNode("./*[local-name()='exclusions']");
                var exclusions = new ArrayList<Dependency.Exclusion>();
                if (exclusionsNode != null) {
                    for (var exclusionNode : exclusionsNode
                            .selectNodes("./*[local-name()='exclusion']")) {
                        var exclusionArtifactNode = exclusionNode
                                .selectSingleNode("./*[local-name()='artifactId']");
                        var exclusionGroupNode = exclusionNode
                                .selectSingleNode("./*[local-name()='groupId']");
                        exclusions.add(new Dependency.Exclusion(
                                exclusionArtifactNode.getText(),
                                exclusionGroupNode.getText()
                        ));
                    }
                }
                var scopeNode = dependencyNode
                        .selectSingleNode("./*[local-name()='scope']");
                var optionalNode = dependencyNode
                        .selectSingleNode("./*[local-name()='optional']");
                var typeNode = dependencyNode
                        .selectSingleNode("./*[local-name()='type']");
                var classifierNode = dependencyNode
                        .selectSingleNode("./*[local-name()='classifier']");
                String version;
                if (versionNode != null) {
                    version = versionNode.getText();
                } else {
                    version = null;
                }
                dependencies.add(new Dependency(
                        artifactNode.getText(),
                        groupNode.getText(),
                        version,
                        exclusions,
                        (scopeNode != null) ? scopeNode.getText() : "",
                        (optionalNode != null) && Boolean.parseBoolean(
                                optionalNode.getText()
                        ),
                        (typeNode != null) ? typeNode.getText() : "",
                        (classifierNode != null) ? classifierNode.getText() : ""
                ));
            }
        }
        return dependencies;
    }

    private Optional<String> downloadPom(String artifactId, String groupId, String version) {
        for (var repo : this.mavenRepos) {
            var pomUrl = this.getPomUrl(artifactId, groupId, version, repo);
            Optional<String> pom;
            try {
                pom = httpGetToFile(pomUrl).flatMap(DataExtractor::fileToString);
            } catch (FileNotFoundException | UnknownHostException | MalformedURLException e) {
                continue;
            }
            if (pom.isPresent()) {
                this.mavenCoordinate = groupId + ":" + artifactId + ":" + version;
                this.pomContents = pom.get();
                return pom;
            }
        }
        return Optional.empty();
    }

    private String getPomUrl(String artifactId, String groupId, String version, String repo) {
        return repo + groupId.replace('.', '/') + "/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".pom";
    }

    /**
     * Utility function that stores the contents of GET request to a temporary file.
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
