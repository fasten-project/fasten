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

package eu.fasten.core.data.metadatadb;

import com.github.t9t.jooq.json.JsonbDSL;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.tables.BinaryModuleContents;
import eu.fasten.core.data.metadatadb.codegen.tables.BinaryModules;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.Edges;
import eu.fasten.core.data.metadatadb.codegen.tables.Files;
import eu.fasten.core.data.metadatadb.codegen.tables.ModuleContents;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.data.metadatadb.codegen.tables.records.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.jooq.*;
import org.jooq.impl.TableImpl;
import org.json.JSONArray;
import org.json.JSONObject;

import static org.jooq.impl.DSL.field;

public class MetadataDao {

    private DSLContext context;

    public MetadataDao(DSLContext context) {
        this.context = context;
    }

    public DSLContext getContext() {
        return this.context;
    }

    public void setContext(DSLContext context) {
        this.context = context;
    }

    /**
     * Inserts a record in 'packages' table in the database.
     *
     * @param packageName Name of the package
     * @param forge       Forge of the package
     * @param projectName Project name to which package belongs
     * @param repository  Repository to which package belongs
     * @param createdAt   Timestamp when package was created
     * @return ID of the new record
     */
    public long insertPackage(String packageName, String forge, String projectName,
                              String repository, Timestamp createdAt) {
        var resultRecord = context.insertInto(Packages.PACKAGES,
                Packages.PACKAGES.PACKAGE_NAME, Packages.PACKAGES.FORGE,
                Packages.PACKAGES.PROJECT_NAME, Packages.PACKAGES.REPOSITORY,
                Packages.PACKAGES.CREATED_AT)
                .values(packageName, forge, projectName, repository, createdAt)
                .onConflictOnConstraint(Keys.UNIQUE_PACKAGE_FORGE).doUpdate()
                .set(Packages.PACKAGES.PROJECT_NAME, Packages.PACKAGES.as("excluded").PROJECT_NAME)
                .set(Packages.PACKAGES.REPOSITORY, Packages.PACKAGES.as("excluded").REPOSITORY)
                .set(Packages.PACKAGES.CREATED_AT, Packages.PACKAGES.as("excluded").CREATED_AT)
                .returning(Packages.PACKAGES.ID).fetchOne();
        return resultRecord.getValue(Packages.PACKAGES.ID);
    }

    /**
     * Inserts multiple records in the 'packages' table in the database.
     *
     * @param packageNames List of names of the packages
     * @param forges       List of forges of the packages
     * @param projectNames List of names of the projects
     * @param repositories List of repositories
     * @param createdAt    List of timestamps
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertPackages(List<String> packageNames,
                                     List<String> forges, List<String> projectNames,
                                     List<String> repositories, List<Timestamp> createdAt)
            throws IllegalArgumentException {
        if (packageNames.size() != forges.size() || forges.size() != projectNames.size()
                || projectNames.size() != repositories.size()
                || repositories.size() != createdAt.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = packageNames.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertPackage(packageNames.get(i), forges.get(i), projectNames.get(i),
                    repositories.get(i), createdAt.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in 'package_versions' table in the database.
     *
     * @param packageId   ID of the package (references 'packages.id')
     * @param cgGenerator Tool used to generate this callgraph
     * @param version     Version of the package
     * @param createdAt   Timestamp when the package version was created
     * @param metadata    Metadata of the package version
     * @return ID of the new record
     */
    public long insertPackageVersion(long packageId, String cgGenerator, String version,
                                     Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(PackageVersions.PACKAGE_VERSIONS,
                PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID,
                PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR,
                PackageVersions.PACKAGE_VERSIONS.VERSION,
                PackageVersions.PACKAGE_VERSIONS.CREATED_AT,
                PackageVersions.PACKAGE_VERSIONS.METADATA)
                .values(packageId, cgGenerator, version, createdAt, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_PACKAGE_VERSION_GENERATOR).doUpdate()
                .set(PackageVersions.PACKAGE_VERSIONS.CREATED_AT,
                        PackageVersions.PACKAGE_VERSIONS.as("excluded").CREATED_AT)
                .set(PackageVersions.PACKAGE_VERSIONS.METADATA,
                        JsonbDSL.concat(PackageVersions.PACKAGE_VERSIONS.METADATA,
                                PackageVersions.PACKAGE_VERSIONS.as("excluded").METADATA))
                .returning(PackageVersions.PACKAGE_VERSIONS.ID).fetchOne();
        return resultRecord.getValue(PackageVersions.PACKAGE_VERSIONS.ID);
    }

