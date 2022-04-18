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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.fasten.core.json.ObjectMapperBuilder;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Pom;

public class MavenResolverIO {

    private static final Logger LOG = LoggerFactory.getLogger(MavenResolverIO.class);

    private DSLContext dbContext;
    private File baseDir;
    private ObjectMapper om;

    public MavenResolverIO(DSLContext dbContext, File baseDir) {
        this(dbContext, baseDir, new ObjectMapperBuilder().build());
    }

    public MavenResolverIO(DSLContext dbContext, File baseDir, ObjectMapper om) {
        this.dbContext = dbContext;
        this.baseDir = baseDir;
        this.om = om;
    }

    public IMavenResolver loadResolver() {
        LOG.info("Loading MavenResolver (base folder: {})", baseDir);

        var poms = hasSerialization() //
                ? readFromDisk() //
                : readFromDB();

        if (!hasSerialization()) {
            saveToDisk(poms);
        }

        return init(poms);
    }

    public boolean hasSerialization() {
        return dbFile().exists();
    }

    private Set<Pom> readFromDisk() {
        var f = dbFile();
        LOG.info("Reading poms from {} ...", f);
        try {
            return om.readValue(f, new TypeReference<Set<Pom>>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveToDisk(Set<Pom> poms) {
        var to = dbFile();
        LOG.info("Saving poms to {} ...", to);
        try {
            var tmp = tmpFile();
            om.writeValue(tmp, poms);
            FileUtils.moveFile(tmp, to);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File tmpFile() {
        return Paths.get(baseDir.getAbsolutePath(), "poms.json-tmp").toFile();
    }

    private File dbFile() {
        return Paths.get(baseDir.getAbsolutePath(), "poms.json").toFile();
    }

    public Set<Pom> readFromDB() {
        LOG.info("Collecting poms from DB ...");

        var dbRes = dbContext.select( //
                PACKAGE_VERSIONS.METADATA, //
                PACKAGE_VERSIONS.ID) //
                .from(PACKAGE_VERSIONS) //
                .where(PACKAGE_VERSIONS.METADATA.isNotNull()) //
                .fetch();

        var poms = dbRes.stream() //
                .map(x -> {
                    try {
                        var json = x.component1().data();
                        var pom = simplify(om.readValue(json, Pom.class));
                        pom.id = x.component2();
                        return pom;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toSet());

        LOG.info("Found {} poms in DB", poms.size());

        return poms;
    }

    private static Pom simplify(Pom pom) {
        pom.forge = null;
        pom.repoUrl = null;
        pom.sourcesUrl = null;
        pom.artifactRepository = null;
        pom.packagingType = null;
        pom.parentCoordinate = null;
        pom.projectName = null;
        pom.commitTag = null;

        for (var d : new LinkedHashSet<>(pom.dependencies)) {
            // re-adding necessary on hash change
            pom.dependencies.remove(d);
            pom.dependencies.add(simplify(d));
        }

        for (var d : new LinkedHashSet<>(pom.dependencyManagement)) {
            // re-adding necessary on hash change
            pom.dependencyManagement.remove(d);
            pom.dependencyManagement.add(simplify(d));
        }

        return pom;
    }

    private static Dependency simplify(Dependency d) {
        d.setPackagingType(null);
        d.setClassifier(null);
        return d;
    }

    private static IMavenResolver init(Set<Pom> poms) {
        LOG.info("Initializing underlying data structures for MavenResolver with {} poms ...", poms.size());
        var dpdRes = new MavenDependentsResolver();
        var dpdData = new MavenDependentsData();
        dpdRes.setData(dpdData);

        var depRes = new MavenDependencyResolver();
        var depData = new MavenDependencyData();
        depRes.setData(depData);

        for (var pom : poms) {
            dpdData.add(pom);
            depData.add(pom);
        }
        
        LOG.info("Initialization done");
        return new MavenResolver(dpdRes, depRes);
    }
}