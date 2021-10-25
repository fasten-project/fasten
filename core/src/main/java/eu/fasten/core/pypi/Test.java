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

package eu.fasten.core.pypi;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.pypi.data.Dependency;
import eu.fasten.core.pypi.data.Revision;
import eu.fasten.core.pypi.data.DependencyEdge;
import eu.fasten.core.pypi.utils.DependencyGraphUtilities;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Test {


    // public static void main(String[] args) throws Exception {
    //     var graphResolver = new GraphPypiResolver();
    //     // export PGPASSWORD=fasten
    //     //  mvn -T 1C  install -Dpypi.test.skip -DskipTests && java -cp docker/rest-api/restapi-plugin-0.1.0-SNAPSHOT-with-dependencies.jar eu.fasten.core.pypi.Test
    //     var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5432/fasten_python", "fastenro", false);
    //     var serializedGraphPath = "../mvn_dep_graph";
    //     graphResolver.buildDependencyGraph(dbContext, serializedGraphPath);    // dbContext is needed only if serializedGraphPath doesn't actually contain the proper serialized graph in order to build the graph from scratch
    //     // graphResolver.repl(dbContext);
    //     // "01.02.2021"
    //     // System.out.println(graphResolver.getCreatedAt("io.nextflow", "nxf-commons", "0.29.1", dbContext));
    //     // var dependentsSet = graphResolver.resolveDependents("io.nextflow", "nxf-commons", "0.29.1", -1, true);
    //     // io.nextflow:nxf-commons:0.29.1
    //     // System.out.println(dependentsSet);
    // }

    public static void main(String[] args) throws Exception {
        var builder = new DependencyGraphBuilder();
        // var serializedGraphPath = "../mvn_dep_graph";
        var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5432/fasten_python", "fastenro", false);
        // graphResolver.buildDependencyGraph(dbContext, serializedGraphPath);
        
        // for Map<Revision, List<Dependency>> i in builder.getDependencyList(dbContext):
        //     System.out.println(i);
        //     break;
        // System.out.println(graphResolver.dependencyGraph);
        builder.ggetDependencyList(dbContext);
        // var dependencies = builder.getDependencyList(dbContext);
        // System.out.println(dependencies.get("org.eclipse.platform:org.eclipse.debug.examples.core:1.4.800"));

        // var productRevisionMap = dependencies.keySet().stream().collect(Collectors.toConcurrentMap(
        //     Revision::product,
        //     List::of,
        //     (x, y) -> {
        //         var z = new ArrayList<Revision>();
        //         z.addAll(x);
        //         z.addAll(y);
        //         return z;
        //     })
        // );
        // System.out.println(dependencies.entrySet());


        // System.out.println(productRevisionMap.get("org.wso2.carbon.identity.framework:org.wso2.carbon.user.mgt.ui.feature"));
    }
}

