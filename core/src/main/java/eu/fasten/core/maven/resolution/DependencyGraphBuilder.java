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

package eu.fasten.core.maven.resolution;

import static eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions.PACKAGE_VERSIONS;
import static eu.fasten.core.utils.Asserts.assertNotNullOrEmpty;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.PomAnalysisResult;
import eu.fasten.core.maven.data.VersionConstraint;

public class DependencyGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    public static IMavenResolver init(DSLContext dbContext, String path) {
        try {
            MavenDependentsGraph graph;
            if (DependencyIOUtils.doesDependentsGraphExist(path)) {
                graph = DependencyIOUtils.loadDependentsGraph(path);
            
            } else {
                assertNotNullOrEmpty(path);
                logger.info("Building dependency graph ...");
                var graphBuilder = new DependencyGraphBuilder();
                graph = graphBuilder.buildDependentsGraph(dbContext);

                logger.info("Serializing graph to {}", path);
                //DependencyIOUtils.serializeDependentsGraph(graph, path);
                
                logger.info("Finished serializing graph");
            }
            return new MavenResolver(graph);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public MavenDependentsGraph buildDependentsGraph(DSLContext dbContext) {
        logger.info("Retrieving package versions ...");
        var poms = getPomAnalysisResults(dbContext);
        logger.info("Found {} package versions.", poms.size());

        var dependents = new MavenDependentsGraph();
        poms.forEach(pom -> dependents.add(pom));

        return dependents;
    }
    
    public Set<PomAnalysisResult> getPomAnalysisResults(DSLContext dbContext) {

        var dbRes = dbContext.select( //
                PACKAGE_VERSIONS.METADATA, //
                PACKAGE_VERSIONS.ID) //
                .from(PACKAGE_VERSIONS) //
                .where(PACKAGE_VERSIONS.METADATA.isNotNull()) //
                .fetch();

        var pars = dbRes.stream() //
                .map(x -> {
                    var json = new JSONObject(x.component1().data());

                    var par = new PomAnalysisResult();
                    par.id = x.component2();
                    par.groupId = json.getString("groupId");
                    par.artifactId = json.getString("artifactId");
                    par.version = json.getString("version");
                    par.releaseDate = json.getLong("releaseDate");

                    var deps = json.getJSONArray("dependencies");
                    for (var i = 0; i < deps.length(); i++) {
                        var dep = deps.getJSONObject(i);
                        par.dependencies.add(Dependency.fromJSON(dep));
                    }

                    var mgts = json.getJSONArray("dependencyManagement");
                    for (var i = 0; i < mgts.length(); i++) {
                        var mgt = mgts.getJSONObject(i);
                        par.dependencyManagement.add(Dependency.fromJSON(mgt));
                    }

                    return par;
                }).collect(Collectors.toSet());

        return pars;
    }

    public List<PomAnalysisResult> findMatchingRevisions(Collection<PomAnalysisResult> revisions,
            Set<VersionConstraint> constraints) {
        if (revisions == null) {
            return Collections.emptyList();
        }
        return revisions.stream() //
                .filter(r -> DependencyGraphBuilder.isMatch(r, constraints)) //
                .collect(Collectors.toList());
    }

    private static boolean isMatch(PomAnalysisResult r, Set<VersionConstraint> constraints) {
        var version = new DefaultArtifactVersion(r.version);
        for (var constraint : constraints) {
            if (isHardConstraint(constraint)) {
                if (checkVersionLowerBound(constraint, version) && checkVersionUpperBound(constraint, version)) {
                    return true;
                }
            } else {
                if (isSimpleVersion(constraint) && new DefaultArtifactVersion(constraint.lowerBound).equals(version)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isSimpleVersion(VersionConstraint constraint) {
        return constraint.lowerBound.equals(constraint.upperBound);
    }

    private static boolean isHardConstraint(VersionConstraint constraint) {
        var spec = constraint.toString();
        boolean isHardConstraint = (spec.startsWith("[") || spec.startsWith("("))
                && (spec.endsWith("]") || spec.endsWith(")"));
        return isHardConstraint;
    }

    private static boolean checkVersionLowerBound(VersionConstraint constraint, DefaultArtifactVersion version) {
        if (constraint.lowerBound.isEmpty()) {
            return true;
        }
        if (constraint.isLowerBoundInclusive) {
            return version.compareTo(new DefaultArtifactVersion(constraint.lowerBound)) >= 0;
        } else {
            return version.compareTo(new DefaultArtifactVersion(constraint.lowerBound)) > 0;
        }
    }

    private static boolean checkVersionUpperBound(VersionConstraint constraint, DefaultArtifactVersion version) {
        if (constraint.upperBound.isEmpty()) {
            return true;
        }
        if (constraint.isUpperBoundInclusive) {
            return version.compareTo(new DefaultArtifactVersion(constraint.upperBound)) <= 0;
        } else {
            return version.compareTo(new DefaultArtifactVersion(constraint.upperBound)) < 0;
        }
    }

    private static String strip(String s) {
        return s.replaceAll("[\\n\\t ]", "");
    }
}
