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

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenArtifactInfo;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import eu.fasten.core.data.RevisionCallGraph;

public class MavenResolver {

    public static List<RevisionCallGraph.Dependency> resolveDependencies(String mavenCoordinate) {

        MavenResolvedArtifact artifact = Maven.resolver().resolve(mavenCoordinate).withoutTransitivity().asSingle(MavenResolvedArtifact.class);

        List<RevisionCallGraph.Dependency> dependencies = new ArrayList<>();

        for (MavenArtifactInfo i : artifact.getDependencies()) {
            RevisionCallGraph.Dependency dependency = new RevisionCallGraph.Dependency(
                "mvn",
                i.getCoordinate().getGroupId() + "." + i.getCoordinate().getArtifactId(),
                Arrays.asList(new RevisionCallGraph.Constraint("[" + i.getCoordinate().getVersion() + "]")));
            dependencies.add(dependency);
        }

        return dependencies;
    }

    public static File downloadArtifact(String coordinate) {

        return Maven.resolver().resolve(coordinate).withoutTransitivity().asSingleFile();

    }
}
