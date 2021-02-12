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

import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.DependencyData;
import eu.fasten.core.maven.data.DependencyManagement;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import eu.fasten.core.data.Constants;
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

    public DataExtractor(List<String> mavenRepos) {
        this.mavenRepos = mavenRepos;
    }

    /**
     * Extracts Maven coordinate from the POM file.
     *
     * @param pomUrl URL to download the POM file
     * @return Maven coordinate in the form of 'groupId:artifactId:version'
     */
    public String getMavenCoordinate(String pomUrl) {
        StringBuilder coordinate = new StringBuilder();
        try {
            var pom = getPomRootElement(pomUrl);
            var properties = extractDependencyResolutionMetadata(pom).getLeft();
            var groupNode = pom.selectSingleNode("./*[local-name()='groupId']");
            var artifactNode = pom.selectSingleNode("./*[local-name()='artifactId']");
            var versionNode = pom.selectSingleNode("./*[local-name()='version']");
            var parent = pom.selectSingleNode("./*[local-name()='parent']");
            var parentGroup = parent == null ? null : parent.selectSingleNode("./*[local-name()='groupId']");
            var parentVersion = parent == null ? null : parent.selectSingleNode("./*[local-name()='groupId']");
            if (artifactNode != null) {
                if (groupNode == null && parentGroup != null) {
                    groupNode = parentGroup;
                }
                if (versionNode == null && parentVersion != null) {
                    versionNode = parentVersion;
                }
                if (groupNode != null && versionNode != null) {
                    coordinate.append(replacePropertyReferences(groupNode.getText(), properties, pom));
                    coordinate.append(Constants.mvnCoordinateSeparator);
                    coordinate.append(replacePropertyReferences(artifactNode.getText(), properties, pom));
                    coordinate.append(Constants.mvnCoordinateSeparator);
                    coordinate.append(replacePropertyReferences(versionNode.getText(), properties, pom));
                    return coordinate.toString();
                }
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file from: " + pomUrl);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file from: " + pomUrl);
        }
        return null;
    }

    /**
     * Extracts the artifact's release date from the given artifact repository.
     *
     * @param groupId      groupId of the artifact
     * @param artifactId   artifactId of the artifact
     * @param version      version of the artifact
     * @param artifactRepo Artifact repository (like Maven Central)
     * @return artifact's release date as Long (in ms since 01.01.1970) or null if could not extract
     */
    public Long extractReleaseDate(String groupId, String artifactId, String version, String artifactRepo) {
        URLConnection connection;
        try {
            connection = new URL(MavenUtilities.getPomUrl(groupId, artifactId, version, artifactRepo)).openConnection();
        } catch (IOException e) {
            logger.error("Could not extract release date", e);
            return null;
        }
        var lastModified = connection.getHeaderField("Last-Modified");
        if (lastModified == null) {
            return null;
        }
        Date releaseDate;
        try {
            releaseDate = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH).parse(lastModified);
        } catch (ParseException e) {
            logger.error("Could not parse extracted release date", e);
            return null;
        }
        return releaseDate.getTime();
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
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
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
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        }
        return name;
    }

    /**
     * Extracts Maven coordinate of the parent from POM of certain Maven coordinate.
     *
     * @param groupId    groupId of the coordinate
     * @param artifactId artifactId of the coordinate
     * @param version    version of the coordinate
     * @return Parent coordinate as String in the form of "groupId:artifactId:version"
     */
    public String extractParentCoordinate(String groupId, String artifactId, String version) {
        String parent = null;
        try {
            var pom = getPomRootElement(groupId, artifactId, version);
            updateResolutionMetadata(groupId, artifactId, version, pom);
            var properties = this.resolutionMetadata.getRight().getLeft();
            var parentNode = pom.selectSingleNode("./*[local-name()='parent']");
            if (parentNode != null) {
                var parentGroupNode = parentNode
                        .selectSingleNode("./*[local-name()='groupId']");
                var parentArtifactNode = parentNode
                        .selectSingleNode("./*[local-name()='artifactId']");
                var parentVersionNode = parentNode
                        .selectSingleNode("./*[local-name()='version']");
                if (parentGroupNode != null && parentArtifactNode != null
                        && parentVersionNode != null) {
                    var parentGroup = replacePropertyReferences(parentGroupNode.getText(),
                            properties, pom);
                    var parentArtifact = replacePropertyReferences(parentArtifactNode.getText(),
                            properties, pom);
                    var parentVersion = replacePropertyReferences(parentVersionNode.getText(),
                            properties, pom);
                    parent = parentGroup + Constants.mvnCoordinateSeparator + parentArtifact
                            + Constants.mvnCoordinateSeparator + parentVersion;
                }
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        }
        return parent;
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
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        }
        return repoUrl;
    }

    private Element getPomRootElement(String groupId, String artifactId, String version)
            throws FileNotFoundException, DocumentException {
        var pomByteStream =
                (groupId + Constants.mvnCoordinateSeparator + artifactId
                        + Constants.mvnCoordinateSeparator + version).equals(this.mavenCoordinate)
                        ? new ByteArrayInputStream(this.pomContents.getBytes())
                        : new ByteArrayInputStream(this.downloadPom(groupId, artifactId, version)
                        .orElseThrow(FileNotFoundException::new).getBytes());
        return new SAXReader().read(pomByteStream).getRootElement();
    }

    private Element getPomRootElement(String pomUrl) throws FileNotFoundException, DocumentException {
        var pomByteStream = new ByteArrayInputStream(this.downloadPom(pomUrl).orElseThrow(FileNotFoundException::new).getBytes());
        return new SAXReader().read(pomByteStream).getRootElement();
    }

    private void updateResolutionMetadata(String groupId, String artifactId, String version, Element pom) {
        if (this.resolutionMetadata == null
                || !this.resolutionMetadata.getLeft()
                .equals(groupId + Constants.mvnCoordinateSeparator + artifactId
                        + Constants.mvnCoordinateSeparator + version)) {
            var metadata = this.extractDependencyResolutionMetadata(pom);
            this.resolutionMetadata = new ImmutablePair<>(groupId + Constants.mvnCoordinateSeparator
                    + artifactId + Constants.mvnCoordinateSeparator + version, metadata);
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
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
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
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        } catch (FileNotFoundException e) {
            logger.error("Error downloading POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
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
                var resolved = false;
                for (var depManagement : depManagements) {
                    for (var parentDep : depManagement.dependencies) {
                        if (parentDep.artifactId.equals(dependency.artifactId)
                                && parentDep.groupId.equals(dependency.groupId)) {
                            resolvedDependencies.add(new Dependency(
                                    dependency.groupId,
                                    dependency.artifactId,
                                    parentDep.versionConstraints,
                                    dependency.exclusions,
                                    dependency.scope,
                                    dependency.optional,
                                    dependency.type,
                                    dependency.classifier
                            ));
                            resolved = true;
                        }
                    }
                }
                if (!resolved) {
                    resolvedDependencies.add(new Dependency(
                            dependency.groupId,
                            dependency.artifactId,
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
                        dependency.groupId,
                        dependency.artifactId,
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
                resolvedExclusions.set(j,
                        new Dependency.Exclusion(resolvedExclusionGroup, resolvedExclusionArtifact)
                );
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
                    resolvedGroup,
                    resolvedArtifact,
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
                        this.downloadPom(parentGroup, parentArtifact, parentVersion)
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
                        + parentGroup + Constants.mvnCoordinateSeparator + parentArtifact
                        + Constants.mvnCoordinateSeparator + parentVersion);
            } catch (FileNotFoundException e) {
                logger.error("Error downloading POM file for: "
                        + parentGroup + Constants.mvnCoordinateSeparator + parentArtifact
                        + Constants.mvnCoordinateSeparator + parentVersion);
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
                        if (exclusionArtifactNode != null && exclusionGroupNode != null) {
                            exclusions.add(new Dependency.Exclusion(
                                    exclusionGroupNode.getText(),
                                    exclusionArtifactNode.getText()
                            ));
                        }
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
                if (groupNode != null && artifactNode != null) {
                    dependencies.add(new Dependency(
                            groupNode.getText(),
                            artifactNode.getText(),
                            version,
                            exclusions,
                            (scopeNode != null) ? scopeNode.getText() : "",
                            (optionalNode != null)
                                    && Boolean.parseBoolean(optionalNode.getText()),
                            (typeNode != null) ? typeNode.getText() : "",
                            (classifierNode != null) ? classifierNode.getText() : ""
                    ));
                }
            }
        }
        return dependencies;
    }

    private Optional<String> downloadPom(String groupId, String artifactId, String version) {
        var pom = MavenUtilities.downloadPom(groupId, artifactId, version, this.mavenRepos).flatMap(DataExtractor::fileToString);

        if (pom.isPresent()) {
            this.mavenCoordinate = groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version;
            this.pomContents = pom.get();
            return pom;
        }

        return Optional.empty();
    }

    private Optional<String> downloadPom(String pomUrl) {
        return MavenUtilities.downloadPomFile(pomUrl).flatMap(DataExtractor::fileToString);
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
