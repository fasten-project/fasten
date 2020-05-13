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
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Query;
import org.json.JSONObject;

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
     * @return ID of the package version (packageVersionId)
     */
    public long insertDependency(long packageVersionId, long dependencyId, String[] versionRanges) {
        var resultRecord = context.insertInto(Dependencies.DEPENDENCIES,
                Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID,
                Dependencies.DEPENDENCIES.VERSION_RANGE)
                .values(packageVersionId, dependencyId, versionRanges)
                .onConflictOnConstraint(Keys.UNIQUE_VERSION_DEPENDENCY_RANGE).doUpdate()
                .set(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID,
                        Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID)
                .returning(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID).fetchOne();
        return resultRecord.getValue(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID);
    }

    /**
     * Inserts multiple 'dependencies' int the database for certain package.
     *
     * @param packageVersionId ID of the package version
     * @param dependenciesIds  List of IDs of dependencies
     * @param versionRanges    List of version ranges
     * @return ID of the package (packageId)
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public long insertDependencies(long packageVersionId, List<Long> dependenciesIds,
                                   List<String[]> versionRanges)
            throws IllegalArgumentException {
        if (dependenciesIds.size() != versionRanges.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = dependenciesIds.size();
        for (int i = 0; i < length; i++) {
            insertDependency(packageVersionId, dependenciesIds.get(i), versionRanges.get(i));
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
}