    /**
     * Inserts multiple records in the 'package_versions' table in the database.
     *
     * @param packageId    ID of the common package (references 'packages.id')
     * @param cgGenerators List of code generators
     * @param versions     List of versions
     * @param createdAt    List of timestamps
     * @param metadata     List of metadata objects
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertPackageVersions(long packageId, List<String> cgGenerators,
                                            List<String> versions, List<Timestamp> createdAt,
                                            List<JSONObject> metadata)
            throws IllegalArgumentException {
        if (cgGenerators.size() != versions.size() || versions.size() != createdAt.size()
                || createdAt.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = cgGenerators.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertPackageVersion(packageId, cgGenerators.get(i), versions.get(i),
                    createdAt.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in the 'dependencies' table in the database.
     *
     * @param packageVersionId ID of the package version (references 'package_versions.id')
     * @param dependencyId     ID of the dependency package (references 'packages.id')
     * @param versionRanges    Ranges of valid versions
     * @param metadata         Metadata of the dependency
     * @return ID of the package version (packageVersionId)
     */
    public long insertDependency(long packageVersionId, long dependencyId, String[] versionRanges,
                                 JSONObject metadata) {
        var resultRecord = context.insertInto(Dependencies.DEPENDENCIES,
                Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID,
                Dependencies.DEPENDENCIES.VERSION_RANGE,
                Dependencies.DEPENDENCIES.METADATA)
                .values(packageVersionId, dependencyId, versionRanges,
                        JSONB.valueOf(metadata.toString()))
                .onConflictOnConstraint(Keys.UNIQUE_VERSION_DEPENDENCY_RANGE).doUpdate()
                .set(Dependencies.DEPENDENCIES.METADATA,
                        JsonbDSL.concat(Dependencies.DEPENDENCIES.METADATA,
                                Dependencies.DEPENDENCIES.as("excluded").METADATA))
                .returning(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID).fetchOne();
        return resultRecord.getValue(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID);
    }

