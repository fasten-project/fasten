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

package eu.fasten.analyzer.metadataplugin.db;

import eu.fasten.analyzer.metadataplugin.db.tables.*;
import eu.fasten.analyzer.metadataplugin.db.tables.records.*;
import org.jooq.DSLContext;
import org.jooq.InsertResultStep;
import org.jooq.InsertValuesStep3;
import org.jooq.InsertValuesStep4;
import org.jooq.InsertValuesStep5;
import org.jooq.JSONB;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetadataDaoTest {

    private MetadataDao metadataDao;
    private DSLContext context;

    @BeforeEach
    public void setUp() {
        context = Mockito.mock(DSLContext.class);
        metadataDao = new MetadataDao(context);
    }

    @Test
    public void insertPackageTest() {
        long id = 1;
        String packageName = "package1";
        String projectName = "project1";
        String repository = "repository1";
        Timestamp createdAt = new Timestamp(1);
        InsertValuesStep4<PackagesRecord, String, String, String, Timestamp> insertValues = Mockito.mock(InsertValuesStep4.class);
        Mockito.when(context.insertInto(Packages.PACKAGES,
                Packages.PACKAGES.PACKAGE_NAME, Packages.PACKAGES.PROJECT_NAME, Packages.PACKAGES.REPOSITORY,
                Packages.PACKAGES.CREATED_AT)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageName, projectName, repository, createdAt)).thenReturn(insertValues);
        InsertResultStep<PackagesRecord> insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Packages.PACKAGES.ID)).thenReturn(insertResult);
        PackagesRecord record = new PackagesRecord(id, packageName, projectName, repository, createdAt);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertPackage(packageName, projectName, repository, createdAt);
        assertEquals(id, result);
    }

    @Test
    public void insertPackageVersionTest() {
        long id = 1;
        long packageId = 42;
        String version = "1.0.0";
        Timestamp createdAt = new Timestamp(1);
        JSONObject metadata = new JSONObject("{\"foo\":\"bar\"}");
        InsertValuesStep4<PackageVersionsRecord, Long, String, Timestamp, JSONB> insertValues = Mockito.mock(InsertValuesStep4.class);
        Mockito.when(context.insertInto(PackageVersions.PACKAGE_VERSIONS,
                PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID, PackageVersions.PACKAGE_VERSIONS.VERSION,
                PackageVersions.PACKAGE_VERSIONS.CREATED_AT, PackageVersions.PACKAGE_VERSIONS.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, version, createdAt, JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        InsertResultStep<PackageVersionsRecord> insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(PackageVersions.PACKAGE_VERSIONS.ID)).thenReturn(insertResult);
        PackageVersionsRecord record = new PackageVersionsRecord(id, packageId, version, createdAt, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertPackageVersion(packageId, version, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertDependencyTest() {
        long packageId = 8;
        long dependencyId = 42;
        String versionRange = "1.0.0-1.9.9";
        InsertValuesStep3<DependenciesRecord, Long, Long, String> insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Dependencies.DEPENDENCIES, Dependencies.DEPENDENCIES.PACKAGE_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyId, versionRange)).thenReturn(insertValues);
        InsertResultStep<DependenciesRecord> insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Dependencies.DEPENDENCIES.PACKAGE_ID)).thenReturn(insertResult);
        DependenciesRecord record = new DependenciesRecord(packageId, dependencyId, versionRange);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertDependency(packageId, dependencyId, versionRange);
        assertEquals(packageId, result);
    }

    @Test
    public void insertFileTest() {
        long id = 1;
        long packageId = 42;
        String namespaces = "namespace1;namespace2";
        byte[] sha256 = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Timestamp createdAt = new Timestamp(1);
        JSONObject metadata = new JSONObject("{\"foo\":\"bar\"}");
        InsertValuesStep5<FilesRecord, Long, String, byte[], Timestamp, JSONB> insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Files.FILES, Files.FILES.PACKAGE_ID, Files.FILES.NAMESPACES, Files.FILES.SHA256,
                Files.FILES.CREATED_AT, Files.FILES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, namespaces, sha256, createdAt, JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        InsertResultStep<FilesRecord> insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Files.FILES.ID)).thenReturn(insertResult);
        FilesRecord record = new FilesRecord(id, packageId, namespaces, sha256, createdAt, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertFile(packageId, namespaces, sha256, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertCallableTest() {
        long id = 1;
        long fileId = 42;
        String fastenUri = "URI";
        Timestamp createdAt = new Timestamp(1);
        JSONObject metadata = new JSONObject("{\"foo\":\"bar\"}");
        InsertValuesStep4<CallablesRecord, Long, String, Timestamp, JSONB> insertValues = Mockito.mock(InsertValuesStep4.class);
        Mockito.when(context.insertInto(Callables.CALLABLES, Callables.CALLABLES.FILE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(fileId, fastenUri, createdAt, JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        InsertResultStep<CallablesRecord> insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Callables.CALLABLES.ID)).thenReturn(insertResult);
        CallablesRecord record = new CallablesRecord(id, fileId, fastenUri, createdAt, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertCallable(fileId, fastenUri, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertEdgeTest() {
        long sourceId = 1;
        long targetId = 2;
        JSONObject metadata = new JSONObject("{\"foo\":\"bar\"}");
        InsertValuesStep3<EdgesRecord, Long, Long, JSONB> insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Edges.EDGES, Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID,
                Edges.EDGES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(sourceId, targetId, JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        InsertResultStep<EdgesRecord> insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Edges.EDGES.SOURCE_ID)).thenReturn(insertResult);
        EdgesRecord record = new EdgesRecord(sourceId, targetId, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertEdge(sourceId, targetId, metadata);
        assertEquals(sourceId, result);
    }
}
