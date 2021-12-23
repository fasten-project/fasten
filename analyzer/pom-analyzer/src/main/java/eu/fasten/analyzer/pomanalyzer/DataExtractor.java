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
package eu.fasten.analyzer.pomanalyzer;

import static eu.fasten.core.data.Constants.mvnCoordinateSeparator;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.core.data.Constants;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Exclusion;
import eu.fasten.core.maven.utils.MavenUtilities;

public class DataExtractor {

    private static final Logger logger = LoggerFactory.getLogger(DataExtractor.class);
    private String mavenCoordinate = null;
    private Pair<String, Pair<Void, List<Set<Dependency>>>> resolutionMetadata = null;

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
                    coordinate.append(groupNode.getText());
                    coordinate.append(Constants.mvnCoordinateSeparator);
                    coordinate.append(artifactNode.getText());
                    coordinate.append(Constants.mvnCoordinateSeparator);
                    coordinate.append(versionNode.getText());
                    return coordinate.toString();
                }
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file from: " + pomUrl);
            throw new RuntimeException(e);
        } catch (IOException e) {
            logger.error("Error downloading POM file from: " + pomUrl);
            throw new RuntimeException(e);
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
    public long extractReleaseDate(String groupId, String artifactId, String version, String artifactRepo) {
        URLConnection connection;
        try {
            connection = new URL(MavenUtilities.getPomUrl(groupId, artifactId, version, artifactRepo)).openConnection();
        } catch (IOException e) {
            logger.error("Could not extract release date", e);
            return -1;
        }
        var lastModified = connection.getHeaderField("Last-Modified");
        if (lastModified == null) {
            return -1;
        }
        Date releaseDate;
        try {
            releaseDate = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH).parse(lastModified);
        } catch (ParseException e) {
            logger.error("Could not parse extracted release date", e);
            return -1;
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
        for (var repo : (Iterable)null) {
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
                packaging = packagingNode.getText();
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
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
                name = nameNode.getText();
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
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
                    var parentGroup = parentGroupNode.getText();
                    var parentArtifact = parentArtifactNode.getText();
                    var parentVersion = parentVersionNode.getText();
                    parent = parentGroup + Constants.mvnCoordinateSeparator + parentArtifact
                            + Constants.mvnCoordinateSeparator + parentVersion;
                }
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        }
        return parent;
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
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        }
        return repoUrl;
    }

    private Element getPomRootElement(String groupId, String artifactId, String version)
            throws DocumentException {
        String requestedCoord = groupId + Constants.mvnCoordinateSeparator + artifactId + Constants.mvnCoordinateSeparator + version;
        boolean isCurrentCoord = requestedCoord.equals(this.mavenCoordinate);

//        String content = isCurrentCoord ?
//                this.pomContents :
//                this.downloadPom(groupId, artifactId, version).orElseThrow(() -> new NoSuchElementException("could not find pom file for " + requestedCoord));
        String content = "";
        var pomByteStream = new ByteArrayInputStream(content.getBytes());
        return new SAXReader().read(pomByteStream).getRootElement();
    }

    private Element getPomRootElement(String pomUrl) throws IOException, DocumentException {
    	ByteArrayInputStream pomByteStream = null;//new ByteArrayInputStream(this.downloadPom(pomUrl).orElseThrow(FileNotFoundException::new).getBytes());
        return new SAXReader().read(pomByteStream).getRootElement();
    }

    private void updateResolutionMetadata(String groupId, String artifactId, String version, Element pom) {
        if (this.resolutionMetadata == null
                || !this.resolutionMetadata.getLeft()
                .equals(groupId + Constants.mvnCoordinateSeparator + artifactId
                        + Constants.mvnCoordinateSeparator + version)) {
            var metadata = this.extractDependencyResolutionMetadata(pom);
            String gav = groupId + mvnCoordinateSeparator + artifactId + mvnCoordinateSeparator + version;
			this.resolutionMetadata = new ImmutablePair<>(gav, metadata);
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
            }
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
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
    public Pair<Set<Dependency>, Set<Dependency>> extractDependencyData(String groupId, String artifactId, String version) {
        var dependencies = new HashSet<Dependency>();
        var dependencyManagement = new HashSet<Dependency>();
    	
    	try {
            var pom = getPomRootElement(groupId, artifactId, version);
            updateResolutionMetadata(groupId, artifactId, version, pom);
            var dependencyManagementNode = pom.selectSingleNode("./*[local-name()='dependencyManagement']");
            if (dependencyManagementNode != null) {
                var dependenciesNode = dependencyManagementNode
                        .selectSingleNode("./*[local-name()='dependencies']");
                dependencyManagement.addAll(extractDependencies(dependenciesNode));
            }
            var dependenciesNode = pom.selectSingleNode("./*[local-name()='dependencies']");
            dependencies.addAll(extractDependencies(dependenciesNode));
        } catch (DocumentException e) {
            logger.error("Error parsing POM file for: "
                    + groupId + Constants.mvnCoordinateSeparator + artifactId
                    + Constants.mvnCoordinateSeparator + version);
        }
        return ImmutablePair.of(dependencies, dependencyManagement);
    }

//    private List<Dependency> resolveDependencies(List<Dependency> dependencies,
//                                                 Map<String, String> properties,
//                                                 List<Set<Dependency>> depManagements,
//                                                 Element pom) {
//        var resolvedDependencies = new ArrayList<Dependency>();
//        for (var dependency : dependencies) {
//            if (dependency.versionConstraints.get(0).lowerBound.equals("*")) {
//                var resolved = false;
//                for (var dmDeps : depManagements) {
//                    for (var parentDep : dmDeps) {
//                        if (parentDep.artifactId.equals(dependency.artifactId)
//                                && parentDep.groupId.equals(dependency.groupId)) {
//                            resolvedDependencies.add(new Dependency(
//                                    dependency.groupId,
//                                    dependency.artifactId,
//                                    parentDep.versionConstraints,
//                                    dependency.exclusions,
//                                    dependency.scope,
//                                    dependency.optional,
//                                    dependency.type,
//                                    dependency.classifier
//                            ));
//                            resolved = true;
//                        }
//                    }
//                }
//                if (!resolved) {
//                    resolvedDependencies.add(new Dependency(
//                            dependency.groupId,
//                            dependency.artifactId,
//                            dependency.getVersion(),
//                            dependency.exclusions,
//                            dependency.scope,
//                            dependency.optional,
//                            dependency.type,
//                            dependency.classifier
//                    ));
//                }
//            } else {
//                resolvedDependencies.add(dependency);
//            }
//        }
//        for (int i = 0; i < resolvedDependencies.size(); i++) {
//            var dep = resolvedDependencies.get(i);
//            var resolvedArtifact = dep.artifactId;
//            var resolvedGroup = dep.groupId;
//            var resolvedExclusions = dep.exclusions;
//            for (int j = 0; j < resolvedExclusions.size(); j++) {
//                var exclusion = dep.exclusions.get(j);
//                var resolvedExclusionGroup = exclusion.groupId;
//                var resolvedExclusionArtifact = exclusion.artifactId;
//                resolvedExclusions.set(j,
//                        new Dependency.Exclusion(resolvedExclusionGroup, resolvedExclusionArtifact)
//                );
//            }
//            var resolvedScope = dep.scope;
//            resolvedDependencies.set(i, new Dependency(
//                    resolvedGroup,
//                    resolvedArtifact,
//                    dep.versionConstraints,
//                    resolvedExclusions,
//                    resolvedScope,
//                    dep.optional,
//                    resolvedType,
//                    resolvedClassifier
//            ));
//        }
//        return resolvedDependencies;
//    }

    private Pair<Void, List<Set<Dependency>>> extractDependencyResolutionMetadata(Node pomRoot) {
        var dependencyManagements = new LinkedList<Set<Dependency>>();
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
                        Optional.of("asd")//this.downloadPom(parentGroup, parentArtifact, parentVersion)
                                .orElseThrow(FileNotFoundException::new).getBytes())).getRootElement();
                var parentMetadata = this.extractDependencyResolutionMetadata(parentPom);
                var dependencyManagementNode = parentPom
                        .selectSingleNode("./*[local-name()='dependencyManagement']");
                Set<Dependency> dependencyManagement;
                if (dependencyManagementNode != null) {
                    var dependenciesNode = dependencyManagementNode
                            .selectSingleNode("./*[local-name()='dependencies']");
                    var dependencies = extractDependencies(dependenciesNode);
                    dependencyManagement = dependencies;
                } else {
                    dependencyManagement = new HashSet<>();
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
        return new ImmutablePair<Void, List<Set<Dependency>>>(null, dependencyManagements);
    }

    private Set<Dependency> extractDependencies(Node dependenciesNode) {
        var dependencies = new HashSet<Dependency>();
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
                var exclusions = new HashSet<Exclusion>();
                if (exclusionsNode != null) {
                    for (var exclusionNode : exclusionsNode
                            .selectNodes("./*[local-name()='exclusion']")) {
                        var exclusionArtifactNode = exclusionNode
                                .selectSingleNode("./*[local-name()='artifactId']");
                        var exclusionGroupNode = exclusionNode
                                .selectSingleNode("./*[local-name()='groupId']");
                        if (exclusionArtifactNode != null && exclusionGroupNode != null) {
                            exclusions.add(new Exclusion(
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
}