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
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
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
import java.util.*;
import java.util.stream.Collectors;

public class Test {

    public static void main(String[] args) throws Exception {
        var dbContext = PostgresConnector.getDSLContext("jdbc:postgresql://localhost:5432/fasten_python", "fastenro", false);
        var graphResolver = new GraphPypiResolver();
        graphResolver.buildDependencyGraph(dbContext, "hi"); 
        graphResolver.repl(dbContext);
    }
}

        // ObjectLinkedOpenHashSet<Revision> revisions = graphResolver.resolveDependents("sentinelhub", "3.0.0", graphResolver.getCreatedAt("sentinelhub","3.0.0", dbContext), true);
        // for (var rev : revisions.stream().sorted(Comparator.comparing(Revision::toString)).
        //         collect(Collectors.toList())) {
        //     System.out.println(rev.toString());
        // }
        // System.err.println(revisions.size() + " revisions");

//    id   | package_id | version | cg_generator | architecture |     created_at      | metadata 
// --------+------------+---------+--------------+--------------+---------------------+----------
//  587688 |     373611 | 0.1.4   | PyCG         |              | 2020-09-12 16:21:19 | {}
//  507889 |     373611 | 0.1.5   | PyCG         |              | 2020-09-12 16:21:50 | {}
//  578396 |     373611 | 0.1.6   | PyCG         |              | 2020-09-12 22:01:04 | {}
//  373611 |     373611 | 0.1.8   | PyCG         |              | 2020-09-16 02:36:00 | {}

// wexpect:4.0.1.dev3 ---> video-diet:0.1.8q