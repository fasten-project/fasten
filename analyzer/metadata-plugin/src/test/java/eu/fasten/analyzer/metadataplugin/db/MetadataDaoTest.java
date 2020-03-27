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

import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.Edges;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.DependenciesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.ModulesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.PackageVersionsRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.PackagesRecord;
import org.jooq.DSLContext;
import org.jooq.InsertResultStep;
import org.jooq.InsertValuesStep3;
import org.jooq.InsertValuesStep5;
import org.jooq.JSONB;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.SelectWhereStep;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MetadataDaoTest {

    private MetadataDao metadataDao;
    private DSLContext context;

    @BeforeEach
    public void setUp() {
        context = Mockito.mock(DSLContext.class);
        metadataDao = new MetadataDao(context);
    }

    @Test
    public void changeContextTest() {
        var newContext = Mockito.mock(DSLContext.class);
        metadataDao.setContext(newContext);
        assertEquals(newContext, metadataDao.getContext());
        assertNotEquals(context, metadataDao.getContext());
    }

    @Test
    public void insertPackageTest() {
        long id = 1;
        var packageName = "package1";
        var forge = "mvn";
        var projectName = "project1";
        var repository = "repository1";
        var createdAt = new Timestamp(1);
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Packages.PACKAGES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Packages.PACKAGES.FORGE.eq(forge))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Packages.PACKAGES,
                Packages.PACKAGES.PACKAGE_NAME, Packages.PACKAGES.FORGE,
                Packages.PACKAGES.PROJECT_NAME, Packages.PACKAGES.REPOSITORY,
                Packages.PACKAGES.CREATED_AT)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageName, forge, projectName, repository, createdAt))
                .thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Packages.PACKAGES.ID)).thenReturn(insertResult);
        var record = new PackagesRecord(id, packageName, forge, projectName, repository, createdAt);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertPackage(packageName, forge, projectName, repository,
                createdAt);
        assertEquals(id, result);
    }

    @Test
    public void insertExistingPackageTest() {
        long id = 1;
        var packageName = "package1";
        var forge = "mvn";
        var projectName = "project1";
        var repository = "repository1";
        var createdAt = new Timestamp(1);
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Packages.PACKAGES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Packages.PACKAGES.FORGE.eq(forge))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(Packages.PACKAGES.ID)).thenReturn(Collections.singletonList(id));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        long result = metadataDao.insertPackage(packageName, forge, projectName, repository, createdAt);
        assertEquals(id, result);
    }

    @Test
    public void insertPackageNullTest() {
        long id = 1;
        var packageName = "package1";
        var forge = "mvn";
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Packages.PACKAGES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Packages.PACKAGES.FORGE.eq(forge))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(true);
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Packages.PACKAGES,
                Packages.PACKAGES.PACKAGE_NAME, Packages.PACKAGES.FORGE,
                Packages.PACKAGES.PROJECT_NAME, Packages.PACKAGES.REPOSITORY,
                Packages.PACKAGES.CREATED_AT)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageName, forge, null, null, null)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Packages.PACKAGES.ID)).thenReturn(insertResult);
        var record = new PackagesRecord(id, packageName, forge, null, null, null);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertPackage(packageName, forge, null, null, null);
        assertEquals(id, result);
    }

    @Test
    public void insertMultiplePackagesTest() throws IllegalArgumentException {
        var ids = Arrays.asList(1L, 2L);
        var packageNames = Arrays.asList("package1", "package2");
        var forges = Arrays.asList("mvn", "mvn");
        var projectNames = Arrays.asList("project1", "project2");
        var repositories = Arrays.asList("repository1", "repository2");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Packages.PACKAGES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Packages.PACKAGES.PACKAGE_NAME.eq(packageNames.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Packages.PACKAGES.FORGE.eq(forges.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectStep.where(Packages.PACKAGES.PACKAGE_NAME.eq(packageNames.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Packages.PACKAGES.FORGE.eq(forges.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Packages.PACKAGES,
                Packages.PACKAGES.PACKAGE_NAME, Packages.PACKAGES.FORGE,
                Packages.PACKAGES.PROJECT_NAME, Packages.PACKAGES.REPOSITORY,
                Packages.PACKAGES.CREATED_AT)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageNames.get(0), forges.get(0), projectNames.get(0),
                repositories.get(0), createdAt.get(0))).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageNames.get(1), forges.get(1), projectNames.get(1),
                repositories.get(1), createdAt.get(1))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Packages.PACKAGES.ID)).thenReturn(insertResult);
        var record1 = new PackagesRecord(ids.get(0), packageNames.get(0), forges.get(0),
                projectNames.get(0), repositories.get(0), createdAt.get(0));
        var record2 = new PackagesRecord(ids.get(1), packageNames.get(1), forges.get(1),
                projectNames.get(1), repositories.get(1), createdAt.get(1));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        List<Long> result = metadataDao.insertPackages(packageNames, forges, projectNames,
                repositories, createdAt);
        assertEquals(ids, result);
    }

    @Test
    public void insertMultiplePackagesErrorTest() {
        var packageNames = Arrays.asList("package1", "package2");
        var forges = Collections.singletonList("mvn");
        var projectNames = Arrays.asList("project1", "project2");
        var repositories = Arrays.asList("repository1", "repository2");
        var createdAt = Collections.singletonList(new Timestamp(1));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertPackages(packageNames, forges, projectNames, repositories, createdAt);
        });
    }

    @Test
    public void insertMultiplePackagesErrorTest1() {
        var packageNames = Arrays.asList("package1", "package2");
        var forges = Arrays.asList("mvn", "mvn");
        var projectNames = Collections.singletonList("project1");
        var repositories = Arrays.asList("repository1", "repository2");
        var createdAt = Collections.singletonList(new Timestamp(1));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertPackages(packageNames, forges, projectNames, repositories, createdAt);
        });
    }

    @Test
    public void insertMultiplePackagesErrorTest2() {
        var packageNames = Arrays.asList("package1", "package2");
        var forges = Arrays.asList("mvn", "mvn");
        var projectNames = Arrays.asList("project1", "project2");
        var repositories = Collections.singletonList("repo");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertPackages(packageNames, forges, projectNames, repositories, createdAt);
        });
    }

    @Test
    public void insertMultiplePackagesErrorTest3() {
        var packageNames = Arrays.asList("package1", "package2");
        var forges = Arrays.asList("mvn", "mvn");
        var projectNames = Arrays.asList("project1", "project2");
        var repositories = Arrays.asList("repo1", "repo2");
        var createdAt = Collections.singletonList(new Timestamp(1));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertPackages(packageNames, forges, projectNames, repositories, createdAt);
        });
    }

    @Test
    public void insertPackageVersionTest() {
        long id = 1;
        long packageId = 42;
        var cgGenerator = "OPAL";
        var version = "1.0.0";
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(PackageVersions.PACKAGE_VERSIONS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR.eq(cgGenerator))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(PackageVersions.PACKAGE_VERSIONS,
                PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID, PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR,
                PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT,
                PackageVersions.PACKAGE_VERSIONS.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, cgGenerator, version, createdAt,
                JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(PackageVersions.PACKAGE_VERSIONS.ID)).thenReturn(insertResult);
        var record = new PackageVersionsRecord(id, packageId, cgGenerator, version, createdAt, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertPackageVersion(packageId, cgGenerator, version, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertExistingPackageVersionTest() {
        long id = 1;
        long packageId = 42;
        var cgGenerator = "OPAL";
        var version = "1.0.0";
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(PackageVersions.PACKAGE_VERSIONS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR.eq(cgGenerator))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(PackageVersions.PACKAGE_VERSIONS.ID)).thenReturn(Collections.singletonList(id));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        long result = metadataDao.insertPackageVersion(packageId, cgGenerator, version, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertPackageVersionNullTest() {
        long id = 1;
        long packageId = 42;
        var cgGenerator = "OPAL";
        var version = "1.0.0";
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(PackageVersions.PACKAGE_VERSIONS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR.eq(cgGenerator))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(true);
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(PackageVersions.PACKAGE_VERSIONS,
                PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID, PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR,
                PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT,
                PackageVersions.PACKAGE_VERSIONS.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, cgGenerator, version, null,
                null)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(PackageVersions.PACKAGE_VERSIONS.ID)).thenReturn(insertResult);
        var record = new PackageVersionsRecord(id, packageId, cgGenerator, version, null, null);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertPackageVersion(packageId, cgGenerator, version, null, null);
        assertEquals(id, result);
    }

    @Test
    public void insertMultiplePackageVersionsTest() throws IllegalArgumentException {
        var ids = Arrays.asList(1L, 2L);
        var packageId = 42L;
        var cgGenerators = Arrays.asList("OPAL", "WALA");
        var versions = Arrays.asList("1.0.0", "2.0.0");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(PackageVersions.PACKAGE_VERSIONS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR.eq(cgGenerators.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(versions.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR.eq(cgGenerators.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(versions.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(PackageVersions.PACKAGE_VERSIONS,
                PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID, PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR,
                PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT,
                PackageVersions.PACKAGE_VERSIONS.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, cgGenerators.get(0), versions.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()))).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, cgGenerators.get(1), versions.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(PackageVersions.PACKAGE_VERSIONS.ID)).thenReturn(insertResult);
        var record1 = new PackageVersionsRecord(ids.get(0), packageId, cgGenerators.get(0), versions.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()));
        var record2 = new PackageVersionsRecord(ids.get(1), packageId, cgGenerators.get(1), versions.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertPackageVersions(packageId, cgGenerators, versions, createdAt, metadata);
        assertEquals(ids, result);
    }

    @Test
    public void insertMultiplePackageVersionsErrorTest() {
        var packageId = 42L;
        var cgGenerators = Arrays.asList("OPAL", "WALA");
        var versions = Arrays.asList("1.0.0", "2.0.0");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertPackageVersions(packageId, cgGenerators, versions, createdAt, metadata);
        });
    }

    @Test
    public void insertMultiplePackageVersionsErrorTest1() {
        var packageId = 42L;
        var cgGenerators = Collections.singletonList("OPAL");
        var versions = Arrays.asList("1.0.0", "2.0.0");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"),
                new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertPackageVersions(packageId, cgGenerators, versions, createdAt, metadata);
        });
    }

    @Test
    public void insertMultiplePackageVersionsErrorTest2() {
        var packageId = 42L;
        var cgGenerators = Arrays.asList("OPAL", "WALA");
        var versions = Collections.singletonList("1.0.0");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"),
                new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertPackageVersions(packageId, cgGenerators, versions, createdAt, metadata);
        });
    }

    @Test
    public void insertMultiplePackageVersionsErrorTest3() {
        var packageId = 42L;
        var cgGenerators = Arrays.asList("OPAL", "WALA");
        var versions = Arrays.asList("1.0.0", "2.0.0");
        var createdAt = Collections.singletonList(new Timestamp(1));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"),
                new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertPackageVersions(packageId, cgGenerators, versions, createdAt, metadata);
        });
    }

    @Test
    public void insertDependencyTest() {
        long packageId = 8;
        long dependencyId = 42;
        var versionRange = new String[]{"1.0.0-1.9.9"};
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Dependencies.DEPENDENCIES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Dependencies.DEPENDENCIES.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRange))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Dependencies.DEPENDENCIES, Dependencies.DEPENDENCIES.PACKAGE_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyId, versionRange)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Dependencies.DEPENDENCIES.PACKAGE_ID)).thenReturn(insertResult);
        var record = new DependenciesRecord(packageId, dependencyId, versionRange);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertDependency(packageId, dependencyId, versionRange);
        assertEquals(packageId, result);
    }

    @Test
    public void insertDependencyEmptyTest() {
        long packageId = 8;
        long dependencyId = 42;
        var versionRange = new String[]{"1.0.0-1.9.9"};
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Dependencies.DEPENDENCIES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Dependencies.DEPENDENCIES.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRange))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(true);
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Dependencies.DEPENDENCIES, Dependencies.DEPENDENCIES.PACKAGE_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyId, versionRange)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Dependencies.DEPENDENCIES.PACKAGE_ID)).thenReturn(insertResult);
        var record = new DependenciesRecord(packageId, dependencyId, versionRange);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertDependency(packageId, dependencyId, versionRange);
        assertEquals(packageId, result);
    }

    @Test
    public void insertExistingDependencyTest() {
        long packageId = 8;
        long dependencyId = 42;
        var versionRange = new String[]{"1.0.0-1.9.9"};
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Dependencies.DEPENDENCIES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Dependencies.DEPENDENCIES.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRange))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(Dependencies.DEPENDENCIES.PACKAGE_ID)).thenReturn(Collections.singletonList(packageId));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Dependencies.DEPENDENCIES, Dependencies.DEPENDENCIES.PACKAGE_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyId, versionRange)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Dependencies.DEPENDENCIES.PACKAGE_ID)).thenReturn(insertResult);
        var record = new DependenciesRecord(packageId, dependencyId, versionRange);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertDependency(packageId, dependencyId, versionRange);
        assertEquals(packageId, result);
    }

    @Test
    public void insertMultipleDependenciesTest() throws IllegalArgumentException {
        var packageId = 1L;
        var dependencyIds = Arrays.asList(8L, 42L);
        var versionRanges = Arrays.asList(new String[]{"1.0.0-1.9.9"}, new String[]{"2.1.0-2.1.9"});
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Dependencies.DEPENDENCIES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Dependencies.DEPENDENCIES.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyIds.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRanges.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyIds.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRanges.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Dependencies.DEPENDENCIES, Dependencies.DEPENDENCIES.PACKAGE_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyIds.get(0), versionRanges.get(0))).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyIds.get(1), versionRanges.get(1))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Dependencies.DEPENDENCIES.PACKAGE_ID)).thenReturn(insertResult);
        var record1 = new DependenciesRecord(packageId, dependencyIds.get(0), versionRanges.get(0));
        var record2 = new DependenciesRecord(packageId, dependencyIds.get(1), versionRanges.get(1));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertDependencies(packageId, dependencyIds, versionRanges);
        assertEquals(packageId, result);
    }

    @Test
    public void insertMultipleDependenciesErrorTest() {
        var packageId = 1L;
        var dependencyIds = Collections.singletonList(8L);
        var versionRanges = Arrays.asList(new String[]{"1.0.0-1.9.9"}, new String[]{"2.1.0-2.1.9"});
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertDependencies(packageId, dependencyIds, versionRanges);
        });
    }

    @Test
    public void insertModuleTest() {
        long id = 1;
        long packageId = 42;
        var namespaces = "namespace1;namespace2";
        byte[] sha256 = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Modules.MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Modules.MODULES.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACES.eq(namespaces))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Modules.MODULES, Modules.MODULES.PACKAGE_ID, Modules.MODULES.NAMESPACES,
                Modules.MODULES.SHA256, Modules.MODULES.CREATED_AT, Modules.MODULES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, namespaces, sha256, createdAt, JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Modules.MODULES.ID)).thenReturn(insertResult);
        var record = new ModulesRecord(id, packageId, namespaces, sha256, createdAt, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertModule(packageId, namespaces, sha256, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertModuleNullTest() {
        long id = 1;
        long packageId = 42;
        var namespaces = "namespace1;namespace2";
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Modules.MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Modules.MODULES.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACES.eq(namespaces))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(true);
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Modules.MODULES, Modules.MODULES.PACKAGE_ID, Modules.MODULES.NAMESPACES, Modules.MODULES.SHA256,
                Modules.MODULES.CREATED_AT, Modules.MODULES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, namespaces, null, null, null)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Modules.MODULES.ID)).thenReturn(insertResult);
        var record = new ModulesRecord(id, packageId, namespaces, null, null, null);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertModule(packageId, namespaces, null, null, null);
        assertEquals(id, result);
    }

    @Test
    public void insertExistingModuleTest() {
        long id = 1;
        long packageId = 42;
        var namespaces = "namespace1;namespace2";
        byte[] sha256 = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Modules.MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Modules.MODULES.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACES.eq(namespaces))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(Modules.MODULES.ID)).thenReturn(Collections.singletonList(id));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        long result = metadataDao.insertModule(packageId, namespaces, sha256, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertMultipleModulesTest() throws IllegalArgumentException {
        var ids = Arrays.asList(1L, 2L);
        long packageId = 42;
        var namespaces = Arrays.asList("namespace1;namespace2", "namespace3;namespace4");
        var sha256s = Arrays.asList(new byte[]{0, 1, 2, 3, 4}, new byte[]{5, 6, 7, 8, 9});
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Modules.MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Modules.MODULES.PACKAGE_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACES.eq(namespaces.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACES.eq(namespaces.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Modules.MODULES, Modules.MODULES.PACKAGE_ID, Modules.MODULES.NAMESPACES, Modules.MODULES.SHA256,
                Modules.MODULES.CREATED_AT, Modules.MODULES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, namespaces.get(0), sha256s.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()))).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, namespaces.get(1), sha256s.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Modules.MODULES.ID)).thenReturn(insertResult);
        var record1 = new ModulesRecord(ids.get(0), packageId, namespaces.get(0), sha256s.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()));
        var record2 = new ModulesRecord(ids.get(1), packageId, namespaces.get(1), sha256s.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertModules(packageId, namespaces, sha256s, createdAt, metadata);
        assertEquals(ids, result);
    }

    @Test
    public void insertMultipleModulesErrorTest() {
        long packageId = 42;
        var namespaces = Arrays.asList("namespace1;namespace2", "namespace3;namespace4");
        var sha256s = Collections.singletonList(new byte[]{0, 1, 2, 3, 4});
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertModules(packageId, namespaces, sha256s, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleModulesErrorTest1() {
        long packageId = 42;
        var namespaces = Arrays.asList("namespace1;namespace2", "namespace3;namespace4");
        var sha256s = Arrays.asList(new byte[]{0, 1, 2, 3, 4}, new byte[]{5, 6, 7, 8, 9});
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertModules(packageId, namespaces, sha256s, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleModulesErrorTest2() {
        long packageId = 42;
        var namespaces = Arrays.asList("namespace1;namespace2", "namespace3;namespace4");
        var sha256s = Arrays.asList(new byte[]{0, 1, 2, 3, 4}, new byte[]{5, 6, 7, 8, 9});
        var createdAt = Collections.singletonList(new Timestamp(1));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertModules(packageId, namespaces, sha256s, createdAt, metadata);
        });
    }

    @Test
    public void insertCallableTest() throws IllegalArgumentException {
        var id = 1L;
        long moduleId = 42;
        var fastenUri = "URI";
        boolean isResolvedCall = true;
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Callables.CALLABLES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Callables.CALLABLES.FASTEN_URI.eq(fastenUri))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Callables.CALLABLES.IS_RESOLVED_CALL.eq(isResolvedCall))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Callables.CALLABLES, Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_RESOLVED_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleId, fastenUri, isResolvedCall, createdAt,
                JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Callables.CALLABLES.ID)).thenReturn(insertResult);
        var record = new CallablesRecord(id, moduleId, fastenUri, isResolvedCall, createdAt,
                JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        var result = metadataDao.insertCallable(moduleId, fastenUri, isResolvedCall, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertCallableNullTest() throws IllegalArgumentException {
        var id = 1L;
        long moduleId = 42;
        var fastenUri = "URI";
        var isResolvedCall = false;
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Callables.CALLABLES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Callables.CALLABLES.FASTEN_URI.eq(fastenUri))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Callables.CALLABLES.IS_RESOLVED_CALL.eq(isResolvedCall))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(true);
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Callables.CALLABLES, Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_RESOLVED_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleId, fastenUri, isResolvedCall, null, null)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Callables.CALLABLES.ID)).thenReturn(insertResult);
        var record = new CallablesRecord(id, moduleId, fastenUri, isResolvedCall, null, null);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        var result = metadataDao.insertCallable(moduleId, fastenUri, isResolvedCall, null, null);
        assertEquals(id, result);
    }

    @Test
    public void insertExistingCallableTest() throws IllegalArgumentException {
        var id = 1L;
        long moduleId = 42;
        var fastenUri = "URI";
        var isResolvedCall = false;
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Callables.CALLABLES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Callables.CALLABLES.FASTEN_URI.eq(fastenUri))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Callables.CALLABLES.IS_RESOLVED_CALL.eq(isResolvedCall))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(Callables.CALLABLES.ID)).thenReturn(Collections.singletonList(id));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var result = metadataDao.insertCallable(moduleId, fastenUri, isResolvedCall, null, null);
        assertEquals(id, result);
    }

    @Test
    public void insertMultipleCallablesTest() throws IllegalArgumentException {
        var ids = Arrays.asList(1L, 2L);
        long moduleId = 42;
        var fastenUris = Arrays.asList("URI1", "URI2");
        var areResolvedCalls = Arrays.asList(true, false);
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Callables.CALLABLES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Callables.CALLABLES.FASTEN_URI.eq(fastenUris.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Callables.CALLABLES.IS_RESOLVED_CALL.eq(areResolvedCalls.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectStep.where(Callables.CALLABLES.FASTEN_URI.eq(fastenUris.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Callables.CALLABLES.IS_RESOLVED_CALL.eq(areResolvedCalls.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Callables.CALLABLES, Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_RESOLVED_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleId, fastenUris.get(0), areResolvedCalls.get(0),
                createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()))).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleId, fastenUris.get(1), areResolvedCalls.get(1),
                createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Callables.CALLABLES.ID)).thenReturn(insertResult);
        var record1 = new CallablesRecord(ids.get(0), moduleId, fastenUris.get(0),
                areResolvedCalls.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()));
        var record2 = new CallablesRecord(ids.get(1), moduleId, fastenUris.get(1),
                areResolvedCalls.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertCallables(moduleId, fastenUris, areResolvedCalls, createdAt, metadata);
        assertEquals(ids, result);
    }

    @Test
    public void insertMultipleCallablesErrorTest() {
        long moduleId = 42;
        var fastenUris = Arrays.asList("URI1", "URI2");
        var areResolvedCalls = Arrays.asList(true, false);
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertCallables(moduleId, fastenUris, areResolvedCalls, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleCallablesErrorTest1() {
        long moduleId = 42;
        var fastenUris = Arrays.asList("URI1", "URI2");
        var areResolvedCalls = Collections.singletonList(true);
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertCallables(moduleId, fastenUris, areResolvedCalls, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleCallablesErrorTest2() {
        long moduleId = 42;
        var fastenUris = Collections.singletonList("URI1");
        var areResolvedCalls = Collections.singletonList(true);
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertCallables(moduleId, fastenUris, areResolvedCalls, createdAt, metadata);
        });
    }

    @Test
    public void insertEdgeTest() {
        long sourceId = 1;
        long targetId = 2;
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Edges.EDGES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Edges.EDGES.SOURCE_ID.eq(sourceId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Edges.EDGES.TARGET_ID.eq(targetId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Edges.EDGES, Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID,
                Edges.EDGES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(sourceId, targetId, JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Edges.EDGES.SOURCE_ID)).thenReturn(insertResult);
        var record = new EdgesRecord(sourceId, targetId, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertEdge(sourceId, targetId, metadata);
        assertEquals(sourceId, result);
    }

    @Test
    public void insertEdgeNullTest() {
        long sourceId = 1;
        long targetId = 2;
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Edges.EDGES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Edges.EDGES.SOURCE_ID.eq(sourceId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Edges.EDGES.TARGET_ID.eq(targetId))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(true);
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Edges.EDGES, Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID,
                Edges.EDGES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(sourceId, targetId, null)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Edges.EDGES.SOURCE_ID)).thenReturn(insertResult);
        var record = new EdgesRecord(sourceId, targetId, null);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertEdge(sourceId, targetId, null);
        assertEquals(sourceId, result);
    }

    @Test
    public void insertExistingEdgeTest() {
        long sourceId = 1;
        long targetId = 2;
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Edges.EDGES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Edges.EDGES.SOURCE_ID.eq(sourceId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Edges.EDGES.TARGET_ID.eq(targetId))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(Edges.EDGES.SOURCE_ID)).thenReturn(Collections.singletonList(sourceId));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        long result = metadataDao.insertEdge(sourceId, targetId, null);
        assertEquals(sourceId, result);
    }

    @Test
    public void insertMultipleEdgesTest() throws IllegalArgumentException {
        var sourceIds = Arrays.asList(1L, 2L);
        var targetIds = Arrays.asList(3L, 4L);
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Edges.EDGES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Edges.EDGES.SOURCE_ID.eq(sourceIds.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Edges.EDGES.TARGET_ID.eq(targetIds.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectStep.where(Edges.EDGES.SOURCE_ID.eq(sourceIds.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Edges.EDGES.TARGET_ID.eq(targetIds.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Edges.EDGES, Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID,
                Edges.EDGES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(sourceIds.get(0), targetIds.get(0), JSONB.valueOf(metadata.get(0).toString()))).thenReturn(insertValues);
        Mockito.when(insertValues.values(sourceIds.get(1), targetIds.get(1), JSONB.valueOf(metadata.get(1).toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Edges.EDGES.SOURCE_ID)).thenReturn(insertResult);
        var record1 = new EdgesRecord(sourceIds.get(0), targetIds.get(0), JSONB.valueOf(metadata.get(0).toString()));
        var record2 = new EdgesRecord(sourceIds.get(1), targetIds.get(1), JSONB.valueOf(metadata.get(1).toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertEdges(sourceIds, targetIds, metadata);
        assertEquals(sourceIds, result);
    }

    @Test
    public void insertMultipleEdgesErrorTest() {
        var sourceIds = Collections.singletonList(1L);
        var targetIds = Arrays.asList(3L, 4L);
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertEdges(sourceIds, targetIds, metadata);
        });
    }

    @Test
    public void insertMultipleEdgesErrorTest2() {
        var sourceIds = Arrays.asList(1L, 2L);
        var targetIds = Arrays.asList(3L, 4L);
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertEdges(sourceIds, targetIds, metadata);
        });
    }
}