    /**
     * Inserts multiple 'dependencies' int the database for certain package.
     *
     * @param packageVersionId ID of the package version
     * @param dependenciesIds  List of IDs of dependencies
     * @param versionRanges    List of version ranges
     * @param metadata         List of metadata
     * @return ID of the package (packageId)
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public long insertDependencies(long packageVersionId, List<Long> dependenciesIds,
                                   List<String[]> versionRanges, List<JSONObject> metadata)
            throws IllegalArgumentException {
        if (dependenciesIds.size() != versionRanges.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = dependenciesIds.size();
        for (int i = 0; i < length; i++) {
            insertDependency(packageVersionId, dependenciesIds.get(i), versionRanges.get(i),
                    metadata.get(i));
        }
        return packageVersionId;
    }

    /**
     * Inserts a record in 'modules' table in the database.
     *
     * @param packageVersionId ID of the package version where the module belongs
     *                         (references 'package_versions.id')
     * @param namespace        Namespace of the module
     * @param createdAt        Timestamp when the module was created
     * @param metadata         Metadata of the module
     * @return ID of the new record
     */
    public long insertModule(long packageVersionId, String namespace,
                             Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Modules.MODULES,
                Modules.MODULES.PACKAGE_VERSION_ID, Modules.MODULES.NAMESPACE,
                Modules.MODULES.CREATED_AT, Modules.MODULES.METADATA)
                .values(packageVersionId, namespace, createdAt, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_VERSION_NAMESPACE).doUpdate()
                .set(Modules.MODULES.CREATED_AT, Modules.MODULES.as("excluded").CREATED_AT)
                .set(Modules.MODULES.METADATA, JsonbDSL.concat(Modules.MODULES.METADATA,
                        Modules.MODULES.as("excluded").METADATA))
                .returning(Modules.MODULES.ID).fetchOne();
        return resultRecord.getValue(Modules.MODULES.ID);
    }

    /**
     * Inserts multiple records in the 'modules' table in the database.
     *
     * @param packageVersionId ID of the common package version
     * @param namespacesList   List of namespaces
     * @param createdAt        List of timestamps
     * @param metadata         List of metadata objects
     * @return List of IDs of new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertModules(long packageVersionId, List<String> namespacesList,
                                    List<Timestamp> createdAt, List<JSONObject> metadata)
            throws IllegalArgumentException {
        if (namespacesList.size() != createdAt.size() || createdAt.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = namespacesList.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertModule(packageVersionId, namespacesList.get(i),
                    createdAt.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in 'binary_modules' table in the database.
     *
     * @param packageVersionId ID of the package version where the binary module belongs
     *                         (references 'package_versions.id')
     * @param name             Name of the binary module
     * @param createdAt        Timestamp when the binary module was created
     * @param metadata         Metadata of the binary module
     * @return ID of the new record
     */
    public long insertBinaryModule(long packageVersionId, String name,
                                   Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(BinaryModules.BINARY_MODULES,
                BinaryModules.BINARY_MODULES.PACKAGE_VERSION_ID, BinaryModules.BINARY_MODULES.NAME,
                BinaryModules.BINARY_MODULES.CREATED_AT, BinaryModules.BINARY_MODULES.METADATA)
                .values(packageVersionId, name, createdAt, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_VERSION_NAME).doUpdate()
                .set(BinaryModules.BINARY_MODULES.CREATED_AT,
                        BinaryModules.BINARY_MODULES.as("excluded").CREATED_AT)
                .set(BinaryModules.BINARY_MODULES.METADATA,
                        JsonbDSL.concat(BinaryModules.BINARY_MODULES.METADATA,
                                BinaryModules.BINARY_MODULES.as("excluded").METADATA))
                .returning(BinaryModules.BINARY_MODULES.ID).fetchOne();
        return resultRecord.getValue(BinaryModules.BINARY_MODULES.ID);
    }

    /**
     * Inserts multiple records in the 'binary_modules' table in the database.
     *
     * @param packageVersionId ID of the common package version
     * @param namesList        List of names
     * @param createdAt        List of timestamps
     * @param metadata         List of metadata objects
     * @return List of IDs of new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertBinaryModules(long packageVersionId, List<String> namesList,
                                          List<Timestamp> createdAt, List<JSONObject> metadata)
            throws IllegalArgumentException {
        if (namesList.size() != createdAt.size() || createdAt.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = namesList.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertBinaryModule(packageVersionId, namesList.get(i), createdAt.get(i),
                    metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in 'module_contents' table in the database.
     *
     * @param moduleId ID of the module (references 'modules.id')
     * @param fileId   ID of the file (references 'files.id')
     * @return ID of the new record = moduleId
     */
    public long insertModuleContent(long moduleId, long fileId) {
        var resultRecord = context.insertInto(ModuleContents.MODULE_CONTENTS,
                ModuleContents.MODULE_CONTENTS.MODULE_ID,
                ModuleContents.MODULE_CONTENTS.FILE_ID)
                .values(moduleId, fileId)
                .onConflictOnConstraint(Keys.UNIQUE_MODULE_FILE).doUpdate()
                .set(ModuleContents.MODULE_CONTENTS.MODULE_ID,
                        ModuleContents.MODULE_CONTENTS.MODULE_ID)
                .returning(ModuleContents.MODULE_CONTENTS.MODULE_ID).fetchOne();
        return resultRecord.getValue(ModuleContents.MODULE_CONTENTS.MODULE_ID);
    }

    /**
     * Inserts multiple records in the 'module_contents' table in the database.
     *
     * @param moduleIds List of modules IDs
     * @param fileIds   List of files IDs
     * @return List of IDs of new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertModuleContents(List<Long> moduleIds, List<Long> fileIds)
            throws IllegalArgumentException {
        if (moduleIds.size() != fileIds.size()) {
            throw new IllegalArgumentException("Lists should have equal size");
        }
        int length = fileIds.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertModuleContent(moduleIds.get(i), fileIds.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in 'binary_module_contents' table in the database.
     *
     * @param binaryModuleId ID of the binary module (references 'binary_modules.id')
     * @param fileId         ID of the file (references 'files.id')
     * @return ID of the new record = binaryModuleId
     */
    public long insertBinaryModuleContent(long binaryModuleId, long fileId) {
        var resultRecord = context.insertInto(BinaryModuleContents.BINARY_MODULE_CONTENTS,
                BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID,
                BinaryModuleContents.BINARY_MODULE_CONTENTS.FILE_ID)
                .values(binaryModuleId, fileId)
                .onConflictOnConstraint(Keys.UNIQUE_BINARY_MODULE_FILE).doUpdate()
                .set(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID,
                        BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID)
                .returning(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID).fetchOne();
        return resultRecord.getValue(BinaryModuleContents.BINARY_MODULE_CONTENTS.BINARY_MODULE_ID);
    }

    /**
     * Inserts multiple records in the 'binary_module_contents' table in the database.
     *
     * @param binaryModuleIds List of binary modules IDs
     * @param fileIds         List of files IDs
     * @return List of IDs of new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertBinaryModuleContents(List<Long> binaryModuleIds, List<Long> fileIds)
            throws IllegalArgumentException {
        if (fileIds.size() != binaryModuleIds.size()) {
            throw new IllegalArgumentException("Lists should have equal size");
        }
        int length = fileIds.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertBinaryModuleContent(binaryModuleIds.get(i), fileIds.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Insert a new record into 'files' table in the database.
     *
     * @param packageVersionId ID of the package version to which the file belongs
     *                         (references 'package_versions.id')
     * @param path             Path of the file
     * @param checksum         Checksum of the file
     * @param createdAt        Timestamp of the file
     * @param metadata         Metadata of the file
     * @return ID of the new record
     */
    public long insertFile(long packageVersionId, String path, byte[] checksum,
                           Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Files.FILES,
                Files.FILES.PACKAGE_VERSION_ID, Files.FILES.PATH,
                Files.FILES.CHECKSUM, Files.FILES.CREATED_AT,
                Files.FILES.METADATA)
                .values(packageVersionId, path, checksum, createdAt, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_VERSION_PATH).doUpdate()
                .set(Files.FILES.CHECKSUM, Files.FILES.as("excluded").CHECKSUM)
                .set(Files.FILES.CREATED_AT, Files.FILES.as("excluded").CREATED_AT)
                .set(Files.FILES.METADATA, JsonbDSL.concat(Files.FILES.METADATA,
                        Files.FILES.as("excluded").METADATA))
                .returning(Files.FILES.ID).fetchOne();
        return resultRecord.getValue(Files.FILES.ID);
    }

    /**
     * Insert multiple records in the 'files' table in the database.
     *
     * @param packageVersionId ID of the common package version
     * @param pathsList        List of paths of files
     * @param checksumsList    List of checksums of files
     * @param createdAt        List of timestamps of files
     * @param metadata         List of metadata of files
     * @return List of IDs of new records
     * @throws IllegalArgumentException if any of the lists have different size
     */
    public List<Long> insertFiles(long packageVersionId, List<String> pathsList,
                                  List<byte[]> checksumsList, List<Timestamp> createdAt,
                                  List<JSONObject> metadata) throws IllegalArgumentException {
        if (pathsList.size() != checksumsList.size() || checksumsList.size() != createdAt.size()
                || createdAt.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = pathsList.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertFile(packageVersionId, pathsList.get(i), checksumsList.get(i),
                    createdAt.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in the 'callables' table in the database.
     *
     * @param moduleId       ID of the module where the callable belongs (references 'modules.id')
     * @param fastenUri      URI of the callable in FASTEN
     * @param isInternalCall 'true' if call is internal, 'false' if external
     * @param createdAt      Timestamp when the callable was created
     * @param metadata       Metadata of the callable
     * @return ID of the new record
     */
    public long insertCallable(Long moduleId, String fastenUri, boolean isInternalCall,
                               Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Callables.CALLABLES,
                Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA)
                .values(moduleId, fastenUri, isInternalCall, createdAt, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_URI_CALL).doUpdate()
                .set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)
                .set(Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.as("excluded").CREATED_AT)
                .set(Callables.CALLABLES.METADATA, JsonbDSL.concat(Callables.CALLABLES.METADATA,
                        Callables.CALLABLES.as("excluded").METADATA))
                .returning(Callables.CALLABLES.ID).fetchOne();
        return resultRecord.getValue(Callables.CALLABLES.ID);
    }

    /**
     * Inserts multiple records in the 'callables' table in the database.
     *
     * @param moduleId         ID of the common module
     * @param fastenUris       List of FASTEN URIs
     * @param areInternalCalls List of booleans that show if callable is internal
     * @param createdAt        List of timestamps
     * @param metadata         List of metadata objects
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertCallables(long moduleId, List<String> fastenUris,
                                      List<Boolean> areInternalCalls, List<Timestamp> createdAt,
                                      List<JSONObject> metadata) throws IllegalArgumentException {
        if (fastenUris.size() != areInternalCalls.size()
                || areInternalCalls.size() != metadata.size()
                || metadata.size() != createdAt.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = fastenUris.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertCallable(moduleId, fastenUris.get(i),
                    areInternalCalls.get(i), createdAt.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in the 'edges' table in the database.
     *
     * @param sourceId ID of the source callable (references 'callables.id')
     * @param targetId ID of the target callable (references 'callables.id')
     * @param metadata Metadata of the edge between source and target
     * @return ID of the source callable (sourceId)
     */
    public long insertEdge(long sourceId, long targetId, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString())
                : JSONB.valueOf("{}");
        var resultRecord = context.insertInto(Edges.EDGES,
                Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.METADATA)
                .values(sourceId, targetId, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET).doUpdate()
                .set(Edges.EDGES.METADATA, JsonbDSL.concat(Edges.EDGES.METADATA,
                        Edges.EDGES.as("excluded").METADATA))
                .returning(Edges.EDGES.SOURCE_ID).fetchOne();
        return resultRecord.getValue(Edges.EDGES.SOURCE_ID);
    }

    /**
     * Inserts multiple records in the 'edges' table in the database.
     *
     * @param sourceIds List of IDs of source callables
     * @param targetIds List of IDs of target callables
     * @param metadata  List of metadata objects
     * @return List of IDs of source callables (sourceIds)
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertEdges(List<Long> sourceIds, List<Long> targetIds,
                                  List<JSONObject> metadata) throws IllegalArgumentException {
        if (sourceIds.size() != targetIds.size() || targetIds.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = sourceIds.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertEdge(sourceIds.get(i), targetIds.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Executes batch insert for 'edges' table.
     *
     * @param edges List of edges records to insert
     */
    public void batchInsertEdges(List<EdgesRecord> edges) {
        Query batchQuery = context.insertInto(Edges.EDGES,
                Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.METADATA)
                .values((Long) null, (Long) null, (JSONB) null)
                .onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET).doUpdate()
                .set(Edges.EDGES.METADATA, JsonbDSL.concat(Edges.EDGES.METADATA,
                        Edges.EDGES.as("excluded").METADATA));
        var batchBind = context.batch(batchQuery);
        for (var edge : edges) {
            batchBind = batchBind.bind(edge.getSourceId(), edge.getTargetId(), edge.getMetadata());
        }
        batchBind.execute();
    }

    /**
     * Executes batch insert for 'callables' table.
     *
     * @param callables List of callables records to insert
     */
    public List<Long> batchInsertCallables(List<CallablesRecord> callables) {
        var insert = context.insertInto(Callables.CALLABLES,
                Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA);
        for (var callable : callables) {
            insert = insert.values(callable.getModuleId(), callable.getFastenUri(),
                    callable.getIsInternalCall(), callable.getCreatedAt(), callable.getMetadata());
        }
        var result = insert.onConflictOnConstraint(Keys.UNIQUE_URI_CALL).doUpdate()
                .set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)
                .set(Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.as("excluded").CREATED_AT)
                .set(Callables.CALLABLES.METADATA, JsonbDSL.concat(Callables.CALLABLES.METADATA,
                        Callables.CALLABLES.as("excluded").METADATA))
                .returning(Callables.CALLABLES.ID).fetch();
        return result.getValues(Callables.CALLABLES.ID);
    }

    /**
     * Inserts all the callables from the CG.
     * First batch inserts all internal callables,
     * then retrieves IDs of all external callables that are already in the database,
     * and then batch inserts all new external callables.
     *
     * @param callables   List of callables. NB! First all internal callables and then all external.
     * @param numInternal Number of internal callables in the callables list
     * @return List of IDs of inserted callables from the database.
     */
    public List<Long> insertCallablesSeparately(List<CallablesRecord> callables, int numInternal) {
        var ids = new ArrayList<Long>(callables.size());
        var internalCallables = new ArrayList<CallablesRecord>(numInternal);
        var externalCallables = new ArrayList<CallablesRecord>(callables.size() - numInternal);
        for (int i = 0; i < callables.size(); i++) {
            if (i < numInternal) {
                internalCallables.add(callables.get(i));
            } else {
                externalCallables.add(callables.get(i));
            }
        }
        // Batch insert internal callables
        final var batchSize = 4096;
        final var callablesIterator = internalCallables.iterator();
        while (callablesIterator.hasNext()) {
            var callablesBatch = new ArrayList<CallablesRecord>(batchSize);
            while (callablesIterator.hasNext() && callablesBatch.size() < batchSize) {
                callablesBatch.add(callablesIterator.next());
            }
            var callablesIds = this.batchInsertCallables(callablesBatch);
            ids.addAll(callablesIds);
        }

        // Get IDs of external callables that are already in the database
        var urisCondition = Callables.CALLABLES.FASTEN_URI
                .eq(externalCallables.get(0).getFastenUri());
        for (int i = 1; i < externalCallables.size(); i++) {
            urisCondition = urisCondition
                    .or(Callables.CALLABLES.FASTEN_URI.eq(externalCallables.get(i).getFastenUri()));
        }
        var result = context
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.MODULE_ID.eq(-1L))
                .and(Callables.CALLABLES.IS_INTERNAL_CALL.eq(false))
                .and(urisCondition)
                .fetch();
        var uriMap = new HashMap<String, Long>(result.size());
        for (var tuple : result) {
            uriMap.put(tuple.value2(), tuple.value1());
        }

        // Batch insert external callables which are not in the database yet
        var newExternalCallables = new ArrayList<CallablesRecord>(
                externalCallables.size() - uriMap.size()
        );
        for (var callable : externalCallables) {
            if (!uriMap.containsKey(callable.getFastenUri())) {
                newExternalCallables.add(callable);
            }
        }
        final var newExternalCallablesIterator = newExternalCallables.iterator();
        while (newExternalCallablesIterator.hasNext()) {
            var callablesBatch = new ArrayList<CallablesRecord>(batchSize);
            while (newExternalCallablesIterator.hasNext() && callablesBatch.size() < batchSize) {
                callablesBatch.add(newExternalCallablesIterator.next());
            }
            var callablesIds = this.batchInsertCallables(callablesBatch);
            for (int i = 0; i < callablesBatch.size(); i++) {
                uriMap.put(callablesBatch.get(i).getFastenUri(), callablesIds.get(i));
            }
        }

        // Add external IDs to the result in the correct order
        for (var externalCallable : externalCallables) {
            ids.add(uriMap.get(externalCallable.getFastenUri()));
        }

        return ids;
    }


    //////////////////////////
    // VULNERABILITY PLUGIN //
    //////////////////////////

    /**
     * Injects in the metadata of the callable vulnerability information.
     * @param vulnJSON - JSON of the vulnerability to inject
     * @param callId - Long ID of the callable interested
     */
    public void injectCallableVulnerability(String vulnJSON, Long callId) {
        JSONObject vulnData = new JSONObject(vulnJSON);
        JSONArray vulns = new JSONArray();
        vulns.put(vulnData);

        var callableVulns = context.select(field("{0}->'vulnerabilities'",
                String.class, Callables.CALLABLES.METADATA))
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.equal(callId))
                .fetchOne();

        // Check if the JSON contains vulnerabilities already
        boolean checkExistence = callableVulns.get(0) != null;

        if (checkExistence) {
            // There is already a vulnerability object --> append a new one
            context
                    .update(Callables.CALLABLES)
                    .set(Callables.CALLABLES.METADATA,
                            field("jsonb_set(\n" +
                                    "  metadata,\n" +
                                    "  '{\"vulnerabilities\"}',\n" +
                                    "   (metadata -> 'vulnerabilities')::jsonb || '" + vulns.toString() + "'::jsonb," +
                                    "  true)", JSONB.class))
                    .where(Callables.CALLABLES.ID.equal(callId))
                    .execute();
        } else {
            // There is no vulnerability object --> add a new one
            context
                    .update(Callables.CALLABLES)
                    .set(Callables.CALLABLES.METADATA,
                            field("jsonb_set(\n" +
                                    "  metadata,\n" +
                                    "  '{\"vulnerabilities\"}',\n" +
                                    "   '" + vulns.toString() + "'::jsonb," +
                                    "  true)", JSONB.class))
                    .where(Callables.CALLABLES.ID.equal(callId))
                    .execute();
        }
    }

    /**
     * Injects in the metadata of the callable vulnerability information.
     * @param vulnJSON - JSON of the vulnerability to inject
     * @param pkgVersionId - Long ID of the packge_version interested
     */
    public void injectPackageVersionVulnerability(String vulnJSON, Long pkgVersionId) {
        JSONObject vulnData = new JSONObject(vulnJSON);
        JSONArray vulns = new JSONArray();
        vulns.put(vulnData);

        var packageVersionVuln = context.select(field("{0}->'vulnerabilities'",
                String.class, PackageVersions.PACKAGE_VERSIONS.METADATA))
                .from(PackageVersions.PACKAGE_VERSIONS)
                .where(PackageVersions.PACKAGE_VERSIONS.ID.equal(pkgVersionId))
                .fetchOne();

        // Check if the JSON contains vulnerabilities already
        boolean checkExistence = packageVersionVuln.get(0) != null;

        if (checkExistence) {
            // There is already a vulnerability object --> append a new one
            context
                .update(PackageVersions.PACKAGE_VERSIONS)
                .set(PackageVersions.PACKAGE_VERSIONS.METADATA,
                        field("jsonb_set(\n" +
                                "  metadata,\n" +
                                "  '{\"vulnerabilities\"}',\n" +
                                "   (metadata -> 'vulnerabilities')::jsonb || '" + vulns.toString() + "'::jsonb," +
                                "  true)", JSONB.class))
                .where(PackageVersions.PACKAGE_VERSIONS.ID.equal(pkgVersionId))
                .execute();
        } else {
            // There is no vulnerability object --> add a new one
            context
                .update(PackageVersions.PACKAGE_VERSIONS)
                .set(PackageVersions.PACKAGE_VERSIONS.METADATA,
                        field("jsonb_set(\n" +
                                "  metadata,\n" +
                                "  '{\"vulnerabilities\"}',\n" +
                                "   '" + vulns.toString() + "'::jsonb," +
                                "  true)", JSONB.class))
                .where(PackageVersions.PACKAGE_VERSIONS.ID.equal(pkgVersionId))
                .execute();
        }
    }

    /**
     * Finds all the callable ids with the given fasten_uri
     * @param fastenUri - String
     * @return - List of ids of the callables
     */
    public List<Long> getCallableIdsForFastenUri(String fastenUri) {
        List<Long> ids = new ArrayList<>();

        Result<Record> crs = context.select()
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.FASTEN_URI.equal(fastenUri))
                .fetch();

        for (Record cr : crs) {
            ids.add((Long) cr.get(0));
        }

        return ids;
    }

    /**
     * Finds all callables that belong to a module.
     * @param moduleId - Long ID of the module
     * @return - List of records
     */
    public Result<Record> getCallablesInModule(Long moduleId) {
        return context.select()
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.MODULE_ID.equal(moduleId))
                .fetch();
    }

    /**
     * Retrieve the fileId of the file that was patched.
     * @param packageVersionId - Long pkg version ID
     * @param filepath - path to the file
     * @return -1 if the file cannot be found
     */
    public Long getFileId(Long packageVersionId, String filepath) {
        // For the demo, just cut out the filename, without the path
        var splits = filepath.split("/");
        var filename = splits[splits.length - 1];

        FilesRecord fr = (FilesRecord) context.select()
                .from(Files.FILES)
                .where(Files.FILES.PACKAGE_VERSION_ID.equal(packageVersionId))
                .and(Files.FILES.PATH.equal(filename))
                .fetchOne();

        if (fr != null) {
            return fr.getId();
        } else {
            return -1L;
        }
    }

    /**
     * Gets the moduleId that corresponds to the file.
     * @param fileId - Long fileId
     * @return list of module Ids
     */
    public List<Long> getModuleIds(Long fileId) {
        List<Long> moduleIds = new ArrayList<>();
        Result<Record> mcr = context.select()
                .from(ModuleContents.MODULE_CONTENTS)
                .where(ModuleContents.MODULE_CONTENTS.FILE_ID.equal(fileId))
                .fetch();

        for (Record record : mcr) {
            moduleIds.add((Long) record.get(0));
        }

        return moduleIds;
    }

    /**
     * Finds the ID of the package given the package given coordinate and forge.
     * Note, this is ecosystem agnostic
     * @param coordinate - includes information about the package
     * @param forge - ['mvn', 'PyPI', 'Debian']
     * @return - Record of the package if found
     */
    public PackagesRecord getPackageIdFromCoordinate(String coordinate, String forge) {
        return (PackagesRecord) context.select()
                .from(Packages.PACKAGES)
                .where(Packages.PACKAGES.PACKAGE_NAME.equal(coordinate))
                .and(Packages.PACKAGES.FORGE.equal(forge))
                .fetchOne();
    }

    /**
     * Finds the ID of the package_version in all the version of the package.
     * @param pkgId - ID of the package
     * @param version - String of the version of the package_version
     * @return - Record of the package_version if found
     */
    public PackageVersionsRecord getPackageVersionIdFromVersion(Long pkgId, String version) {
        return (PackageVersionsRecord) context.select()
                .from(PackageVersions.PACKAGE_VERSIONS)
                .where(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.equal(pkgId))
                .and(PackageVersions.PACKAGE_VERSIONS.VERSION.equal(version))
                .fetchOne();
    }
}