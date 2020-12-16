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
import eu.fasten.core.data.Constants;
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
import eu.fasten.core.data.metadatadb.codegen.tables.VirtualImplementations;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.EdgesRecord;
import eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord;
import eu.fasten.core.utils.FastenUriUtils;
import org.apache.commons.math3.util.Pair;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.JSONFormat;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SelectField;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.*;

public class MetadataDao {

    private final Logger logger = LoggerFactory.getLogger(MetadataDao.class.getName());
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
     * Inserts a record in 'packages' table in the database.
     *
     * @param packageName Name of the package
     * @param forge       Forge of the package
     * @return ID of the new record
     */
    public long insertPackage(String packageName, String forge) {
        var resultRecord = context.insertInto(Packages.PACKAGES,
                Packages.PACKAGES.PACKAGE_NAME, Packages.PACKAGES.FORGE)
                .values(packageName, forge)
                .onConflictOnConstraint(Keys.UNIQUE_PACKAGE_FORGE).doUpdate()
                .set(Packages.PACKAGES.PACKAGE_NAME, Packages.PACKAGES.as("excluded").PACKAGE_NAME)
                .set(Packages.PACKAGES.FORGE, Packages.PACKAGES.as("excluded").FORGE)
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
     * @param packageId    ID of the package (references 'packages.id')
     * @param cgGenerator  Tool used to generate this callgraph
     * @param version      Version of the package
     * @param architecture Architecture of the package
     * @param createdAt    Timestamp when the package version was created
     * @param metadata     Metadata of the package version
     * @return ID of the new record
     */
    public long insertPackageVersion(long packageId, String cgGenerator, String version,
                                     String architecture, Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(PackageVersions.PACKAGE_VERSIONS,
                PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID,
                PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR,
                PackageVersions.PACKAGE_VERSIONS.VERSION,
                PackageVersions.PACKAGE_VERSIONS.ARCHITECTURE,
                PackageVersions.PACKAGE_VERSIONS.CREATED_AT,
                PackageVersions.PACKAGE_VERSIONS.METADATA)
                .values(packageId, cgGenerator, version, architecture, createdAt, metadataJsonb)
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
     * @param packageId     ID of the common package (references 'packages.id')
     * @param cgGenerators  List of code generators
     * @param versions      List of versions
     * @param architectures List of architectures
     * @param createdAt     List of timestamps
     * @param metadata      List of metadata objects
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertPackageVersions(long packageId, List<String> cgGenerators,
                                            List<String> versions, List<String> architectures,
                                            List<Timestamp> createdAt, List<JSONObject> metadata)
            throws IllegalArgumentException {
        if (cgGenerators.size() != versions.size() || versions.size() != createdAt.size()
                || createdAt.size() != metadata.size() || metadata.size() != architectures.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = cgGenerators.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertPackageVersion(packageId, cgGenerators.get(i), versions.get(i),
                    architectures.get(i), createdAt.get(i), metadata.get(i));
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
     * @param architecture     Architectures of the dependency
     * @param dependencyType   Types of the dependencies
     * @param alternativeGroup Alternative dependencies group
     * @param metadata         Metadata of the dependency
     * @return ID of the package version (packageVersionId)
     */
    public long insertDependency(long packageVersionId, long dependencyId, String[] versionRanges,
                                 String[] architecture, String[] dependencyType,
                                 Long alternativeGroup, JSONObject metadata) {
        var resultRecord = context.insertInto(Dependencies.DEPENDENCIES,
                Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID,
                Dependencies.DEPENDENCIES.DEPENDENCY_ID,
                Dependencies.DEPENDENCIES.VERSION_RANGE,
                Dependencies.DEPENDENCIES.ARCHITECTURE,
                Dependencies.DEPENDENCIES.DEPENDENCY_TYPE,
                Dependencies.DEPENDENCIES.ALTERNATIVE_GROUP,
                Dependencies.DEPENDENCIES.METADATA)
                .values(packageVersionId, dependencyId, versionRanges, architecture, dependencyType,
                        alternativeGroup, JSONB.valueOf(metadata.toString()))
                .onConflictOnConstraint(Keys.UNIQUE_VERSION_DEPENDENCY_RANGE).doUpdate()
                .set(Dependencies.DEPENDENCIES.VERSION_RANGE,
                        Dependencies.DEPENDENCIES.as("excluded").VERSION_RANGE)
                .set(Dependencies.DEPENDENCIES.ARCHITECTURE,
                        Dependencies.DEPENDENCIES.as("excluded").ARCHITECTURE)
                .set(Dependencies.DEPENDENCIES.DEPENDENCY_TYPE,
                        Dependencies.DEPENDENCIES.as("excluded").DEPENDENCY_TYPE)
                .set(Dependencies.DEPENDENCIES.ALTERNATIVE_GROUP,
                        Dependencies.DEPENDENCIES.as("excluded").ALTERNATIVE_GROUP)
                .set(Dependencies.DEPENDENCIES.METADATA,
                        JsonbDSL.concat(Dependencies.DEPENDENCIES.METADATA,
                                Dependencies.DEPENDENCIES.as("excluded").METADATA))
                .returning(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID).fetchOne();
        return resultRecord.getValue(Dependencies.DEPENDENCIES.PACKAGE_VERSION_ID);
    }

    /**
     * Inserts multiple 'dependencies' in the database for certain package.
     *
     * @param packageVersionId  ID of the package version
     * @param dependenciesIds   List of IDs of dependencies
     * @param versionRanges     List of version ranges
     * @param architectures     List of architectures of the dependencies
     * @param dependencyTypes   List of types of the dependencies
     * @param alternativeGroups List of alternative dependencies group
     * @param metadata          List of metadata
     * @return ID of the package (packageId)
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public long insertDependencies(long packageVersionId, List<Long> dependenciesIds,
                                   List<String[]> versionRanges, List<String[]> architectures,
                                   List<String[]> dependencyTypes, List<Long> alternativeGroups,
                                   List<JSONObject> metadata)
            throws IllegalArgumentException {
        if (dependenciesIds.size() != versionRanges.size()
                || versionRanges.size() != architectures.size()
                || architectures.size() != dependencyTypes.size()
                || dependencyTypes.size() != alternativeGroups.size()
                || alternativeGroups.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = dependenciesIds.size();
        for (int i = 0; i < length; i++) {
            insertDependency(packageVersionId, dependenciesIds.get(i), versionRanges.get(i),
                    architectures.get(i), dependencyTypes.get(i), alternativeGroups.get(i),
                    metadata.get(i));
        }
        return packageVersionId;
    }

    /**
     * Inserts a record in 'virtual_implementations' table in the database.
     *
     * @param virtualPackageVersionId ID of the virtual implementation of package version
     *                                (references 'package_versions.id)
     * @param packageVersionId        ID of the package version (references 'package_versions.id)
     * @return ID of the virtual implementation of package version (= virtualPackageVersionId)
     */
    public long insertVirtualImplementation(long virtualPackageVersionId, long packageVersionId) {
        var resultRecord = context.insertInto(VirtualImplementations.VIRTUAL_IMPLEMENTATIONS,
                VirtualImplementations.VIRTUAL_IMPLEMENTATIONS.VIRTUAL_PACKAGE_VERSION_ID,
                VirtualImplementations.VIRTUAL_IMPLEMENTATIONS.PACKAGE_VERSION_ID)
                .values(virtualPackageVersionId, packageVersionId)
                .onConflictOnConstraint(Keys.UNIQUE_VIRTUAL_IMPLEMENTATION).doUpdate()
                .set(VirtualImplementations.VIRTUAL_IMPLEMENTATIONS.PACKAGE_VERSION_ID,
                        VirtualImplementations.VIRTUAL_IMPLEMENTATIONS.PACKAGE_VERSION_ID)
                .returning(VirtualImplementations.VIRTUAL_IMPLEMENTATIONS
                        .VIRTUAL_PACKAGE_VERSION_ID)
                .fetchOne();
        return resultRecord.getValue(VirtualImplementations.VIRTUAL_IMPLEMENTATIONS
                .VIRTUAL_PACKAGE_VERSION_ID);
    }

    /**
     * Inserts multiple 'virtual_implementations' in the database.
     *
     * @param virtualPackageVersionIds List of IDs of virtual package versions
     * @param packageVersionIds        List of IDs of package versions
     * @return List of virtual package version IDs from the database
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertVirtualImplementations(List<Long> virtualPackageVersionIds,
                                                   List<Long> packageVersionIds) {
        if (virtualPackageVersionIds.size() != packageVersionIds.size()) {
            throw new IllegalArgumentException("Lists should have equal size");
        }
        int length = virtualPackageVersionIds.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertVirtualImplementation(virtualPackageVersionIds.get(i),
                    packageVersionIds.get(i));
            recordIds.add(result);
        }
        return recordIds;
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
                             Timestamp createdAt, JSONObject metadata,
                             boolean addDuplicates) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Modules.MODULES,
                Modules.MODULES.PACKAGE_VERSION_ID, Modules.MODULES.NAMESPACE,
                Modules.MODULES.CREATED_AT, Modules.MODULES.METADATA)
                .values(packageVersionId, namespace, createdAt, metadataJsonb)
                // FIXME
                /* .set(Modules.MODULES.CREATED_AT, Modules.MODULES.as("excluded").CREATED_AT) */
                // .set(Modules.MODULES.METADATA, JsonbDSL.concat(Modules.MODULES.METADATA,
                /* Modules.MODULES.as("excluded").METADATA)) */
                .returning(Modules.MODULES.ID).fetchOne();
        return resultRecord.getValue(Modules.MODULES.ID);
    }

    public long getModuleContent(long fileId) {
        var res = context.select(ModuleContents.MODULE_CONTENTS.MODULE_ID)
                .from(ModuleContents.MODULE_CONTENTS)
                .where(ModuleContents.MODULE_CONTENTS.FILE_ID.equal(fileId))
                .fetchOne();
        if (res == null) {
            return -1L;
        }
        return res.getValue(ModuleContents.MODULE_CONTENTS.MODULE_ID);
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
     * Insert a new record into 'files' table in the database.
     *
     * @param packageVersionId ID of the package version to which the file belongs
     *                         (references 'package_versions.id')
     * @param path             Path of the file
     * @return ID of the new record
     */
    public long insertFile(long packageVersionId, String path) {
        var resultRecord = context.insertInto(Files.FILES,
                Files.FILES.PACKAGE_VERSION_ID, Files.FILES.PATH)
                .values(packageVersionId, path)
                .onConflictOnConstraint(Keys.UNIQUE_VERSION_PATH).doUpdate()
                .set(Files.FILES.PACKAGE_VERSION_ID, Files.FILES.as("excluded").PACKAGE_VERSION_ID)
                .set(Files.FILES.PATH, Files.FILES.as("excluded").PATH)
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
     * @param lineStart      Line number where the callable starts
     * @param lineEnd        Line number where the callable ends
     * @param metadata       Metadata of the callable
     * @return ID of the new record
     */
    public long insertCallable(Long moduleId, String fastenUri, boolean isInternalCall,
                               Timestamp createdAt, Integer lineStart, Integer lineEnd,
                               JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Callables.CALLABLES,
                Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.LINE_START, Callables.CALLABLES.LINE_END,
                Callables.CALLABLES.METADATA)
                .values(moduleId, fastenUri, isInternalCall, createdAt, lineStart, lineEnd,
                        metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_URI_CALL).doUpdate()
                .set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)
                .set(Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.as("excluded").CREATED_AT)
                .set(Callables.CALLABLES.LINE_START, Callables.CALLABLES.as("excluded").LINE_START)
                .set(Callables.CALLABLES.LINE_END, Callables.CALLABLES.as("excluded").LINE_END)
                .set(Callables.CALLABLES.METADATA, JsonbDSL.concat(Callables.CALLABLES.METADATA,
                        Callables.CALLABLES.as("excluded").METADATA))
                .returning(Callables.CALLABLES.ID).fetchOne();
        return resultRecord.getValue(Callables.CALLABLES.ID);
    }

    /**
     * Updates a metadata in the 'callables' table in the database.
     * If the record doesn't exist, it will create a new one.
     *
     * @param moduleId   ID of the module where the callable belongs (references 'modules.id')
     * @param fastenUri  URI of the callable in FASTEN
     * @param isInternal 'true' if call is internal, 'false' if external
     * @param metadata   Metadata of the callable
     * @return ID of the record
     */
    public long updateCallableMetadata(Long moduleId, String fastenUri, boolean isInternal,
                                       JSONObject metadata) {
        var metadataJsonb = metadata != null
                ? JSONB.valueOf(metadata.toString()) : JSONB.valueOf("{}");
        var resultRecord = context.insertInto(Callables.CALLABLES,
                Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.METADATA)
                .values(moduleId, fastenUri, isInternal, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_URI_CALL).doUpdate()
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
     * @param lineStarts       List of line number where callable starts
     * @param lineEnds         List of line number where callable ends
     * @param metadata         List of metadata objects
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertCallables(long moduleId, List<String> fastenUris,
                                      List<Boolean> areInternalCalls, List<Timestamp> createdAt,
                                      List<Integer> lineStarts, List<Integer> lineEnds,
                                      List<JSONObject> metadata) throws IllegalArgumentException {
        if (fastenUris.size() != areInternalCalls.size()
                || areInternalCalls.size() != metadata.size()
                || createdAt.size() != lineStarts.size()
                || lineStarts.size() != lineEnds.size()
                || metadata.size() != createdAt.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = fastenUris.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertCallable(moduleId, fastenUris.get(i),
                    areInternalCalls.get(i), createdAt.get(i), lineStarts.get(i),
                    lineEnds.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in the 'edges' table in the database.
     *
     * @param sourceId  ID of the source callable (references 'callables.id')
     * @param targetId  ID of the target callable (references 'callables.id')
     * @param receivers Array of receivers data (one receiver per call-site)
     * @param metadata  Metadata of the edge between source and target
     * @return ID of the source callable (sourceId)
     */
    public long insertEdge(long sourceId, long targetId, ReceiverRecord[] receivers, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString())
                : JSONB.valueOf("{}");
        var resultRecord = context.insertInto(Edges.EDGES,
                Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID,
                Edges.EDGES.RECEIVERS, Edges.EDGES.METADATA)
                .values(sourceId, targetId, receivers, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET).doUpdate()
                .set(Edges.EDGES.RECEIVERS, Edges.EDGES.as("excluded").RECEIVERS)
                .set(Edges.EDGES.METADATA, JsonbDSL.concat(Edges.EDGES.METADATA,
                        Edges.EDGES.as("excluded").METADATA))
                .returning(Edges.EDGES.SOURCE_ID).fetchOne();
        return resultRecord.getValue(Edges.EDGES.SOURCE_ID);
    }

    /**
     * Inserts multiple records in the 'edges' table in the database.
     *
     * @param sourceIds     List of IDs of source callables
     * @param targetIds     List of IDs of target callables
     * @param receiversList List of arrays of receivers
     * @param metadata      List of metadata objects
     * @return List of IDs of source callables (sourceIds)
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertEdges(List<Long> sourceIds, List<Long> targetIds,
                                  List<ReceiverRecord[]> receiversList,
                                  List<JSONObject> metadata) throws IllegalArgumentException {
        if (sourceIds.size() != targetIds.size() || targetIds.size() != metadata.size()
                || metadata.size() != receiversList.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = sourceIds.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertEdge(sourceIds.get(i), targetIds.get(i),
                    receiversList.get(i), metadata.get(i));
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
                Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS,
                Edges.EDGES.METADATA)
                .values((Long) null, (Long) null, (ReceiverRecord[]) null, (JSONB) null)
                .onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET).doUpdate()
                .set(Edges.EDGES.RECEIVERS, Edges.EDGES.as("excluded").RECEIVERS)
                .set(Edges.EDGES.METADATA, JsonbDSL.concat(Edges.EDGES.METADATA,
                        Edges.EDGES.as("excluded").METADATA));
        var batchBind = context.batch(batchQuery);
        for (var edge : edges) {
            batchBind = batchBind.bind(edge.getSourceId(), edge.getTargetId(),
                    edge.getReceivers(), edge.getMetadata());
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
                Callables.CALLABLES.LINE_START, Callables.CALLABLES.LINE_END,
                Callables.CALLABLES.METADATA);
        for (var callable : callables) {
            insert = insert.values(callable.getModuleId(), callable.getFastenUri(),
                    callable.getIsInternalCall(), callable.getCreatedAt(),
                    callable.getLineStart(), callable.getLineEnd(), callable.getMetadata());
        }
        var result = insert.onConflictOnConstraint(Keys.UNIQUE_URI_CALL).doUpdate()
                .set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)
                .set(Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.as("excluded").CREATED_AT)
                .set(Callables.CALLABLES.LINE_START, Callables.CALLABLES.as("excluded").LINE_START)
                .set(Callables.CALLABLES.LINE_END, Callables.CALLABLES.as("excluded").LINE_END)
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
        HashMap<String, Long> uriMap = null;
        if (externalCallables.size() > 0) {
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
            uriMap = new HashMap<>(result.size());
            for (var tuple : result) {
                uriMap.put(tuple.value2(), tuple.value1());
            }

        }

        if (uriMap == null) {
            uriMap = new HashMap<>();
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

    protected Condition packageVersionWhereClause(String name, String version) {
        return trueCondition()
                .and(Packages.PACKAGES.PACKAGE_NAME.equalIgnoreCase(name))
                .and(PackageVersions.PACKAGE_VERSIONS.VERSION.equalIgnoreCase(version));
    }

    public String getPackageLastVersion(String packageName) {

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;

        // Building and executing the query
        Record queryResult = this.context
                .select(p.fields())
                .select(pv.VERSION)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .where(p.PACKAGE_NAME.equalIgnoreCase(packageName))
                .orderBy(pv.CREATED_AT.sortDesc())
                .limit(1)
                .fetchOne();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getPackageVersion(String packageName, String packageVersion, short offset, short limit) {
        return getPackageInfo(packageName, packageVersion, false, offset, limit);
    }

    public String getPackageMetadata(String packageName, String packageVersion, short offset, short limit) {
        return getPackageInfo(packageName, packageVersion, true, offset, limit);
    }

    protected String getPackageInfo(String packageName,
                                    String packageVersion,
                                    boolean metadataOnly,
                                    short offset,
                                    short limit) {

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;

        // Select clause
        SelectField<?>[] selectClause;
        if (metadataOnly) {
            selectClause = new SelectField[]{p.PACKAGE_NAME, pv.VERSION, pv.METADATA};
        } else {
            selectClause = pv.fields();
        }

        // Building and executing the query
        Record queryResult = this.context
                .select(selectClause)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .offset(offset)
                .limit(1)
                .fetchOne();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).format(true).quoteNested(false));
    }

    /**
     * Returns information about versions of a package, including potential vulnerabilities.
     *
     * @param packageName Name of the package of interest.
     * @param offset
     * @param limit
     * @return Package version information, including potential vulnerabilities.
     */
    public String getPackageVersions(String packageName, short offset, short limit) {

        // SQL query
        /*
            SELECT pv.*
            FROM packages AS p
                JOIN package_versions AS pv ON p.id = pv.package_id
            WHERE p.package_name=<package_name>
        */

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;

        // Query
        Result<Record> queryResult = context
                .select(pv.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .where(p.PACKAGE_NAME.equalIgnoreCase(packageName))
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    /**
     * Returns all dependencies of a given package version.
     *
     * @param packageName    Name of the package whose dependencies are of interest.
     * @param packageVersion Version of the package whose dependencies are of interest.
     * @param offset
     * @param limit
     * @return All package version dependencies.
     */
    public String getPackageDependencies(String packageName, String packageVersion, short offset, short limit) {

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Dependencies d = Dependencies.DEPENDENCIES;

        // Query
        Result<Record> queryResult = context
                .select(d.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(d).on(pv.ID.eq(d.PACKAGE_VERSION_ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getPackageModules(String packageName,
                                    String packageVersion,
                                    short offset,
                                    short limit) {
        return getModuleInfo(packageName, packageVersion, null, false, offset, limit);
    }

    public String getModuleMetadata(String packageName,
                                    String packageVersion,
                                    String moduleNamespace,
                                    short offset,
                                    short limit) {
        return getModuleInfo(packageName, packageVersion, moduleNamespace, true, offset, limit);
    }

    protected String getModuleInfo(String packageName,
                                   String packageVersion,
                                   String moduleNamespace,
                                   boolean metadataOnly,
                                   short offset,
                                   short limit) {

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;

        // Select clause
        SelectField<?>[] selectClause;
        if (metadataOnly) {
            selectClause = new SelectField[]{p.PACKAGE_NAME, pv.VERSION, m.NAMESPACE, m.METADATA};
        } else {
            selectClause = m.fields();
        }

        // Where clause
        Condition whereClause = packageVersionWhereClause(packageName, packageVersion);
        if (metadataOnly) {
            whereClause = whereClause.and(m.NAMESPACE.equalIgnoreCase(moduleNamespace));
        }

        // Building and executing the query
        Result<Record> queryResult = context
                .select(selectClause)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .where(whereClause)
                .offset(offset)
                .limit(metadataOnly ? 1 : limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getModuleFiles(String packageName,
                                 String packageVersion,
                                 String moduleNamespace,
                                 short offset,
                                 short limit) {

        // SQL query
        /*
            SELECT f.*
            FROM packages AS p
                JOIN package_versions AS pv ON p.id = pv.package_id
                JOIN modules AS m ON pv.id = m.package_version_id
                JOIN files AS f ON pv.id = f.package_version_id
            WHERE p.package_name = <packageName>
                AND pv.version = <packageVersion>
                AND m.namespace = <moduleNamespace>
        */

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Files f = Files.FILES;

        // Query
        Result<Record> queryResult = context
                .select(f.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(f).on(pv.ID.eq(f.PACKAGE_VERSION_ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .and(m.NAMESPACE.equalIgnoreCase(moduleNamespace))
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getModuleCallables(String packageName,
                                     String packageVersion,
                                     String moduleNamespace,
                                     short offset,
                                     short limit) {

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Callables c = Callables.CALLABLES;

        // Query
        Result<Record> queryResult = context
                .select(c.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(c).on(m.ID.eq(c.MODULE_ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .and(m.NAMESPACE.equalIgnoreCase(moduleNamespace))
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getPackageBinaryModules(String packageName, String packageVersion, short offset, short limit) {
        return getBinaryModuleInfo(packageName, packageVersion, null, false, offset, limit);
    }

    public String getBinaryModuleMetadata(String packageName,
                                          String packageVersion,
                                          String binaryModule,
                                          short offset,
                                          short limit) {
        return getBinaryModuleInfo(packageName, packageVersion, binaryModule, true, offset, limit);
    }

    // TODO Test with real DB data
    protected String getBinaryModuleInfo(String packageName,
                                         String packageVersion,
                                         String binaryModule,
                                         boolean metadataOnly,
                                         short offset,
                                         short limit) {

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        BinaryModules b = BinaryModules.BINARY_MODULES;

        // Select clause
        SelectField<?>[] selectClause;
        if (metadataOnly) {
            selectClause = new SelectField[]{p.PACKAGE_NAME, pv.VERSION, b.NAME, b.METADATA};
        } else {
            selectClause = b.fields();
        }

        // Where clause
        Condition whereClause = packageVersionWhereClause(packageName, packageVersion);
        if (metadataOnly) {
            whereClause = whereClause.and(b.NAME.equalIgnoreCase(binaryModule));
        }

        // Building and executing the query
        Result<Record> queryResult = context
                .select(selectClause)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(b).on(pv.ID.eq(b.PACKAGE_VERSION_ID))
                .where(whereClause)
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    // TODO Test with real DB data
    public String getBinaryModuleFiles(String packageName,
                                       String packageVersion,
                                       String binaryModule,
                                       short offset,
                                       short limit) {

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        BinaryModules b = BinaryModules.BINARY_MODULES;
        Files f = Files.FILES;

        // Query
        Result<Record> queryResult = context
                .select(f.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(b).on(pv.ID.eq(b.PACKAGE_VERSION_ID))
                .innerJoin(f).on(pv.ID.eq(f.PACKAGE_VERSION_ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .and(b.NAME.equalIgnoreCase(binaryModule))
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getPackageCallables(String packageName, String packageVersion, short offset, short limit) {
        return getCallablesInfo(packageName, packageVersion, null, false, offset, limit);
    }

    public String getCallableMetadata(String packageName,
                                      String packageVersion,
                                      String fastenURI,
                                      short offset,
                                      short limit) {
        return getCallablesInfo(packageName, packageVersion, fastenURI, true, offset, limit);
    }

    protected String getCallablesInfo(String packageName,
                                      String packageVersion,
                                      String fastenURI,
                                      boolean metadataOnly,
                                      short offset,
                                      short limit) {

        // SQL query
        /*
            SELECT {c.* | c.metadata}
            FROM packages AS p
                JOIN package_versions AS pv ON p.id = pv.package_id
                JOIN modules AS m ON pv.id = m.package_version_id
                JOIN callables AS c ON m.id = c.module_id
            WHERE p.package_name = <packageName>
                AND pv.version = <packageVersion>
                [AND c.fasten_uri = <fastenURI>]
        */

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Callables c = Callables.CALLABLES;

        // Select clause
        SelectField<?>[] selectClause;
        if (metadataOnly) {
            selectClause = new SelectField[]{p.PACKAGE_NAME, c.FASTEN_URI, c.METADATA};
        } else {
            selectClause = c.fields();
        }

        // Where clause
        Condition whereClause = packageVersionWhereClause(packageName, packageVersion);
        if (metadataOnly) {
            whereClause = whereClause.and(c.FASTEN_URI.equalIgnoreCase(fastenURI));
        }

        // Building and executing the query
        Result<Record> queryResult = context
                .select(selectClause)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(c).on(m.ID.eq(c.MODULE_ID))
                .where(whereClause)
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getArtifactName(long packageVersionId) {
        var result = context
                .select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION)
                .from(PackageVersions.PACKAGE_VERSIONS)
                .join(Packages.PACKAGES)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(PackageVersions.PACKAGE_VERSIONS.ID.eq(packageVersionId))
                .limit(1)
                .fetchOne();
        return result.value1() + Constants.mvnCoordinateSeparator + result.value2();
    }

    public String getPackageFiles(String packageName, String packageVersion, short offset, short limit) {

        // SQL query
        /*
            SELECT f.*
            FROM packages AS p
                JOIN package_versions AS pv ON p.id = pv.package_id
                JOIN files AS f ON pv.id = f.package_version_id
            WHERE p.package_name = <packageName>
                AND pv.version = <packageVersion>
        */

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Files f = Files.FILES;

        // Query
        Result<Record> queryResult = context
                .select(f.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(f).on(pv.ID.eq(f.PACKAGE_VERSION_ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getPackageCallgraph(String packageName, String packageVersion, short offset, short limit) {
        return getEdgesInfo(packageName, packageVersion, true, offset, limit);
    }

    public String getPackageEdges(String packageName, String packageVersion, short offset, short limit) {
        return getEdgesInfo(packageName, packageVersion, false, offset, limit);
    }

    protected String getEdgesInfo(String packageName,
                                  String packageVersion,
                                  boolean idsOnly,
                                  short offset,
                                  short limit) {

        // SQL query
        /*
            SELECT {e.* | (e.source_id, e.target_id)}
            FROM edges AS e
                JOIN callables AS c ON e.source_id = c.id
                JOIN modules AS m ON m.id = c.module_id
                JOIN package_versions AS pv ON pv.id = m.package_version_id
                JOIN packages AS p ON p.id = pv.package_id
            WHERE p.package_name = <packageName>
                AND pv.version = <packageVersion>;
         */

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Callables c = Callables.CALLABLES;
        Edges e = Edges.EDGES;

        // Select clause
        SelectField<?>[] selectClause;
        if (idsOnly) {
            selectClause = new SelectField[]{e.SOURCE_ID, e.TARGET_ID};
        } else {
            selectClause = e.fields();
        }

        // Query
        Result<Record> queryResult = context
                .select(selectClause)
                .from(e)
                .innerJoin(c).on(e.SOURCE_ID.eq(c.ID))
                .innerJoin(m).on(m.ID.eq(c.MODULE_ID))
                .innerJoin(pv).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(p).on(p.ID.eq(pv.PACKAGE_ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    /**
     * Retrieves an ID of certain package version.
     *
     * @param packageName Name of the package
     * @param version     Version of the package
     * @return ID of the package version
     */
    public Long getPackageVersionID(String packageName, String version) {
        var record = context
                .select(PackageVersions.PACKAGE_VERSIONS.ID)
                .from(PackageVersions.PACKAGE_VERSIONS)
                .where(packageVersionWhereClause(packageName, version))
                .limit(1)
                .fetchOne();
        if (record == null) {
            return null;
        }
        return record.value1();
    }

    /**
     * Returns a Map (Maven Coordinate -> Package Version ID) for a list of artifacts.
     *
     * @param artifacts List of Maven coordinates
     * @return HashMap from artifact to package version ID
     */
    public Map<String, Long> getPackageVersionIDs(List<String> artifacts) {
        var packageNames = new ArrayList<String>(artifacts.size());
        var versions = new ArrayList<String>(artifacts.size());
        for (var coordinate : artifacts) {
            var parts = coordinate.split(Constants.mvnCoordinateSeparator);
            var packageName = parts[0] + Constants.mvnCoordinateSeparator + parts[1];
            packageNames.add(packageName);
            versions.add(parts[2]);
        }
        var whereClause = packageVersionWhereClause(packageNames.get(0), versions.get(0));
        for (int i = 1; i < artifacts.size(); i++) {
            whereClause = whereClause.or(packageVersionWhereClause(packageNames.get(i), versions.get(0)));
        }
        var queryResult = context
                .select(Packages.PACKAGES.PACKAGE_NAME,
                        PackageVersions.PACKAGE_VERSIONS.VERSION,
                        PackageVersions.PACKAGE_VERSIONS.ID)
                .from(Packages.PACKAGES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(whereClause)
                .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .fetch();
        var map = new HashMap<String, Long>(queryResult.size());
        for (var record : queryResult) {
            var coordinate = record.value1() + Constants.mvnCoordinateSeparator + record.value2();
            map.put(coordinate, record.value3());
        }
        return map;
    }

    /**
     * Returns a Map (Callable ID -> JSON Metadata) for a list of callables.
     *
     * @param callableIds List of IDs of callables
     * @return HashMap from ID to metadata of callables
     */
    public Map<Long, JSONObject> getCallablesMetadata(Set<Long> callableIds) {
        var queryResult = context
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.METADATA)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(callableIds))
                .fetch();
        var metadataMap = new HashMap<Long, JSONObject>(queryResult.size());
        for (var record : queryResult) {
            var json = new JSONObject(record.value2().data());
            metadataMap.put(record.value1(), json);
        }
        return metadataMap;
    }

    public Map<Long, JSONObject> getCallables(List<Long> callableIds) {
        var queryResult = context
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.MODULE_ID,
                        Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.LINE_START,
                        Callables.CALLABLES.LINE_END, Callables.CALLABLES.METADATA)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(callableIds))
                .fetch();
        var callablesMap = new HashMap<Long, JSONObject>(queryResult.size());
        for (var record : queryResult) {
            var json = new JSONObject();
            json.put("fasten_uri", record.value2());
            json.put("module_id", record.value3());
            json.put("is_internal_call", record.value4());
            json.put("line_start", record.value5());
            json.put("line_end", record.value6());
            json.put("metadata", new JSONObject(record.value7().data()));
            callablesMap.put(record.value1(), json);
        }
        return callablesMap;
    }

    /**
     * Returns a Map (Pair of IDs -> JSON Metadata) for a list of edges.
     *
     * @param edges List of pairs of IDs which constitute edges
     * @return HashMap from Pair of IDs to metadata of the edge
     */
    public Map<Pair<Long, Long>, JSONObject> getEdgesMetadata(List<Pair<Long, Long>> edges) {
        var whereClause = and(Edges.EDGES.SOURCE_ID.eq(edges.get(0).getFirst()))
                .and(Edges.EDGES.TARGET_ID.eq(edges.get(0).getSecond()));
        for (int i = 1; i < edges.size(); i++) {
            whereClause = whereClause.or(
                    and(Edges.EDGES.SOURCE_ID.eq(edges.get(i).getFirst()))
                            .and(Edges.EDGES.TARGET_ID.eq(edges.get(i).getSecond()))
            );
        }
        var queryResult = context
                .select(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS, Edges.EDGES.METADATA)
                .from(Edges.EDGES)
                .where(whereClause)
                .fetch();
        var metadataMap = new HashMap<Pair<Long, Long>, JSONObject>(queryResult.size());
        for (var record : queryResult) {
            var json = new JSONObject(record.value4().data());
            var receiversJson = new JSONArray();
            for (var receiver : record.value3()) {
                var receiverJson = new JSONObject();
                receiverJson.put("line", receiver.value1());
                receiverJson.put("type", receiver.value2().getLiteral());
                receiverJson.put("receiver_uri", receiver.value3());
            }
            json.put("receivers", receiversJson);
            metadataMap.put(new Pair<>(record.value1(), record.value2()), json);
        }
        return metadataMap;
    }

    public List<String> getFullFastenUris(List<Long> callableIds) {
        var result = context
                .select(Packages.PACKAGES.FORGE,
                        Packages.PACKAGES.PACKAGE_NAME,
                        PackageVersions.PACKAGE_VERSIONS.VERSION,
                        Callables.CALLABLES.FASTEN_URI)
                .from(Packages.PACKAGES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(Packages.PACKAGES.ID.eq(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID))
                .join(Modules.MODULES)
                .on(Modules.MODULES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID))
                .join(Callables.CALLABLES)
                .on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
                .where(Callables.CALLABLES.ID.in(callableIds))
                .fetch();
        return result.stream()
                .map(r -> FastenUriUtils.generateFullFastenUri(r.value1(), r.value2(), r.value3(), r.value4()))
                .collect(Collectors.toList());
    }

    public Map<String, JSONObject> getCallablesMetadataByUri(String forge, String packageName, String version, List<String> fastenUris) {
        var result = context
                .select(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.METADATA)
                .from(Callables.CALLABLES)
                .join(Modules.MODULES).on(Modules.MODULES.ID.eq(Callables.CALLABLES.MODULE_ID))
                .join(PackageVersions.PACKAGE_VERSIONS).on(PackageVersions.PACKAGE_VERSIONS.ID.eq(Modules.MODULES.PACKAGE_VERSION_ID))
                .join(Packages.PACKAGES).on(Packages.PACKAGES.ID.eq(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID))
                .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName).and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))
                        .and("digest(callables.fasten_uri, 'sha1') in ("
                                + fastenUris.stream()
                                .map(u -> "digest('" + u + "', 'sha1')")
                                .collect(Collectors.joining(",")) + ")"))
                .fetch();
        var metadataMap = new HashMap<String, JSONObject>(result.size());
        for (var record : result) {
            metadataMap.put(FastenUriUtils.generateFullFastenUri(forge, packageName, version, record.value1()), new JSONObject(record.value2().data()));
        }
        return metadataMap;
    }

    public String getMavenCoordinate(long packageVersionId) {
        var record = context
                .select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION)
                .from(Packages.PACKAGES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(PackageVersions.PACKAGE_VERSIONS.ID.eq(packageVersionId))
                .limit(1)
                .fetchOne();
        if (record == null) {
            return null;
        }
        return record.value1() + Constants.mvnCoordinateSeparator + record.value2();
    }

    /**
     * Reconstructs the dependency network given a product and a timestamp.
     *
     * @param forge       Forge of the package
     * @param packageName Name of the package
     * @param timestamp   Timestamp of the package
     * @return dependencyNet    A set of revisions, along with an adjacency matrix
     */
    public String rebuildDependencyNet(String forge, String packageName, Timestamp timestamp, boolean transitive) {

        Packages p = Packages.PACKAGES.as("p");
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS.as("pv");
        Dependencies d = Dependencies.DEPENDENCIES.as("d");

        Result<Record> queryResult =
                context
                        .select(p.FORGE, p.PACKAGE_NAME)
                        .select(pv.VERSION, pv.CREATED_AT, pv.METADATA.as("package_metadata"))
                        .select(d.METADATA.as("dependencies_metadata"))
                        .from(p)
                        .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                        .innerJoin(d).on(pv.ID.equal(d.PACKAGE_VERSION_ID))
                        .where(p.FORGE.equalIgnoreCase(forge)
                                .and(p.PACKAGE_NAME.equalIgnoreCase(packageName)
                                        .and(pv.CREATED_AT.equal(timestamp))
                                )
                        )
                        .fetch();

        logger.debug("Total rows: " + queryResult.size());

        String result = queryResult.formatJSON(new JSONFormat().format(true).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
        return result;
    }

    /**
     * Retrieve a call graph for a given a package name and a timestamp.
     *
     * @param forge       Forge of the package
     * @param packageName Name of the package
     * @param timestamp   Timestamp when package was created
     * @param transitive  Boolean option to query transitive relationships
     * @return callGraph    A JSON-serialized RevisionCallGraph
     */
    public String getCallGraph(String forge, String packageName, Timestamp timestamp, boolean transitive) {

        Packages p = Packages.PACKAGES.as("p");
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS.as("pv");
        Modules m = Modules.MODULES.as("m");

        Result<Record> queryResult =
                context
                        .select(p.ID, p.FORGE, p.PACKAGE_NAME)
                        .select(pv.VERSION, pv.CREATED_AT)
                        .select(m.METADATA.as("module_metadata"))
                        .from(p)
                        .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                        .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                        .where(p.FORGE.equalIgnoreCase(forge)
                                .and(p.PACKAGE_NAME.equalIgnoreCase(packageName)
                                        .and(pv.CREATED_AT.equal(timestamp))
                                )
                        )
                        .fetch();

        logger.debug("Total rows: " + queryResult.size());

        String result = queryResult.formatJSON(new JSONFormat().format(true).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
        return result;
    }

    /**
     * Gets the vulnerabilities in the transitive closure of a package version
     *
     * @param forge       Forge of the package
     * @param packageName Name of the package
     * @param version     Version of the package
     * @return vulnerabilities  Paths of revisions, paths of files/compilation units, paths of functions
     */
    public String getVulnerabilities(String forge, String packageName, String version) {
        // FIXME: Query should be implemented as soon as the data become available
        Result<Record2<Long, String>> queryResult = context
                .select(Packages.PACKAGES.ID, Packages.PACKAGES.PACKAGE_NAME)
                .from(Packages.PACKAGES).fetch();

        for (Record r : queryResult) {
            Long id = r.getValue(Packages.PACKAGES.ID);
            String pkgName = r.getValue(Packages.PACKAGES.PACKAGE_NAME);
            logger.debug("id: " + id + "  / name: " + pkgName);
        }
        String result = queryResult.formatJSON(new JSONFormat().format(true).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
        logger.debug("Query dummy result: " + result);
        return ("dummy getVulnerabilities query OK!");
    }

    /**
     * Impact analysis: the user asks the KB to compute the impact of a semantic change to a function
     *
     * @param forge       Forge of the package
     * @param packageName Name of the package
     * @param version     Version of the package
     * @param transitive  Boolean option to query transitive relationships
     * @return impact       The full set of functions reachable from the provided function
     */
    public String updateImpact(String forge, String packageName, String version, boolean transitive) {
        // FIXME: Query should be implemented as soon as the data become available
        Result<Record2<Long, String>> queryResult = context
                .select(Packages.PACKAGES.ID, Packages.PACKAGES.PACKAGE_NAME)
                .from(Packages.PACKAGES).fetch();

        for (Record r : queryResult) {
            Long id = r.getValue(Packages.PACKAGES.ID);
            String pkgName = r.getValue(Packages.PACKAGES.PACKAGE_NAME);
            logger.debug("id: " + id + "  / name: " + pkgName);
        }
        String result = queryResult.formatJSON(new JSONFormat().format(true).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
        logger.debug("Query dummy result: " + result);
        return ("dummy updateImpact query OK!");
    }

    /**
     * Update the static CG of a package version with new edges
     *
     * @param forge       Forge of the package
     * @param packageName Name of the package
     * @param version     Version of the package
     * @return cgEdges       A list of edges that where added
     */
    public String updateCg(String forge, String packageName, String version) {
        // FIXME: Query should be implemented as soon as the data become available
        Result<Record2<Long, String>> queryResult = context
                .select(Packages.PACKAGES.ID, Packages.PACKAGES.PACKAGE_NAME)
                .from(Packages.PACKAGES).fetch();

        for (Record r : queryResult) {
            Long id = r.getValue(Packages.PACKAGES.ID);
            String pkgName = r.getValue(Packages.PACKAGES.PACKAGE_NAME);
            logger.debug("id: " + id + "  / name: " + pkgName);
        }
        String result = queryResult.formatJSON(new JSONFormat().format(true).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
        logger.debug("Query dummy result: " + result);
        return ("dummy updateCg query OK!");
    }

}
