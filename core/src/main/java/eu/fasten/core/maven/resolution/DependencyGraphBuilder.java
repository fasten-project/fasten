/*
 * Copyright 2022 Delft University of Technology
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
package eu.fasten.core.maven.resolution;

import static eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions.PACKAGE_VERSIONS;
import static eu.fasten.core.utils.Asserts.assertNotNullOrEmpty;

import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.fasten.core.json.ObjectMapperBuilder;
import eu.fasten.core.maven.data.Pom;

public class DependencyGraphBuilder {

    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);
    private static final ObjectMapper OM = new ObjectMapperBuilder().build();

    private DSLContext dbContext;
    private MavenDependentsData dataDpdt;
    private MavenDependencyData dataDeps;

    public static IMavenResolver init(DSLContext dbContext, String path) {
        assertNotNullOrEmpty(path);
        var builder = new DependencyGraphBuilder(dbContext);

        // TODO clean-up method
        try {
            if (DependencyIOUtils.doesDependentsGraphExist(path)) {
//                dataDpdt = DependencyIOUtils.loadDependentsGraph(path);

            } else {
                logger.info("Building dependency graph ...");
                builder.build();

                logger.info("Serializing graph to {}", path);
                // DependencyIOUtils.serializeDependentsGraph(graph, path);

                logger.info("Finished serializing graph");
            }
            var resolverDeps = new MavenDependencyResolver();
            resolverDeps.setData(builder.dataDeps);

            return new MavenResolver(new MavenDependentsResolver(builder.dataDpdt), resolverDeps);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public DependencyGraphBuilder(DSLContext dbContext) {
        this.dbContext = dbContext;
    }

    private void build() {

        dataDpdt = new MavenDependentsData();
        dataDeps = new MavenDependencyData();
        for (var pom : getPomAnalysisResults()) {
            dataDpdt.add(pom);
            dataDeps.add(pom);
        }
    }

    public Set<Pom> getPomAnalysisResults() {

        var dbRes = dbContext.select( //
                PACKAGE_VERSIONS.METADATA, //
                PACKAGE_VERSIONS.ID) //
                .from(PACKAGE_VERSIONS) //
                .where(PACKAGE_VERSIONS.METADATA.isNotNull()) //
                .fetch();

        var pars = dbRes.stream() //
                .map(x -> {
                    try {
                        var json = x.component1().data();
                        var pom = OM.readValue(json, Pom.class);
                        pom.id = x.component2();
                        return pom;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toSet());

        return pars;
    }
}