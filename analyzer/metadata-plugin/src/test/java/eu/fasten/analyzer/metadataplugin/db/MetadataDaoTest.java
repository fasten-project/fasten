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

import com.github.t9t.jooq.json.JsonbDSL;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.tables.*;
import eu.fasten.core.data.metadatadb.codegen.tables.records.*;
import org.jooq.*;
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
        var updateSetStart = Mockito.mock(UpdateSetFirstStep.class);
        Mockito.when(context.update(Packages.PACKAGES)).thenReturn(updateSetStart);
        var updateSet = Mockito.mock(UpdateSetMoreStep.class);
        Mockito.when(updateSetStart.set(Packages.PACKAGES.PROJECT_NAME, projectName)).thenReturn(updateSet);
        Mockito.when(updateSet.set(Packages.PACKAGES.REPOSITORY, repository)).thenReturn(updateSet);
        Mockito.when(updateSet.set(Packages.PACKAGES.CREATED_AT, createdAt)).thenReturn(updateSet);
        var updateCond = Mockito.mock(UpdateConditionStep.class);
        Mockito.when(updateSet.where(Packages.PACKAGES.ID.eq(id))).thenReturn(updateCond);
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
        var updateSetStart = Mockito.mock(UpdateSetFirstStep.class);
        Mockito.when(context.update(PackageVersions.PACKAGE_VERSIONS)).thenReturn(updateSetStart);
        var updateSet = Mockito.mock(UpdateSetMoreStep.class);
        Mockito.when(updateSetStart.set(PackageVersions.PACKAGE_VERSIONS.CREATED_AT, createdAt)).thenReturn(updateSet);
        Mockito.when(updateSet.set(PackageVersions.PACKAGE_VERSIONS.METADATA, JSONB.valueOf(metadata.toString()))).thenReturn(updateSet);
        var updateCond = Mockito.mock(UpdateConditionStep.class);
        Mockito.when(updateSet.where(PackageVersions.PACKAGE_VERSIONS.ID.eq(id))).thenReturn(updateCond);
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
        Mockito.when(selectStep.where(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRange))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Dependencies.DEPENDENCIES, Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyId, versionRange)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID)).thenReturn(insertResult);
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
        Mockito.when(selectStep.where(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRange))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(true);
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Dependencies.DEPENDENCIES, Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyId, versionRange)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID)).thenReturn(insertResult);
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
        Mockito.when(selectStep.where(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRange))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID)).thenReturn(Collections.singletonList(packageId));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Dependencies.DEPENDENCIES, Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyId, versionRange)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID)).thenReturn(insertResult);
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
        Mockito.when(selectStep.where(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyIds.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRanges.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.DEPENDENCY_ID.eq(dependencyIds.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Dependencies.DEPENDENCIES.VERSION_RANGE.cast(String[].class).eq(versionRanges.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Dependencies.DEPENDENCIES, Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyIds.get(0), versionRanges.get(0))).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, dependencyIds.get(1), versionRanges.get(1))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID)).thenReturn(insertResult);
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
        var namespace = "namespace";
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Modules.MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Modules.MODULES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACE.eq(namespace))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep4.class);
        Mockito.when(context.insertInto(Modules.MODULES, Modules.MODULES.PACKAGE_VERSION_ID, Modules.MODULES.NAMESPACE,
                Modules.MODULES.CREATED_AT, Modules.MODULES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, namespace, createdAt, JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Modules.MODULES.ID)).thenReturn(insertResult);
        var record = new ModulesRecord(id, packageId, namespace, createdAt, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertModule(packageId, namespace, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertModuleNullTest() {
        long id = 1;
        long packageId = 42;
        var namespace = "namespace";
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Modules.MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Modules.MODULES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACE.eq(namespace))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(true);
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep4.class);
        Mockito.when(context.insertInto(Modules.MODULES, Modules.MODULES.PACKAGE_VERSION_ID, Modules.MODULES.NAMESPACE,
                Modules.MODULES.CREATED_AT, Modules.MODULES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, namespace, null, null)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Modules.MODULES.ID)).thenReturn(insertResult);
        var record = new ModulesRecord(id, packageId, namespace, null, null);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertModule(packageId, namespace, null, null);
        assertEquals(id, result);
    }

    @Test
    public void insertExistingModuleTest() {
        long id = 1;
        long packageId = 42;
        var namespace = "namespace";
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Modules.MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Modules.MODULES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACE.eq(namespace))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(Modules.MODULES.ID)).thenReturn(Collections.singletonList(id));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var updateSetStart = Mockito.mock(UpdateSetFirstStep.class);
        Mockito.when(context.update(Modules.MODULES)).thenReturn(updateSetStart);
        var updateSet = Mockito.mock(UpdateSetMoreStep.class);
        Mockito.when(updateSetStart.set(Modules.MODULES.CREATED_AT, createdAt)).thenReturn(updateSet);
        Mockito.when(updateSet.set(Modules.MODULES.METADATA, JSONB.valueOf(metadata.toString()))).thenReturn(updateSet);
        var updateCond = Mockito.mock(UpdateConditionStep.class);
        Mockito.when(updateSet.where(Modules.MODULES.ID.eq(id))).thenReturn(updateCond);
        long result = metadataDao.insertModule(packageId, namespace, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertMultipleModulesTest() throws IllegalArgumentException {
        var ids = Arrays.asList(1L, 2L);
        long packageId = 42;
        var namespaces = Arrays.asList("namespace1", "namespace2");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Modules.MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Modules.MODULES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACE.eq(namespaces.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Modules.MODULES.NAMESPACE.eq(namespaces.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep4.class);
        Mockito.when(context.insertInto(Modules.MODULES, Modules.MODULES.PACKAGE_VERSION_ID, Modules.MODULES.NAMESPACE,
                Modules.MODULES.CREATED_AT, Modules.MODULES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, namespaces.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()))).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, namespaces.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Modules.MODULES.ID)).thenReturn(insertResult);
        var record1 = new ModulesRecord(ids.get(0), packageId, namespaces.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()));
        var record2 = new ModulesRecord(ids.get(1), packageId, namespaces.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertModules(packageId, namespaces, createdAt, metadata);
        assertEquals(ids, result);
    }

    @Test
    public void insertMultipleModulesErrorTest() {
        long packageId = 42;
        var namespaces = Collections.singletonList("namespace");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertModules(packageId, namespaces, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleModulesErrorTest1() {
        long packageId = 42;
        var namespaces = Arrays.asList("namespace1", "namespace2");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertModules(packageId, namespaces, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleModulesErrorTest2() {
        long packageId = 42;
        var namespaces = Arrays.asList("namespace1", "namespace2");
        var createdAt = Collections.singletonList(new Timestamp(1));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertModules(packageId, namespaces, createdAt, metadata);
        });
    }

    @Test
    public void insertBinaryModuleTest() {
        long id = 1;
        long packageId = 42;
        var name = "name";
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(BinaryModules.BINARY_MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(BinaryModules.BINARY_MODULES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(BinaryModules.BINARY_MODULES.NAME.eq(name))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep4.class);
        Mockito.when(context.insertInto(BinaryModules.BINARY_MODULES, BinaryModules.BINARY_MODULES.PACKAGE_VERSION_ID, BinaryModules.BINARY_MODULES.NAME,
                BinaryModules.BINARY_MODULES.CREATED_AT, BinaryModules.BINARY_MODULES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, name, createdAt, JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(BinaryModules.BINARY_MODULES.ID)).thenReturn(insertResult);
        var record = new BinaryModulesRecord(id, packageId, name, createdAt, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertBinaryModule(packageId, name, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertBinaryModuleNullTest() {
        long id = 1;
        long packageId = 42;
        var name = "name";
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(BinaryModules.BINARY_MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(BinaryModules.BINARY_MODULES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(BinaryModules.BINARY_MODULES.NAME.eq(name))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(true);
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var insertValues = Mockito.mock(InsertValuesStep4.class);
        Mockito.when(context.insertInto(BinaryModules.BINARY_MODULES, BinaryModules.BINARY_MODULES.PACKAGE_VERSION_ID, BinaryModules.BINARY_MODULES.NAME,
                BinaryModules.BINARY_MODULES.CREATED_AT, BinaryModules.BINARY_MODULES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, name, null, null)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(BinaryModules.BINARY_MODULES.ID)).thenReturn(insertResult);
        var record = new BinaryModulesRecord(id, packageId, name, null, null);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertBinaryModule(packageId, name, null, null);
        assertEquals(id, result);
    }

    @Test
    public void insertExistingBinaryModuleTest() {
        long id = 1;
        long packageId = 42;
        var name = "name";
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(BinaryModules.BINARY_MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(BinaryModules.BINARY_MODULES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(BinaryModules.BINARY_MODULES.NAME.eq(name))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(BinaryModules.BINARY_MODULES.ID)).thenReturn(Collections.singletonList(id));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var updateSetStart = Mockito.mock(UpdateSetFirstStep.class);
        Mockito.when(context.update(BinaryModules.BINARY_MODULES)).thenReturn(updateSetStart);
        var updateSet = Mockito.mock(UpdateSetMoreStep.class);
        Mockito.when(updateSetStart.set(BinaryModules.BINARY_MODULES.CREATED_AT, createdAt)).thenReturn(updateSet);
        Mockito.when(updateSet.set(BinaryModules.BINARY_MODULES.METADATA, JSONB.valueOf(metadata.toString()))).thenReturn(updateSet);
        var updateCond = Mockito.mock(UpdateConditionStep.class);
        Mockito.when(updateSet.where(BinaryModules.BINARY_MODULES.ID.eq(id))).thenReturn(updateCond);
        long result = metadataDao.insertBinaryModule(packageId, name, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertMultipleBinaryModulesTest() throws IllegalArgumentException {
        var ids = Arrays.asList(1L, 2L);
        long packageId = 42;
        var name = Arrays.asList("name1", "name2");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(BinaryModules.BINARY_MODULES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(BinaryModules.BINARY_MODULES.PACKAGE_VERSION_ID.eq(packageId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(BinaryModules.BINARY_MODULES.NAME.eq(name.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(BinaryModules.BINARY_MODULES.NAME.eq(name.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep4.class);
        Mockito.when(context.insertInto(BinaryModules.BINARY_MODULES, BinaryModules.BINARY_MODULES.PACKAGE_VERSION_ID, BinaryModules.BINARY_MODULES.NAME,
                BinaryModules.BINARY_MODULES.CREATED_AT, BinaryModules.BINARY_MODULES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, name.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()))).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageId, name.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(BinaryModules.BINARY_MODULES.ID)).thenReturn(insertResult);
        var record1 = new BinaryModulesRecord(ids.get(0), packageId, name.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()));
        var record2 = new BinaryModulesRecord(ids.get(1), packageId, name.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertBinaryModules(packageId, name, createdAt, metadata);
        assertEquals(ids, result);
    }

    @Test
    public void insertMultipleBinaryModulesErrorTest() {
        long packageId = 42;
        var names = Collections.singletonList("name");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertBinaryModules(packageId, names, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleBinaryModulesErrorTest1() {
        long packageId = 42;
        var names = Arrays.asList("name1", "name2");
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertBinaryModules(packageId, names, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleBinaryModulesErrorTest2() {
        long packageId = 42;
        var names = Arrays.asList("name1", "name2");
        var createdAt = Collections.singletonList(new Timestamp(1));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertBinaryModules(packageId, names, createdAt, metadata);
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void insertModuleContentsTest() {
        long moduleId = 1;
        long fileId = 42;
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(ModuleContents.MODULE_CONTENTS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(ModuleContents.MODULE_CONTENTS.MODULE_ID.eq(moduleId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(ModuleContents.MODULE_CONTENTS.FILE_ID.eq(fileId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep2.class);
        Mockito.when(context.insertInto(ModuleContents.MODULE_CONTENTS, ModuleContents.MODULE_CONTENTS.MODULE_ID,
                ModuleContents.MODULE_CONTENTS.FILE_ID)).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleId, fileId)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(ModuleContents.MODULE_CONTENTS.MODULE_ID)).thenReturn(insertResult);
        var record = new ModuleContentsRecord(moduleId, fileId);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertModuleContent(moduleId, fileId);
        assertEquals(moduleId, result);
    }

    @Test
    public void insertExistingModuleContentsTest() {
        long moduleId = 1;
        long fileId = 42;
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(ModuleContents.MODULE_CONTENTS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(ModuleContents.MODULE_CONTENTS.MODULE_ID.eq(moduleId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(ModuleContents.MODULE_CONTENTS.FILE_ID.eq(fileId))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(ModuleContents.MODULE_CONTENTS.MODULE_ID)).thenReturn(Collections.singletonList(moduleId));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        long result = metadataDao.insertModuleContent(moduleId, fileId);
        assertEquals(moduleId, result);
    }

    @Test
    public void insertMultipleModuleContentsTest() throws IllegalArgumentException {
        var moduleIds = List.of(1L, 2L);
        var fileIds = List.of(8L, 42L);
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(ModuleContents.MODULE_CONTENTS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(ModuleContents.MODULE_CONTENTS.MODULE_ID.eq(moduleIds.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(ModuleContents.MODULE_CONTENTS.FILE_ID.eq(fileIds.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectStep.where(ModuleContents.MODULE_CONTENTS.MODULE_ID.eq(moduleIds.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(ModuleContents.MODULE_CONTENTS.FILE_ID.eq(fileIds.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep2.class);
        Mockito.when(context.insertInto(ModuleContents.MODULE_CONTENTS, ModuleContents.MODULE_CONTENTS.MODULE_ID,
                ModuleContents.MODULE_CONTENTS.FILE_ID)).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleIds.get(0), fileIds.get(0))).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleIds.get(1), fileIds.get(1))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(ModuleContents.MODULE_CONTENTS.MODULE_ID)).thenReturn(insertResult);
        var record1 = new ModuleContentsRecord(moduleIds.get(0), fileIds.get(0));
        var record2 = new ModuleContentsRecord(moduleIds.get(1), fileIds.get(1));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertModuleContents(moduleIds, fileIds);
        assertEquals(moduleIds, result);
    }

    @Test
    public void insertMultipleModuleContentsErrorTest() {
        var moduleIds = List.of(1L);
        var fileIds = List.of(8L, 42L);
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertModuleContents(moduleIds, fileIds);
        });
    }

    @Test
    public void insertMultipleModuleContentsErrorTest1() {
        var moduleIds = List.of(1L, 2L);
        var fileIds = List.of(8L);
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertModuleContents(moduleIds, fileIds);
        });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////


    @Test
    public void insertBinaryModuleContentsTest() {
        long binaryModuleId = 1;
        long fileId = 42;
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(BinaryModuleContents.BINARY_MODULE_CONTENTS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID.eq(binaryModuleId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(BinaryModuleContents.BINARY_MODULE_CONTENTS.FILE_ID.eq(fileId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep2.class);
        Mockito.when(context.insertInto(BinaryModuleContents.BINARY_MODULE_CONTENTS, BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID,
                BinaryModuleContents.BINARY_MODULE_CONTENTS.FILE_ID)).thenReturn(insertValues);
        Mockito.when(insertValues.values(binaryModuleId, fileId)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID)).thenReturn(insertResult);
        var record = new BinaryModuleContentsRecord(binaryModuleId, fileId);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertBinaryModuleContent(binaryModuleId, fileId);
        assertEquals(binaryModuleId, result);
    }

    @Test
    public void insertExistingBinaryModuleContentsTest() {
        long binaryModuleId = 1;
        long fileId = 42;
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(BinaryModuleContents.BINARY_MODULE_CONTENTS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID.eq(binaryModuleId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(BinaryModuleContents.BINARY_MODULE_CONTENTS.FILE_ID.eq(fileId))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID)).thenReturn(Collections.singletonList(binaryModuleId));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        long result = metadataDao.insertBinaryModuleContent(binaryModuleId, fileId);
        assertEquals(binaryModuleId, result);
    }

    @Test
    public void insertMultipleBinaryModuleContentsTest() throws IllegalArgumentException {
        var binaryModuleIds = List.of(1L, 2L);
        var fileIds = List.of(8L, 42L);
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(BinaryModuleContents.BINARY_MODULE_CONTENTS)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID.eq(binaryModuleIds.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(BinaryModuleContents.BINARY_MODULE_CONTENTS.FILE_ID.eq(fileIds.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectStep.where(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID.eq(binaryModuleIds.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(BinaryModuleContents.BINARY_MODULE_CONTENTS.FILE_ID.eq(fileIds.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep2.class);
        Mockito.when(context.insertInto(BinaryModuleContents.BINARY_MODULE_CONTENTS, BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID,
                BinaryModuleContents.BINARY_MODULE_CONTENTS.FILE_ID)).thenReturn(insertValues);
        Mockito.when(insertValues.values(binaryModuleIds.get(0), fileIds.get(0))).thenReturn(insertValues);
        Mockito.when(insertValues.values(binaryModuleIds.get(1), fileIds.get(1))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID)).thenReturn(insertResult);
        var record1 = new BinaryModuleContentsRecord(binaryModuleIds.get(0), fileIds.get(0));
        var record2 = new BinaryModuleContentsRecord(binaryModuleIds.get(1), fileIds.get(1));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertBinaryModuleContents(binaryModuleIds, fileIds);
        assertEquals(binaryModuleIds, result);
    }

    @Test
    public void insertMultipleBinaryModuleContentsErrorTest() {
        var binaryModuleIds = List.of(1L);
        var fileIds = List.of(8L, 42L);
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertBinaryModuleContents(binaryModuleIds, fileIds);
        });
    }

    @Test
    public void insertMultipleBinaryModuleContentsErrorTest1() {
        var binaryModuleIds = List.of(1L, 2L);
        var fileIds = List.of(8L);
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertBinaryModuleContents(binaryModuleIds, fileIds);
        });
    }

    @Test
    public void insertFileTest() {
        var id = 1L;
        var packageVersionId = 42L;
        var path = "path/to/file";
        var checksum = new byte[]{1, 2, 3, 4, 5};
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Files.FILES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Files.FILES.PACKAGE_VERSION_ID.eq(packageVersionId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Files.FILES.PATH.eq(path))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Files.FILES, Files.FILES.PACKAGE_VERSION_ID, Files.FILES.PATH, Files.FILES.CHECKSUM,
                Files.FILES.CREATED_AT, Files.FILES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageVersionId, path, checksum, createdAt,
                JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Files.FILES.ID)).thenReturn(insertResult);
        var record = new FilesRecord(id, packageVersionId, path, checksum, createdAt,
                JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        var result = metadataDao.insertFile(packageVersionId, path, checksum, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertNullFileTest() {
        var id = 1L;
        var packageVersionId = 42L;
        var path = "path/to/file";
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Files.FILES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Files.FILES.PACKAGE_VERSION_ID.eq(packageVersionId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Files.FILES.PATH.eq(path))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Files.FILES, Files.FILES.PACKAGE_VERSION_ID, Files.FILES.PATH, Files.FILES.CHECKSUM,
                Files.FILES.CREATED_AT, Files.FILES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageVersionId, path, null, null, null)).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Files.FILES.ID)).thenReturn(insertResult);
        var record = new FilesRecord(id, packageVersionId, path, null, null, null);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        var result = metadataDao.insertFile(packageVersionId, path, null, null, null);
        assertEquals(id, result);
    }

    @Test
    public void insertExistingFileTest() {
        var id = 1L;
        var packageVersionId = 42L;
        var path = "path/to/file";
        var checksum = new byte[]{1, 2, 3, 4, 5};
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Files.FILES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Files.FILES.PACKAGE_VERSION_ID.eq(packageVersionId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Files.FILES.PATH.eq(path))).thenReturn(selectCondStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isEmpty()).thenReturn(false);
        Mockito.when(resultSet.getValues(Files.FILES.ID)).thenReturn(Collections.singletonList(id));
        Mockito.when(selectCondStep.fetch()).thenReturn(resultSet);
        var updateSetStart = Mockito.mock(UpdateSetFirstStep.class);
        Mockito.when(context.update(Files.FILES)).thenReturn(updateSetStart);
        var updateSet = Mockito.mock(UpdateSetMoreStep.class);
        Mockito.when(updateSetStart.set(Files.FILES.CHECKSUM, checksum)).thenReturn(updateSet);
        Mockito.when(updateSet.set(Files.FILES.CREATED_AT, createdAt)).thenReturn(updateSet);
        Mockito.when(updateSet.set(Files.FILES.METADATA, JSONB.valueOf(metadata.toString()))).thenReturn(updateSet);
        var updateCond = Mockito.mock(UpdateConditionStep.class);
        Mockito.when(updateSet.where(Files.FILES.ID.eq(id))).thenReturn(updateCond);
        var result = metadataDao.insertFile(packageVersionId, path, checksum, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertMultipleFilesTest() {
        var ids = Arrays.asList(1L, 2L);
        var packageVersionId = 42L;
        var paths = Arrays.asList("path/to/file", "path/to/another/file");
        var checksums = Arrays.asList(new byte[]{1, 2, 3, 4, 5}, new byte[]{6, 7, 8, 9});
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        var selectStep = Mockito.mock(SelectWhereStep.class);
        Mockito.when(context.selectFrom(Files.FILES)).thenReturn(selectStep);
        var selectCondStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(selectStep.where(Files.FILES.PACKAGE_VERSION_ID.eq(packageVersionId))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Files.FILES.PATH.eq(paths.get(0)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.and(Files.FILES.PATH.eq(paths.get(1)))).thenReturn(selectCondStep);
        Mockito.when(selectCondStep.fetch()).thenReturn(null);
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Files.FILES, Files.FILES.PACKAGE_VERSION_ID, Files.FILES.PATH, Files.FILES.CHECKSUM,
                Files.FILES.CREATED_AT, Files.FILES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageVersionId, paths.get(0), checksums.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()))).thenReturn(insertValues);
        Mockito.when(insertValues.values(packageVersionId, paths.get(1), checksums.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()))).thenReturn(insertValues);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertValues.returning(Files.FILES.ID)).thenReturn(insertResult);
        var record1 = new FilesRecord(ids.get(0), packageVersionId, paths.get(0), checksums.get(1), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()));
        var record2 = new FilesRecord(ids.get(1), packageVersionId, paths.get(1), checksums.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertFiles(packageVersionId, paths, checksums, createdAt, metadata);
        assertEquals(ids, result);
    }

    @Test
    public void insertMultipleFilesErrorTest() {
        var packageVersionId = 42L;
        var paths = Collections.singletonList("path/to/file");
        var checksums = Arrays.asList(new byte[]{1, 2, 3, 4, 5}, new byte[]{6, 7, 8, 9});
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertFiles(packageVersionId, paths, checksums, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleFilesErrorTest1() {
        var packageVersionId = 42L;
        var paths = Arrays.asList("path/to/file", "path/to/another/file");
        var checksums = Collections.singletonList(new byte[]{1, 2, 3, 4, 5});
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertFiles(packageVersionId, paths, checksums, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleFilesErrorTest2() {
        var packageVersionId = 42L;
        var paths = Arrays.asList("path/to/file", "path/to/another/file");
        var checksums = Arrays.asList(new byte[]{1, 2, 3, 4, 5}, new byte[]{6, 7, 8, 9});
        var createdAt = Collections.singletonList(new Timestamp(1));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertFiles(packageVersionId, paths, checksums, createdAt, metadata);
        });
    }

    @Test
    public void insertMultipleFilesErrorTest3() {
        var packageVersionId = 42L;
        var paths = Arrays.asList("path/to/file", "path/to/another/file");
        var checksums = Arrays.asList(new byte[]{1, 2, 3, 4, 5}, new byte[]{6, 7, 8, 9});
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Collections.singletonList(new JSONObject("{\"foo\":\"bar\"}"));
        assertThrows(IllegalArgumentException.class, () -> {
            metadataDao.insertFiles(packageVersionId, paths, checksums, createdAt, metadata);
        });
    }

    @Test
    public void insertCallableTest() throws IllegalArgumentException {
        var id = 1L;
        long moduleId = 42;
        var fastenUri = "URI";
        boolean isInternalCall = true;
        var createdAt = new Timestamp(1);
        var metadata = new JSONObject("{\"foo\":\"bar\"}");
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Callables.CALLABLES, Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleId, fastenUri, isInternalCall, createdAt,
                JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        var insertOnConflict = Mockito.mock(InsertOnConflictDoUpdateStep.class);
        Mockito.when(insertValues.onConflictOnConstraint(Keys.UNIQUE_URI_CALL)).thenReturn(insertOnConflict);
        var insertDuplicateSet = Mockito.mock(InsertOnDuplicateSetStep.class);
        Mockito.when(insertOnConflict.doUpdate()).thenReturn(insertDuplicateSet);
        var insertDuplicateSetMore = Mockito.mock(InsertOnDuplicateSetMoreStep.class);
        Mockito.when(insertDuplicateSet.set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)).thenReturn(insertDuplicateSetMore);
        Mockito.when(insertDuplicateSetMore.set(Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.as("excluded").CREATED_AT)).thenReturn(insertDuplicateSetMore);
        Mockito.when(insertDuplicateSetMore.set(Callables.CALLABLES.METADATA, JsonbDSL.concat(Callables.CALLABLES.METADATA,
                Callables.CALLABLES.as("excluded").METADATA))).thenReturn(insertDuplicateSetMore);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertDuplicateSetMore.returning(Callables.CALLABLES.ID)).thenReturn(insertResult);
        var record = new CallablesRecord(id, moduleId, fastenUri, isInternalCall, createdAt,
                JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        var result = metadataDao.insertCallable(moduleId, fastenUri, isInternalCall, createdAt, metadata);
        assertEquals(id, result);
    }

    @Test
    public void insertCallableNullTest() throws IllegalArgumentException {
        var id = 1L;
        long moduleId = 42;
        var fastenUri = "URI";
        var isInternalCall = false;
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Callables.CALLABLES, Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleId, fastenUri, isInternalCall, null, null)).thenReturn(insertValues);
        var insertOnConflict = Mockito.mock(InsertOnConflictDoUpdateStep.class);
        Mockito.when(insertValues.onConflictOnConstraint(Keys.UNIQUE_URI_CALL)).thenReturn(insertOnConflict);
        var insertDuplicateSet = Mockito.mock(InsertOnDuplicateSetStep.class);
        Mockito.when(insertOnConflict.doUpdate()).thenReturn(insertDuplicateSet);
        var insertDuplicateSetMore = Mockito.mock(InsertOnDuplicateSetMoreStep.class);
        Mockito.when(insertDuplicateSet.set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)).thenReturn(insertDuplicateSetMore);
        Mockito.when(insertDuplicateSetMore.set(Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.as("excluded").CREATED_AT)).thenReturn(insertDuplicateSetMore);
        Mockito.when(insertDuplicateSetMore.set(Callables.CALLABLES.METADATA, JsonbDSL.concat(Callables.CALLABLES.METADATA,
                Callables.CALLABLES.as("excluded").METADATA))).thenReturn(insertDuplicateSetMore);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertDuplicateSetMore.returning(Callables.CALLABLES.ID)).thenReturn(insertResult);
        var record = new CallablesRecord(id, moduleId, fastenUri, isInternalCall, null, null);
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        var result = metadataDao.insertCallable(moduleId, fastenUri, isInternalCall, null, null);
        assertEquals(id, result);
    }

    @Test
    public void insertMultipleCallablesTest() throws IllegalArgumentException {
        var ids = Arrays.asList(1L, 2L);
        long moduleId = 42;
        var fastenUris = Arrays.asList("URI1", "URI2");
        var areInternalCalls = Arrays.asList(true, false);
        var createdAt = Arrays.asList(new Timestamp(1), new Timestamp(2));
        var metadata = Arrays.asList(new JSONObject("{\"foo\":\"bar\"}"), new JSONObject("{\"hello\":\"world\"}"));
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Callables.CALLABLES, Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleId, fastenUris.get(0), areInternalCalls.get(0), createdAt.get(0),
                JSONB.valueOf(metadata.get(0).toString()))).thenReturn(insertValues);
        Mockito.when(insertValues.values(moduleId, fastenUris.get(1), areInternalCalls.get(1), createdAt.get(1),
                JSONB.valueOf(metadata.get(1).toString()))).thenReturn(insertValues);
        var insertOnConflict = Mockito.mock(InsertOnConflictDoUpdateStep.class);
        Mockito.when(insertValues.onConflictOnConstraint(Keys.UNIQUE_URI_CALL)).thenReturn(insertOnConflict);
        var insertDuplicateSet = Mockito.mock(InsertOnDuplicateSetStep.class);
        Mockito.when(insertOnConflict.doUpdate()).thenReturn(insertDuplicateSet);
        var insertDuplicateSetMore = Mockito.mock(InsertOnDuplicateSetMoreStep.class);
        Mockito.when(insertDuplicateSet.set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)).thenReturn(insertDuplicateSetMore);
        Mockito.when(insertDuplicateSetMore.set(Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.as("excluded").CREATED_AT)).thenReturn(insertDuplicateSetMore);
        Mockito.when(insertDuplicateSetMore.set(Callables.CALLABLES.METADATA, JsonbDSL.concat(Callables.CALLABLES.METADATA,
                Callables.CALLABLES.as("excluded").METADATA))).thenReturn(insertDuplicateSetMore);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertDuplicateSetMore.returning(Callables.CALLABLES.ID)).thenReturn(insertResult);
        var record1 = new CallablesRecord(ids.get(0), moduleId, fastenUris.get(0),
                areInternalCalls.get(0), createdAt.get(0), JSONB.valueOf(metadata.get(0).toString()));
        var record2 = new CallablesRecord(ids.get(1), moduleId, fastenUris.get(1),
                areInternalCalls.get(1), createdAt.get(1), JSONB.valueOf(metadata.get(1).toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record1, record2);
        var result = metadataDao.insertCallables(moduleId, fastenUris, areInternalCalls, createdAt, metadata);
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
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Edges.EDGES, Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID,
                Edges.EDGES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(sourceId, targetId, JSONB.valueOf(metadata.toString()))).thenReturn(insertValues);
        var insertConflict = Mockito.mock(InsertOnConflictDoUpdateStep.class);
        Mockito.when(insertValues.onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET)).thenReturn(insertConflict);
        var insertDuplicate = Mockito.mock(InsertOnDuplicateSetStep.class);
        Mockito.when(insertConflict.doUpdate()).thenReturn(insertDuplicate);
        var insertDuplicateMore = Mockito.mock(InsertOnDuplicateSetMoreStep.class);
        Mockito.when(insertDuplicate.set(Mockito.eq(Edges.EDGES.METADATA), Mockito.any(Field.class))).thenReturn(insertDuplicateMore);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertDuplicateMore.returning(Edges.EDGES.SOURCE_ID)).thenReturn(insertResult);
        var record = new EdgesRecord(sourceId, targetId, JSONB.valueOf(metadata.toString()));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
        long result = metadataDao.insertEdge(sourceId, targetId, metadata);
        assertEquals(sourceId, result);
    }

    @Test
    public void insertNullEdgeTest() {
        long sourceId = 1;
        long targetId = 2;
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Edges.EDGES, Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID,
                Edges.EDGES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(sourceId, targetId, JSONB.valueOf("{}"))).thenReturn(insertValues);
        var insertConflict = Mockito.mock(InsertOnConflictDoUpdateStep.class);
        Mockito.when(insertValues.onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET)).thenReturn(insertConflict);
        var insertDuplicate = Mockito.mock(InsertOnDuplicateSetStep.class);
        Mockito.when(insertConflict.doUpdate()).thenReturn(insertDuplicate);
        var insertDuplicateMore = Mockito.mock(InsertOnDuplicateSetMoreStep.class);
        Mockito.when(insertDuplicate.set(Mockito.eq(Edges.EDGES.METADATA), Mockito.any(Field.class))).thenReturn(insertDuplicateMore);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertDuplicateMore.returning(Edges.EDGES.SOURCE_ID)).thenReturn(insertResult);
        var record = new EdgesRecord(sourceId, targetId, JSONB.valueOf("{}"));
        Mockito.when(insertResult.fetchOne()).thenReturn(record);
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
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Edges.EDGES, Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID,
                Edges.EDGES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(sourceIds.get(0), targetIds.get(0), JSONB.valueOf(metadata.get(0).toString()))).thenReturn(insertValues);
        Mockito.when(insertValues.values(sourceIds.get(1), targetIds.get(1), JSONB.valueOf(metadata.get(1).toString()))).thenReturn(insertValues);
        var insertConflict = Mockito.mock(InsertOnConflictDoUpdateStep.class);
        Mockito.when(insertValues.onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET)).thenReturn(insertConflict);
        var insertDuplicate = Mockito.mock(InsertOnDuplicateSetStep.class);
        Mockito.when(insertConflict.doUpdate()).thenReturn(insertDuplicate);
        var insertDuplicateMore = Mockito.mock(InsertOnDuplicateSetMoreStep.class);
        Mockito.when(insertDuplicate.set(Mockito.eq(Edges.EDGES.METADATA), Mockito.any(Field.class))).thenReturn(insertDuplicateMore);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertDuplicateMore.returning(Edges.EDGES.SOURCE_ID)).thenReturn(insertResult);
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

    @Test
    public void updatePackageTest() {
        long packageId = 1;
        String projectName = "Project1";
        String repository = "Repo1";
        Timestamp timestamp = new Timestamp(123);
        var updateSetStart = Mockito.mock(UpdateSetFirstStep.class);
        Mockito.when(context.update(Packages.PACKAGES)).thenReturn(updateSetStart);
        var updateSet = Mockito.mock(UpdateSetMoreStep.class);
        Mockito.when(updateSetStart.set(Packages.PACKAGES.PROJECT_NAME, projectName)).thenReturn(updateSet);
        Mockito.when(updateSet.set(Packages.PACKAGES.REPOSITORY, repository)).thenReturn(updateSet);
        Mockito.when(updateSet.set(Packages.PACKAGES.CREATED_AT, timestamp)).thenReturn(updateSet);
        var updateCond = Mockito.mock(UpdateConditionStep.class);
        Mockito.when(updateSet.where(Packages.PACKAGES.ID.eq(packageId))).thenReturn(updateCond);
        this.metadataDao.updatePackage(packageId, projectName, repository, timestamp);
        Mockito.verify(updateCond).execute();
    }

    @Test
    public void updatePackageVersionTest() {
        long packageVersionId = 1;
        Timestamp timestamp = new Timestamp(123);
        JSONB metadata = JSONB.valueOf("{\"foo\":\"bar\"}");
        var updateSetStart = Mockito.mock(UpdateSetFirstStep.class);
        Mockito.when(context.update(PackageVersions.PACKAGE_VERSIONS)).thenReturn(updateSetStart);
        var updateSet = Mockito.mock(UpdateSetMoreStep.class);
        Mockito.when(updateSetStart.set(PackageVersions.PACKAGE_VERSIONS.CREATED_AT, timestamp)).thenReturn(updateSet);
        Mockito.when(updateSet.set(PackageVersions.PACKAGE_VERSIONS.METADATA, metadata)).thenReturn(updateSet);
        var updateCond = Mockito.mock(UpdateConditionStep.class);
        Mockito.when(updateSet.where(PackageVersions.PACKAGE_VERSIONS.ID.eq(packageVersionId))).thenReturn(updateCond);
        this.metadataDao.updatePackageVersion(packageVersionId, timestamp, metadata);
        Mockito.verify(updateCond).execute();
    }

    @Test
    public void updateModuleTest() {
        long moduleId = 1;
        Timestamp timestamp = new Timestamp(123);
        JSONB metadata = JSONB.valueOf("{\"foo\":\"bar\"}");
        var updateSetStart = Mockito.mock(UpdateSetFirstStep.class);
        Mockito.when(context.update(Modules.MODULES)).thenReturn(updateSetStart);
        var updateSet = Mockito.mock(UpdateSetMoreStep.class);
        Mockito.when(updateSetStart.set(Modules.MODULES.CREATED_AT, timestamp)).thenReturn(updateSet);
        Mockito.when(updateSet.set(Modules.MODULES.METADATA, metadata)).thenReturn(updateSet);
        var updateCond = Mockito.mock(UpdateConditionStep.class);
        Mockito.when(updateSet.where(Modules.MODULES.ID.eq(moduleId))).thenReturn(updateCond);
        this.metadataDao.updateModule(moduleId, timestamp, metadata);
        Mockito.verify(updateCond).execute();
    }

    @Test
    public void batchInsertEdgesTest() {
        var r1 = new EdgesRecord(1L, 2L, JSONB.valueOf("{}"));
        var r2 = new EdgesRecord(3L, 4L, JSONB.valueOf("{\"foo\": \"bar\"}"));
        var insertValues = Mockito.mock(InsertValuesStep3.class);
        Mockito.when(context.insertInto(Edges.EDGES, Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID,
                Edges.EDGES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values((Long) null, (Long) null, (JSONB) null)).thenReturn(insertValues);
        var insertConflict = Mockito.mock(InsertOnConflictDoUpdateStep.class);
        Mockito.when(insertValues.onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET)).thenReturn(insertConflict);
        var insertDuplicate = Mockito.mock(InsertOnDuplicateSetStep.class);
        Mockito.when(insertConflict.doUpdate()).thenReturn(insertDuplicate);
        var insertDuplicateMore = Mockito.mock(InsertOnDuplicateSetMoreStep.class);
        Mockito.when(insertDuplicate.set(Mockito.eq(Edges.EDGES.METADATA), Mockito.any(Field.class))).thenReturn(insertDuplicateMore);
        var batchBind = Mockito.mock(BatchBindStep.class);
        Mockito.when(context.batch(insertDuplicateMore)).thenReturn(batchBind);
        Mockito.when(batchBind.bind(r1.getSourceId(), r1.getTargetId(), r1.getMetadata())).thenReturn(batchBind);
        Mockito.when(batchBind.bind(r2.getSourceId(), r2.getTargetId(), r2.getMetadata())).thenReturn(batchBind);
        metadataDao.batchInsertEdges(List.of(r1, r2));
        Mockito.verify(batchBind).bind(r1.getSourceId(), r1.getTargetId(), r1.getMetadata());
        Mockito.verify(batchBind).bind(r2.getSourceId(), r2.getTargetId(), r2.getMetadata());
        Mockito.verify(batchBind).execute();
    }

    @Test
    public void batchInsertCallablesTest() throws IllegalArgumentException {
        var record1 = new CallablesRecord(1L, 42L, "URI1", true, new Timestamp(1), JSONB.valueOf("{\"foo\":\"bar\"}"));
        var record2 = new CallablesRecord(2L, 42L, "URI2", false, new Timestamp(2), JSONB.valueOf("{\"hello\":\"world\"}"));
        var insertValues = Mockito.mock(InsertValuesStep5.class);
        Mockito.when(context.insertInto(Callables.CALLABLES, Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA)).thenReturn(insertValues);
        Mockito.when(insertValues.values(record1.getModuleId(), record1.getFastenUri(), record1.getIsInternalCall(),
                record1.getCreatedAt(), record1.getMetadata())).thenReturn(insertValues);
        Mockito.when(insertValues.values(record2.getModuleId(), record2.getFastenUri(), record2.getIsInternalCall(),
                record2.getCreatedAt(), record2.getMetadata())).thenReturn(insertValues);
        var insertOnConflict = Mockito.mock(InsertOnConflictDoUpdateStep.class);
        Mockito.when(insertValues.onConflictOnConstraint(Keys.UNIQUE_URI_CALL)).thenReturn(insertOnConflict);
        var insertDuplicateSet = Mockito.mock(InsertOnDuplicateSetStep.class);
        Mockito.when(insertOnConflict.doUpdate()).thenReturn(insertDuplicateSet);
        var insertDuplicateSetMore = Mockito.mock(InsertOnDuplicateSetMoreStep.class);
        Mockito.when(insertDuplicateSet.set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)).thenReturn(insertDuplicateSetMore);
        Mockito.when(insertDuplicateSetMore.set(Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.as("excluded").CREATED_AT)).thenReturn(insertDuplicateSetMore);
        Mockito.when(insertDuplicateSetMore.set(Callables.CALLABLES.METADATA, JsonbDSL.concat(Callables.CALLABLES.METADATA,
                Callables.CALLABLES.as("excluded").METADATA))).thenReturn(insertDuplicateSetMore);
        var insertResult = Mockito.mock(InsertResultStep.class);
        Mockito.when(insertDuplicateSetMore.returning(Callables.CALLABLES.ID)).thenReturn(insertResult);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.getValues(Callables.CALLABLES.ID)).thenReturn(List.of(record1.getId(), record2.getId()));
        Mockito.when(insertResult.fetch()).thenReturn(resultSet);
        var result = metadataDao.batchInsertCallables(List.of(record1, record2));
        assertEquals(List.of(record1.getId(), record2.getId()), result);
    }
}
