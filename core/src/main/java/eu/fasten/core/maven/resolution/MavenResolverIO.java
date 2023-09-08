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
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.c0ps.maven.MavenUtilities;
import dev.c0ps.maven.data.Pom;
import dev.c0ps.maven.resolution.IMavenResolver;
import dev.c0ps.maven.resolution.MavenDependencyResolver;
import dev.c0ps.maven.resolution.MavenDependentsResolver;
import dev.c0ps.maven.resolution.MavenResolver;
import dev.c0ps.maven.resolution.MavenResolverData;
import eu.fasten.core.json.FastenObjectMapperBuilder;

public class MavenResolverIO {

    private static final Logger LOG = LoggerFactory.getLogger(MavenResolverIO.class);
    private static final int PG_FETCH_SIZE = 10000;

    private final DSLContext dbContext;

    private File baseDir;
    private ObjectMapper om;

    public MavenResolverIO(DSLContext dbContext, File baseDir) {
        this(dbContext, baseDir, new FastenObjectMapperBuilder().build());
    }

    // TODO inject and @Named annotations, get rid of DB variant
    public MavenResolverIO(DSLContext dbContext, File baseDir, ObjectMapper om) {
        this.dbContext = dbContext;
        this.baseDir = baseDir;
        this.om = om;

        if (!this.baseDir.exists()) {
            this.baseDir.mkdir();
        }
    }

    public IMavenResolver loadResolver() {
        LOG.info("Loading MavenResolver (base folder: {})", baseDir);

        var poms = hasSerialization() //
                ? readFromDisk() //
                : readFromDB();

        if (!hasSerialization()) {
            saveToDisk(poms);
        }

        return initResolver(poms);
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
            createFileIfDoNotExist(tmp);
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

        var poms = new HashSet<Pom>();
        var numberOfFetchedPoms = 0;

        var dbRes = dbContext.select( //
                PACKAGE_VERSIONS.METADATA, //
                PACKAGE_VERSIONS.ID) //
                .from(PACKAGE_VERSIONS) //
                .where(PACKAGE_VERSIONS.METADATA.isNotNull()).fetchSize(PG_FETCH_SIZE); //

        try (var cursor = dbRes.fetchLazy()) {
            while (cursor.hasNext()) {
                var record = cursor.fetchNext();
                if (record != null) {
                    try {
                        var json = record.component1().data();
                        var pom = MavenUtilities.simplify(om.readValue(json, Pom.class));
                        pom.id = record.component2();
                        poms.add(pom);
                        numberOfFetchedPoms++;
                        if (numberOfFetchedPoms % PG_FETCH_SIZE == 0) {
                            LOG.info("Fetched {} POMs", numberOfFetchedPoms);
                        }
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        LOG.info("Found {} poms in DB", poms.size());

        return poms;
    }

    private static IMavenResolver initResolver(Set<Pom> poms) {
        LOG.info("Initializing underlying data structures for MavenResolver with {} poms ...", poms.size());
        var data = new MavenResolverData();

        var dpdRes = new MavenDependentsResolver();
        dpdRes.setData(data);
        var depRes = new MavenDependencyResolver();
        depRes.setData(data);

        for (var pom : poms) {
            data.add(pom);
        }

        LOG.info("Initialization done");
        return new MavenResolver(dpdRes, depRes);
    }

    private void createFileIfDoNotExist(File file) {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                LOG.error("Could not create file {}", file.toPath());
            }
        } else {
            LOG.info("File {} exists!", file.toPath());
        }
    }
}