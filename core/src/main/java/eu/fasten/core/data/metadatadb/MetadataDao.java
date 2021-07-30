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
import eu.fasten.core.data.metadatadb.codegen.enums.Access;
import eu.fasten.core.data.metadatadb.codegen.enums.CallableType;
import eu.fasten.core.data.metadatadb.codegen.tables.*;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallSitesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.IngestedArtifactsRecord;
import eu.fasten.core.maven.data.PackageVersionNotFoundException;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.utils.FastenUriUtils;
import org.apache.commons.math3.util.Pair;
import org.jooq.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.sql.Timestamp;
import java.util.*;

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

    public long insertArtifactRepository(String repositoryBaseUrl) {
        var result = context.insertInto(ArtifactRepositories.ARTIFACT_REPOSITORIES,
                ArtifactRepositories.ARTIFACT_REPOSITORIES.REPOSITORY_BASE_URL)
                .values(repositoryBaseUrl)
                .onConflictOnConstraint(Keys.UNIQUE_ARTIFACT_REPOSITORIES).doUpdate()
                .set(ArtifactRepositories.ARTIFACT_REPOSITORIES.REPOSITORY_BASE_URL,
                        ArtifactRepositories.ARTIFACT_REPOSITORIES.as("excluded").REPOSITORY_BASE_URL)
                .returning(ArtifactRepositories.ARTIFACT_REPOSITORIES.ID).fetchOne();
        if (result == null) {
            return -1;
        }
        return result.getId();
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
    public long insertPackageVersion(long packageId, String cgGenerator, String version, Long artifactRepositoryId,
                                     String architecture, Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(PackageVersions.PACKAGE_VERSIONS,
                PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID,
                PackageVersions.PACKAGE_VERSIONS.CG_GENERATOR,
                PackageVersions.PACKAGE_VERSIONS.VERSION,
                PackageVersions.PACKAGE_VERSIONS.ARTIFACT_REPOSITORY_ID,
                PackageVersions.PACKAGE_VERSIONS.ARCHITECTURE,
                PackageVersions.PACKAGE_VERSIONS.CREATED_AT,
                PackageVersions.PACKAGE_VERSIONS.METADATA)
                .values(packageId, cgGenerator, version, artifactRepositoryId, architecture, createdAt, metadataJsonb)
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
     * Inserts outbound licenses at the package version level.
     *
     * @param coordinates the coordinates whose outbound licenses are about to be inserted.
     * @param outboundLicenses the package version's outbound licenses.
     * @return the updated metadata field.
     */
    public String insertPackageOutboundLicenses(Revision coordinates,
                                                String outboundLicenses) {
        return insertPackageOutboundLicenses(
                coordinates.groupId,
                coordinates.artifactId,
                coordinates.version.toString(),
                outboundLicenses);
    }

    /**
     * Inserts outbound licenses at the package version level.
     *
     * @param groupId          the group ID of the package version whose outbound licenses are about to be inserted.
     * @param artifactId       the artifact ID of the package version whose outbound licenses are about to be inserted.
     * @param packageVersion   the package version whose outbound licenses are about to be inserted.
     * @param outboundLicenses the package version's outbound licenses.
     * @return the updated metadata field.
     */
    public String insertPackageOutboundLicenses(String groupId,
                                                String artifactId,
                                                String packageVersion,
                                                String outboundLicenses) {
        return insertPackageOutboundLicenses(
                MavenUtilities.getMavenCoordinateName(groupId, artifactId),
                packageVersion,
                outboundLicenses);
    }

    /**
     * Inserts outbound licenses at the package version level.
     *
     * @param packageName      the package name whose outbound licenses are about to be inserted.
     * @param packageVersion   the package version whose outbound licenses are about to be inserted.
     * @param outboundLicenses the package version's outbound licenses.
     * @return the updated metadata field.
     */
    public String insertPackageOutboundLicenses(String packageName, String packageVersion, String outboundLicenses) {

        logger.debug("Inserting outbound licenses for " + packageName + ":" + packageVersion + ": " + outboundLicenses);

        /*  Warning!
            The `concat()` method casts the first argument to `VARCHAR`, causing errors!

            Packages p = Packages.PACKAGES;
            PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
            Record updatedMetadata = context.update(pv)
                    .set(
                            pv.METADATA,
                            when(pv.METADATA.isNull(), JSONB.valueOf("{}")).otherwise(pv.METADATA)
                                    .concat(outboundLicenses).cast(SQLDataType.JSONB)
                    )
                    .from(p)
                    .where(p.ID.eq(pv.PACKAGE_ID).and(packageVersionWhereClause(packageName, packageVersion)))
                    .returning(pv.METADATA)
                    .fetchOne();
        */
        // Using plain SQL
        Object updatedMetadata = context.fetchValue("UPDATE package_versions pv\n" +
                "SET metadata = (CASE WHEN metadata IS NULL THEN '{}'::jsonb ELSE metadata END) || {0}\n" +
                "    FROM packages p\n" +
                "WHERE p.id = pv.package_id\n" +
                "  AND p.package_name = LOWER({1})\n" +
                "  AND pv.version = LOWER({2})\n" +
                "    RETURNING pv.metadata;", JSONB.valueOf(outboundLicenses), packageName, packageVersion);

        // Updated metadata field
        logger.debug("`updatedMetadata`: " + updatedMetadata);
        assert updatedMetadata != null; // FIXME
        return updatedMetadata.toString();
    }

    /**
     * Inserts scanned licenses at the file level.
     *
     * @param coordinates  the Maven coordinates to which the file belongs.
     * @param filePath     the path of the file whose scanned licenses are about to be inserte.
     * @param fileLicenses the scanned licenses of the file.
     * @return the updated metadata field. Can be `null` in case the file is not present in the DB.
     */
    @Nullable
    public String insertFileLicenses(Revision coordinates,
                                     String filePath,
                                     String fileLicenses) {
        return insertFileLicenses(
                coordinates.groupId,
                coordinates.artifactId,
                coordinates.version.toString(),
                filePath,
                fileLicenses);
    }

    /**
     * Inserts scanned licenses at the file level.
     *
     * @param groupId        the group ID of the package to which the file belongs.
     * @param artifactId     the artifact ID of the package to which the file belongs.
     * @param packageVersion the version of the package to which the file belongs.
     * @param filePath       the path of the file whose scanned licenses are about to be inserte.
     * @param fileLicenses   the scanned licenses of the file.
     * @return the updated metadata field. Can be `null` in case the file is not present in the DB.
     */
    @Nullable
    public String insertFileLicenses(String groupId,
                                     String artifactId,
                                     String packageVersion,
                                     String filePath,
                                     String fileLicenses) {
        return insertFileLicenses(
                MavenUtilities.getMavenCoordinateName(groupId, artifactId),
                packageVersion,
                filePath,
                fileLicenses);
    }

    /**
     * Inserts scanned licenses at the file level.
     *
     * @param packageName    the name of the package to which the file belongs.
     * @param packageVersion the version of the package to which the file belongs.
     * @param filePath       the path of the file whose scanned licenses are about to be inserte.
     * @param fileLicenses   the scanned licenses of the file.
     * @return the updated metadata field. Can be `null` in case the file is not present in the DB.
     */
    @Nullable
    public String insertFileLicenses(String packageName,
                                     String packageVersion,
                                     String filePath,
                                     String fileLicenses) {

        logger.debug("Inserting file licenses for " + packageName + ":" + packageVersion + ", file" +
                filePath + ": " + fileLicenses);

        // Can be `null` in case the DB does not have this file
        @Nullable Object updatedMetadata = context.fetchValue("UPDATE files f\n" +
                        "SET metadata = (CASE WHEN f.metadata IS NULL THEN '{}'::jsonb ELSE f.metadata END) || {0}\n" +
                        "    FROM packages p JOIN package_versions pv ON p.id = pv.package_id\n" +
                        "WHERE f.package_version_id = pv.id\n" +
                        "  AND p.package_name = LOWER({1})\n" +
                        "  AND pv.version = LOWER({2})\n" +
                        "  AND f.path ILIKE '%' || array_to_string(" +
                        "    (regexp_split_to_array({3}, '/'))[(array_length(regexp_split_to_array({3}, '/'), 1) - 1):],\n" +
                        "    '/')\n" +
                        "    RETURNING f.metadata;\n",
                JSONB.valueOf(fileLicenses),
                packageName,
                packageVersion,
                filePath);

        logger.debug("`updatedMetadata`: " + updatedMetadata);

        // Updated metadata field
        return updatedMetadata == null ? null : updatedMetadata.toString();
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
                                            List<String> versions, List<Long> artifactRepositoriesIds,
                                            List<String> architectures, List<Timestamp> createdAt,
                                            List<JSONObject> metadata)
            throws IllegalArgumentException {
        if (cgGenerators.size() != versions.size() || versions.size() != createdAt.size()
                || createdAt.size() != metadata.size() || metadata.size() != architectures.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = cgGenerators.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertPackageVersion(packageId, cgGenerators.get(i), versions.get(i),
                    artifactRepositoriesIds.get(i), architectures.get(i), createdAt.get(i), metadata.get(i));
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
     * Inserts a record in 'modules' table in the database.
     *
     * @param packageVersionId ID of the package version where the module belongs
     *                         (references 'package_versions.id')
     * @param namespace_id     ID of the namespace of the module (references 'namespaces.id`)
     * @param metadata         Metadata of the module
     * @param annotations      Annotations of the class and their values
     * @return ID of the new record
     */
    public long insertModule(long packageVersionId, long namespace_id, Boolean isFinal, Access access,
                             Long[] superClasses, Long[] superInterfaces, JSONObject metadata, JSONObject annotations) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var annotationsJsonb = annotations != null ? JSONB.valueOf(annotations.toString()) : null;
        var resultRecord = context.insertInto(Modules.MODULES,
                Modules.MODULES.PACKAGE_VERSION_ID, Modules.MODULES.MODULE_NAME_ID,
                Modules.MODULES.FINAL, Modules.MODULES.ACCESS, Modules.MODULES.SUPER_CLASSES,
                Modules.MODULES.SUPER_INTERFACES, Modules.MODULES.METADATA, Modules.MODULES.ANNOTATIONS)
                .values(packageVersionId, namespace_id, isFinal, access, superClasses, superInterfaces, metadataJsonb, annotationsJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_VERSION_NAMESPACE).doUpdate()
                .set(Modules.MODULES.FINAL, Modules.MODULES.as("excluded").FINAL)
                .set(Modules.MODULES.ACCESS, Modules.MODULES.as("excluded").ACCESS)
                .set(Modules.MODULES.SUPER_CLASSES, Modules.MODULES.as("excluded").SUPER_CLASSES)
                .set(Modules.MODULES.SUPER_INTERFACES, Modules.MODULES.as("excluded").SUPER_INTERFACES)
                .set(Modules.MODULES.METADATA, field("coalesce(modules.metadata, '{}'::jsonb) || excluded.metadata", JSONB.class))
                .set(Modules.MODULES.ANNOTATIONS, field("coalesce(modules.annotations, '{}'::jsonb) || excluded.annotations", JSONB.class))
                .returning(Modules.MODULES.ID).fetchOne();
        return resultRecord.getValue(Modules.MODULES.ID);
    }

    /**
     * Inserts a record in 'modules' table in the database.
     *
     * @param packageVersionId ID of the package version where the module belongs
     *                         (references 'package_versions.id')
     * @param namespace_id     ID of the namespace of the module (references 'namespaces.id`)
     * @param metadata         Metadata of the module
     * @return ID of the new record
     */
    public long insertModule(long packageVersionId, long namespace_id,
                             JSONObject metadata,
                             boolean addDuplicates) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Modules.MODULES,
                Modules.MODULES.PACKAGE_VERSION_ID, Modules.MODULES.MODULE_NAME_ID,
                Modules.MODULES.METADATA)
                .values(packageVersionId, namespace_id, metadataJsonb)
                // FIXME
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
     * Inserts a record in the 'callables' table in the database.
     *
     * @param moduleId       ID of the module where the callable belongs (references 'modules.id')
     * @param fastenUri      URI of the callable in FASTEN
     * @param isInternalCall 'true' if call is internal, 'false' if external
     * @param lineStart      Line number where the callable starts
     * @param lineEnd        Line number where the callable ends
     * @param type           Type of the callable
     * @param defined        'true' of callable is defined, 'false' otherwise
     * @param access         Access of the callable
     * @param metadata       Metadata of the callable
     * @return ID of the new record
     */
    public long insertCallable(Long moduleId, String fastenUri, boolean isInternalCall,
                               Integer lineStart, Integer lineEnd,
                               CallableType type, boolean defined, Access access,
                               JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Callables.CALLABLES,
                Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.LINE_START,
                Callables.CALLABLES.LINE_END, Callables.CALLABLES.TYPE, Callables.CALLABLES.DEFINED,
                Callables.CALLABLES.ACCESS, Callables.CALLABLES.METADATA)
                .values(moduleId, fastenUri, isInternalCall, lineStart, lineEnd, type, defined, access, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_URI_CALL).doUpdate()
                .set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)
                .set(Callables.CALLABLES.LINE_START, Callables.CALLABLES.as("excluded").LINE_START)
                .set(Callables.CALLABLES.LINE_END, Callables.CALLABLES.as("excluded").LINE_END)
                .set(Callables.CALLABLES.TYPE, Callables.CALLABLES.as("excluded").TYPE)
                .set(Callables.CALLABLES.DEFINED, Callables.CALLABLES.as("excluded").DEFINED)
                .set(Callables.CALLABLES.ACCESS, Callables.CALLABLES.as("excluded").ACCESS)
                .set(Callables.CALLABLES.METADATA, field("coalesce(callables.metadata, '{}'::jsonb) || excluded.metadata", JSONB.class))
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
                ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Callables.CALLABLES,
                Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL, Callables.CALLABLES.METADATA)
                .values(moduleId, fastenUri, isInternal, metadataJsonb)
                .onConflictOnConstraint(Keys.UNIQUE_URI_CALL).doUpdate()
                .set(Callables.CALLABLES.METADATA, field("coalesce(callables.metadata, '{}'::jsonb) || excluded.metadata", JSONB.class))
                .returning(Callables.CALLABLES.ID).fetchOne();
        return resultRecord.getValue(Callables.CALLABLES.ID);
    }

    /**
     * Inserts multiple records in the 'callables' table in the database.
     *
     * @param moduleId         ID of the common module
     * @param fastenUris       List of FASTEN URIs
     * @param areInternalCalls List of booleans that show if callable is internal
     * @param lineStarts       List of line number where callable starts
     * @param lineEnds         List of line number where callable ends
     * @param metadata         List of metadata objects
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertCallables(long moduleId, List<String> fastenUris,
                                      List<Boolean> areInternalCalls,
                                      List<Integer> lineStarts, List<Integer> lineEnds,
                                      List<CallableType> types, List<Boolean> defined,
                                      List<Access> accesses, List<JSONObject> metadata)
            throws IllegalArgumentException {
        if (fastenUris.size() != areInternalCalls.size()
                || areInternalCalls.size() != metadata.size()
                || metadata.size() != lineStarts.size()
                || lineStarts.size() != lineEnds.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = fastenUris.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertCallable(moduleId, fastenUris.get(i),
                    areInternalCalls.get(i), lineStarts.get(i),
                    lineEnds.get(i), types.get(i), defined.get(i), accesses.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Executes batch insert for 'callsites' table.
     *
     * @param callsites List of callsites records to insert
     */
    public void batchInsertEdges(List<CallSitesRecord> callsites) {
        var insert = context.insertInto(CallSites.CALL_SITES,
                CallSites.CALL_SITES.SOURCE_ID, CallSites.CALL_SITES.TARGET_ID, CallSites.CALL_SITES.CALL_TYPE,
                CallSites.CALL_SITES.RECEIVER_TYPE_IDS, CallSites.CALL_SITES.LINE, CallSites.CALL_SITES.METADATA);
        for (var call : callsites) {
            insert = insert.values(call.getSourceId(), call.getTargetId(), call.getCallType(), call.getReceiverTypeIds(), call.getLine(), call.getMetadata());
        }
        insert.onConflictOnConstraint(Keys.UNIQUE_SOURCE_TARGET).doUpdate()
                .set(CallSites.CALL_SITES.CALL_TYPE, CallSites.CALL_SITES.as("excluded").CALL_TYPE)
                .set(CallSites.CALL_SITES.RECEIVER_TYPE_IDS, CallSites.CALL_SITES.as("excluded").RECEIVER_TYPE_IDS)
                .set(CallSites.CALL_SITES.LINE, CallSites.CALL_SITES.as("excluded").LINE)
                .set(CallSites.CALL_SITES.METADATA, field("coalesce(call_sites.metadata, '{}'::jsonb) || excluded.metadata", JSONB.class))
                .execute();
    }

    /**
     * Executes batch insert for 'callables' table.
     *
     * @param callables List of callables records to insert
     */
    public List<Long> batchInsertCallables(List<CallablesRecord> callables) {
        var insert = context.insertInto(Callables.CALLABLES,
                Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_INTERNAL_CALL,
                Callables.CALLABLES.LINE_START, Callables.CALLABLES.LINE_END,
                Callables.CALLABLES.TYPE, Callables.CALLABLES.DEFINED, Callables.CALLABLES.ACCESS,
                Callables.CALLABLES.METADATA);
        for (var callable : callables) {
            insert = insert.values(callable.getModuleId(), callable.getFastenUri(),
                    callable.getIsInternalCall(),
                    callable.getLineStart(), callable.getLineEnd(), callable.getType(),
                    callable.getDefined(), callable.getAccess(), callable.getMetadata());
        }
        var result = insert.onConflictOnConstraint(Keys.UNIQUE_URI_CALL).doUpdate()
                .set(Callables.CALLABLES.MODULE_ID, Callables.CALLABLES.as("excluded").MODULE_ID)
                .set(Callables.CALLABLES.LINE_START, Callables.CALLABLES.as("excluded").LINE_START)
                .set(Callables.CALLABLES.LINE_END, Callables.CALLABLES.as("excluded").LINE_END)
                .set(Callables.CALLABLES.TYPE, Callables.CALLABLES.as("excluded").TYPE)
                .set(Callables.CALLABLES.DEFINED, Callables.CALLABLES.as("excluded").DEFINED)
                .set(Callables.CALLABLES.ACCESS, Callables.CALLABLES.as("excluded").ACCESS)
                .set(Callables.CALLABLES.METADATA, field("coalesce(callables.metadata, '{}'::jsonb) || excluded.metadata", JSONB.class))
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

    public void insertIngestedArtifact(String packageName, String version, Timestamp timestamp) {
        context.insertInto(IngestedArtifacts.INGESTED_ARTIFACTS,
                IngestedArtifacts.INGESTED_ARTIFACTS.PACKAGE_NAME,
                IngestedArtifacts.INGESTED_ARTIFACTS.VERSION,
                IngestedArtifacts.INGESTED_ARTIFACTS.TIMESTAMP)
                .values(packageName, version, timestamp)
                .onConflictOnConstraint(Keys.UNIQUE_INGESTED_ARTIFACTS).doUpdate()
                .set(IngestedArtifacts.INGESTED_ARTIFACTS.PACKAGE_NAME, IngestedArtifacts.INGESTED_ARTIFACTS.as("excluded").PACKAGE_NAME)
                .set(IngestedArtifacts.INGESTED_ARTIFACTS.VERSION, IngestedArtifacts.INGESTED_ARTIFACTS.as("excluded").VERSION)
                .set(IngestedArtifacts.INGESTED_ARTIFACTS.TIMESTAMP, IngestedArtifacts.INGESTED_ARTIFACTS.as("excluded").TIMESTAMP)
                .execute();
    }

    public void batchInsertIngestedArtifacts(List<IngestedArtifactsRecord> ingestedArtifacts) {
        var insert = context.insertInto(IngestedArtifacts.INGESTED_ARTIFACTS,
                IngestedArtifacts.INGESTED_ARTIFACTS.PACKAGE_NAME,
                IngestedArtifacts.INGESTED_ARTIFACTS.VERSION,
                IngestedArtifacts.INGESTED_ARTIFACTS.TIMESTAMP);
        for (var artifact : ingestedArtifacts) {
            insert = insert.values(artifact.getPackageName(), artifact.getVersion(), artifact.getTimestamp());
        }
        insert.onConflictOnConstraint(Keys.UNIQUE_INGESTED_ARTIFACTS).doUpdate()
                .set(IngestedArtifacts.INGESTED_ARTIFACTS.TIMESTAMP, IngestedArtifacts.INGESTED_ARTIFACTS.as("excluded").TIMESTAMP)
                .execute();
    }

    public boolean isArtifactIngested(String packageName, String version) {
        var result = context
                .select(IngestedArtifacts.INGESTED_ARTIFACTS.PACKAGE_NAME,
                        IngestedArtifacts.INGESTED_ARTIFACTS.VERSION)
                .from(IngestedArtifacts.INGESTED_ARTIFACTS)
                .where(IngestedArtifacts.INGESTED_ARTIFACTS.PACKAGE_NAME.eq(packageName))
                .and(IngestedArtifacts.INGESTED_ARTIFACTS.VERSION.eq(version))
                .fetch();
        return !result.isEmpty();
    }

    public Set<Pair<String, String>> areArtifactsIngested(List<String> packageNames, List<String> versions) {
        var condition = IngestedArtifacts.INGESTED_ARTIFACTS.PACKAGE_NAME.eq(packageNames.get(0))
                .and(IngestedArtifacts.INGESTED_ARTIFACTS.VERSION.eq(versions.get(0)));
        for (int i = 1; i < packageNames.size(); i++) {
            condition = condition.or(IngestedArtifacts.INGESTED_ARTIFACTS.PACKAGE_NAME.eq(packageNames.get(i))
                    .and(IngestedArtifacts.INGESTED_ARTIFACTS.VERSION.eq(versions.get(i))));
        }
        var result = context
                .select(IngestedArtifacts.INGESTED_ARTIFACTS.PACKAGE_NAME,
                        IngestedArtifacts.INGESTED_ARTIFACTS.VERSION)
                .from(IngestedArtifacts.INGESTED_ARTIFACTS)
                .where(condition)
                .fetch();
        var ingestedArtifacts = new HashSet<Pair<String, String>>(result.size());
        result.forEach(r -> ingestedArtifacts.add(new Pair<>(r.component1(), r.component2())));
        return ingestedArtifacts;
    }

    protected Condition packageVersionWhereClause(String name, String version) {
        return trueCondition()
                .and(Packages.PACKAGES.PACKAGE_NAME.equalIgnoreCase(name))
                .and(PackageVersions.PACKAGE_VERSIONS.VERSION.equalIgnoreCase(version));
    }

    public boolean assertPackageExistence(String name, String version) {
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;

        Record selectPackage = context
                .select(p.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .where(packageVersionWhereClause(name, version))
                .fetchOne();

        return selectPackage != null;
    }

    public boolean assertModulesExistence(String name, String version, String namespace) {
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        ModuleNames n = ModuleNames.MODULE_NAMES;

        Record selectModule = context
                .select(m.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(n).on(m.MODULE_NAME_ID.eq(n.ID))
                .where(packageVersionWhereClause(name, version))
                .and(n.NAME.equalIgnoreCase(namespace))
                .fetchOne();

        return selectModule != null;
    }

    public String getPackageLastVersion(String packageName) {

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        ArtifactRepositories ar = ArtifactRepositories.ARTIFACT_REPOSITORIES;

        // Building and executing the query
        Record queryResult = this.context
                .select(p.fields())
                .select(pv.VERSION, ar.REPOSITORY_BASE_URL)
                .from(p)
                .leftJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .leftJoin(ar).on(pv.ARTIFACT_REPOSITORY_ID.eq(ar.ID))
                .where(p.PACKAGE_NAME.equalIgnoreCase(packageName))
                .orderBy(pv.CREATED_AT.sortDesc().nullsLast())
                .limit(1)
                .fetchOne();

        if (queryResult == null) {
            return null;
        }

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getAllPackages(int offset, int limit) {
        var result = context
                .select(Packages.PACKAGES.fields())
                .from(Packages.PACKAGES)
                .offset(offset)
                .limit(limit)
                .fetch();
        return result.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getPackageVersion(String packageName, String packageVersion) {
        return getPackageInfo(packageName, packageVersion, false);
    }

    public String getPackageMetadata(String packageName, String packageVersion) {
        return getPackageInfo(packageName, packageVersion, true);
    }

    protected String getPackageInfo(String packageName,
                                    String packageVersion,
                                    boolean metadataOnly) {

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        ArtifactRepositories ar = ArtifactRepositories.ARTIFACT_REPOSITORIES;

        // Select clause
        SelectField<?>[] selectClause;
        if (metadataOnly) {
            selectClause = new SelectField[]{p.PACKAGE_NAME, pv.VERSION, pv.METADATA};
        } else {
            selectClause = new SelectField[]{pv.ID, pv.PACKAGE_ID, pv.VERSION, pv.CG_GENERATOR, ar.REPOSITORY_BASE_URL, pv.ARCHITECTURE, pv.METADATA, pv.CREATED_AT};
        }

        // Building and executing the query
        Record queryResult = this.context
                .select(selectClause)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .leftJoin(ar).on(pv.ARTIFACT_REPOSITORY_ID.eq(ar.ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .limit(1)
                .fetchOne();
        if (queryResult == null) {
            return null;
        }
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
    public String getPackageVersions(String packageName, int offset, int limit) {

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
        ArtifactRepositories ar = ArtifactRepositories.ARTIFACT_REPOSITORIES;

        // Query
        var queryResult = context
                .select(pv.ID, pv.PACKAGE_ID, pv.CG_GENERATOR, pv.VERSION, ar.REPOSITORY_BASE_URL, pv.ARCHITECTURE, pv.CREATED_AT)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(ar).on(pv.ARTIFACT_REPOSITORY_ID.eq(ar.ID))
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
    public String getPackageDependencies(String packageName, String packageVersion, int offset, int limit) throws PackageVersionNotFoundException {

        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

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
                                    int offset,
                                    int limit) throws PackageVersionNotFoundException {

        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;

        // Select clause
        SelectField<?>[] selectClause = m.fields();

        // Where clause
        Condition whereClause = packageVersionWhereClause(packageName, packageVersion);

        // Building and executing the query
        Result<Record> queryResult = context
                .select(selectClause)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .where(whereClause)
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getModuleMetadata(String packageName,
                                    String packageVersion,
                                    String moduleNamespace) throws PackageVersionNotFoundException {
        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        ModuleNames n = ModuleNames.MODULE_NAMES;

        // Select clause
        SelectField<?>[] selectClause = new SelectField[]{p.PACKAGE_NAME, pv.VERSION, n.NAME, m.METADATA};

        // Where clause
        Condition whereClause = packageVersionWhereClause(packageName, packageVersion).and(n.NAME.equalIgnoreCase(moduleNamespace));

        // Building and executing the query
        var queryResult = context
                .select(selectClause)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(n).on(m.MODULE_NAME_ID.eq(n.ID))
                .where(whereClause)
                .limit(1)
                .fetchOne();
        if (queryResult == null) {
            return null;
        }
        // Returning the result
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public String getModuleFiles(String packageName,
                                 String packageVersion,
                                 String moduleNamespace,
                                 int offset,
                                 int limit) throws PackageVersionNotFoundException {
        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

        if (!assertModulesExistence(packageName, packageVersion, moduleNamespace)) return null;

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Files f = Files.FILES;
        ModuleNames n = ModuleNames.MODULE_NAMES;

        // Query
        Result<Record> queryResult = context
                .select(f.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(f).on(pv.ID.eq(f.PACKAGE_VERSION_ID))
                .innerJoin(n).on(m.MODULE_NAME_ID.eq(n.ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .and(n.NAME.eq(moduleNamespace))
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
                                     int offset,
                                     int limit) throws PackageVersionNotFoundException {
        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

        if (!assertModulesExistence(packageName, packageVersion, moduleNamespace)) return null;

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Callables c = Callables.CALLABLES;
        ModuleNames n = ModuleNames.MODULE_NAMES;

        // Main Query
        Result<Record> queryResult = context
                .select(c.fields())
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(c).on(m.ID.eq(c.MODULE_ID))
                .innerJoin(n).on(m.MODULE_NAME_ID.eq(n.ID))
                .where(packageVersionWhereClause(packageName, packageVersion))
                .and(n.NAME.eq(moduleNamespace))
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        var res = queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));


        //// Insert user-friendly formatted method signature

        // Parse result json string back into object
        JSONArray json;
        try {
            json = new JSONArray(res);
        } catch (JSONException err) {
            logger.error("Error JSON Parser: " + err.toString());
            return null;
        }

        // Go through each callable, parse fasten uri, insert signature.
        for (Object j : json) {
            JSONObject jObj = (JSONObject) j;
            var uri = jObj.getString("fasten_uri");

            try {
                var uriObject = FastenUriUtils.parsePartialFastenUri(uri);
                jObj.put("method_name", uriObject.get(2));
                jObj.put("method_args", uriObject.get(3));
            } catch (IllegalArgumentException err) {
                logger.warn("Error FASTEN URI Parser: " + err.toString());
            }
        }

        return json.toString();
    }

    public String getPackageBinaryModules(String packageName, String packageVersion, int offset, int limit) throws PackageVersionNotFoundException {
        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        BinaryModules b = BinaryModules.BINARY_MODULES;

        // Select clause
        SelectField<?>[] selectClause = b.fields();

        // Where clause
        Condition whereClause = packageVersionWhereClause(packageName, packageVersion);


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

    public String getBinaryModuleMetadata(String packageName,
                                          String packageVersion,
                                          String binaryModule) throws PackageVersionNotFoundException {
        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        BinaryModules b = BinaryModules.BINARY_MODULES;

        // Select clause
        SelectField<?>[] selectClause = new SelectField[]{p.PACKAGE_NAME, pv.VERSION, b.NAME, b.METADATA};

        // Where clause
        Condition whereClause = packageVersionWhereClause(packageName, packageVersion).and(b.NAME.equalIgnoreCase(binaryModule));

        // Building and executing the query
        var queryResult = context
                .select(selectClause)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(b).on(pv.ID.eq(b.PACKAGE_VERSION_ID))
                .where(whereClause)
                .limit(1)
                .fetchOne();
        if (queryResult == null) {
            return null;
        }
        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    // TODO Test with real DB data
    public String getBinaryModuleFiles(String packageName,
                                       String packageVersion,
                                       String binaryModule,
                                       int offset,
                                       int limit) throws PackageVersionNotFoundException {
        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

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

    public String getPackageCallables(String packageName, String packageVersion, int offset, int limit) throws PackageVersionNotFoundException {

        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Callables c = Callables.CALLABLES;

        // Select clause
        SelectField<?>[] selectClause = c.fields();

        // Where clause
        Condition whereClause = packageVersionWhereClause(packageName, packageVersion);

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

    public List<Long> getPackageInternalCallableIDs(String packageName, String version) {
        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Callables c = Callables.CALLABLES;

        // Building and executing the query
        var result = context
                .select(c.ID)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(c).on(m.ID.eq(c.MODULE_ID))
                .where(packageVersionWhereClause(packageName, version))
                .and(Callables.CALLABLES.IS_INTERNAL_CALL.eq(true))
                .fetch();
        return result.map(Record1::value1);
    }

    public String getCallableMetadata(String packageName,
                                      String packageVersion,
                                      String fastenURI) {
        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Callables c = Callables.CALLABLES;

        // Select clause
        SelectField<?>[] selectClause = new SelectField[]{p.PACKAGE_NAME, pv.VERSION, c.FASTEN_URI, c.METADATA};

        // Where clause
        Condition whereClause = packageVersionWhereClause(packageName, packageVersion).and("digest(callables.fasten_uri, 'sha1') = digest(?, 'sha1')", fastenURI);

        // Building and executing the query
        var queryResult = context
                .select(selectClause)
                .from(p)
                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                .innerJoin(c).on(m.ID.eq(c.MODULE_ID))
                .where(whereClause)
                .limit(1)
                .fetchOne();
        if (queryResult == null) {
            return null;
        }
        // Returning the result
        logger.debug("Total rows: " + queryResult.size());
        return queryResult.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
    }

    public Map<Long, JSONObject> getCallablesMetadata(Collection<Long> callableIDs) {
        var result = context
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.METADATA)
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.ID.in(callableIDs))
                .fetch();
        var map = new HashMap<Long, JSONObject>(result.size());
        for (var record : result) {
            map.put(record.value1(), new JSONObject(record.value2().data()));
        }
        return map;
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
        if (result == null) {
            return null;
        }
        return result.value1() + Constants.mvnCoordinateSeparator + result.value2();
    }

    public Set<Long> findVulnerablePackageVersions(Set<Long> packageVersionIDs) {
        var result = context
                .select(PackageVersions.PACKAGE_VERSIONS.ID)
                .from(PackageVersions.PACKAGE_VERSIONS)
                .where(PackageVersions.PACKAGE_VERSIONS.ID.in(packageVersionIDs))
                .and("package_versions.metadata::jsonb->'vulnerabilities' is not null")
                .fetch();
        return new HashSet<>(result.map(Record1::value1));
    }

    public Map<Long, JSONObject> findVulnerableCallables(Set<Long> vulnerablePackageVersions, Set<Long> callableIDs) {
        var result = context
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.METADATA)
                .from(Callables.CALLABLES)
                .join(Modules.MODULES)
                .on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(Modules.MODULES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID))
                .where(PackageVersions.PACKAGE_VERSIONS.ID.in(vulnerablePackageVersions))
                .and(Callables.CALLABLES.ID.in(callableIDs))
                .and("callables.metadata::jsonb->'vulnerabilities' is not null")
                .fetch();
        var map = new HashMap<Long, JSONObject>(result.size());
        for (var record : result) {
            map.put(record.value1(), new JSONObject(record.value2().data()).getJSONObject("vulnerabilities"));
        }
        return map;
    }

    public String getPackageFiles(String packageName, String packageVersion, int offset, int limit) throws PackageVersionNotFoundException {

        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

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

    public String getPackageCallgraph(String packageName, String packageVersion, int offset, int limit) {
        return getEdgesInfo(packageName, packageVersion, true, offset, limit);
    }

    public String getPackageEdges(String packageName, String packageVersion, int offset, int limit) {
        return getEdgesInfo(packageName, packageVersion, false, offset, limit);
    }

    protected String getEdgesInfo(String packageName,
                                  String packageVersion,
                                  boolean idsOnly,
                                  int offset,
                                  int limit) throws PackageVersionNotFoundException {

        if (!assertPackageExistence(packageName, packageVersion)) {
            throw new PackageVersionNotFoundException(packageName + Constants.mvnCoordinateSeparator + packageVersion);
        }

        // Tables
        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;
        Modules m = Modules.MODULES;
        Callables c = Callables.CALLABLES;
        CallSites e = CallSites.CALL_SITES;

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
        var whereClause = and(CallSites.CALL_SITES.SOURCE_ID.eq(edges.get(0).getFirst()))
                .and(CallSites.CALL_SITES.TARGET_ID.eq(edges.get(0).getSecond()));
        for (int i = 1; i < edges.size(); i++) {
            whereClause = whereClause.or(
                    and(CallSites.CALL_SITES.SOURCE_ID.eq(edges.get(i).getFirst()))
                            .and(CallSites.CALL_SITES.TARGET_ID.eq(edges.get(i).getSecond()))
            );
        }
        var queryResult = context
                .select(CallSites.CALL_SITES.SOURCE_ID, CallSites.CALL_SITES.TARGET_ID, CallSites.CALL_SITES.METADATA)
                .from(CallSites.CALL_SITES)
                .where(whereClause)
                .fetch();
        var metadataMap = new HashMap<Pair<Long, Long>, JSONObject>(queryResult.size());
        for (var record : queryResult) {
            metadataMap.put(new Pair<>(record.value1(), record.value2()), new JSONObject(record.value3().data()));
        }
        return metadataMap;
    }

    public Map<Long, String> getFullFastenUris(List<Long> callableIds) {
        var result = context
                .select(Packages.PACKAGES.FORGE,
                        Packages.PACKAGES.PACKAGE_NAME,
                        PackageVersions.PACKAGE_VERSIONS.VERSION,
                        Callables.CALLABLES.FASTEN_URI,
                        Callables.CALLABLES.ID)
                .from(Packages.PACKAGES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(Packages.PACKAGES.ID.eq(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID))
                .join(Modules.MODULES)
                .on(Modules.MODULES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID))
                .join(Callables.CALLABLES)
                .on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
                .where(Callables.CALLABLES.ID.in(callableIds))
                .fetch();
        var map = new HashMap<Long, String>(result.size());
        result.forEach(r -> map.put(r.value5(), FastenUriUtils.generateFullFastenUri(r.value1(), r.value2(), r.value3(), r.value4())));
        return map;
    }

    public Map<String, JSONObject> getCallablesMetadataByUri(String forge, String packageName, String version, List<String> fastenUris) {
        var conditions = new ArrayList<String>(fastenUris.size());
        for (int i = 0; i < fastenUris.size(); i++) {
            conditions.add("callables.fasten_uri = ?");
        }
        var uriDigestIsInList = String.join(" or ", conditions);
        var result = context
                .select(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.METADATA)
                .from(Callables.CALLABLES)
                .join(Modules.MODULES).on(Modules.MODULES.ID.eq(Callables.CALLABLES.MODULE_ID))
                .join(PackageVersions.PACKAGE_VERSIONS).on(PackageVersions.PACKAGE_VERSIONS.ID.eq(Modules.MODULES.PACKAGE_VERSION_ID))
                .join(Packages.PACKAGES).on(Packages.PACKAGES.ID.eq(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID))
                .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName).and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))
                        .and(uriDigestIsInList, fastenUris.toArray()))
                .fetch();
        if (result.isEmpty()) {
            return null;
        }
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

    public String searchPackageNames(String searchName, int offset, int limit) {

        Packages p = Packages.PACKAGES;
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS;

        // Query
        Result<Record> result = context
                .select(p.fields())
                .from(p)
                .where(
                    exists(context.select(p.ID).from(p).where(p.ID.eq(pv.PACKAGE_ID)).limit(1))
                    .and(p.PACKAGE_NAME.like("%" + searchName + "%"))
                )
                .offset(offset)
                .limit(limit)
                .fetch();

        // Returning the result
        logger.debug("Total rows: " + result.size());
        return result.formatJSON(new JSONFormat().format(true).header(false).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
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
        return queryResult.formatJSON(new JSONFormat().format(true).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
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
        return queryResult.formatJSON(new JSONFormat().format(true).recordFormat(JSONFormat.RecordFormat.OBJECT).quoteNested(false));
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

    public Map<String, Long> insertNamespaces(Collection<String> namespaces) {
        var insert = context.insertInto(ModuleNames.MODULE_NAMES, ModuleNames.MODULE_NAMES.NAME);
        for (var namespace : namespaces) {
            insert = insert.values(namespace);
        }
        var result = insert.onConflictOnConstraint(Keys.UNIQUE_MODULE_NAMES).doUpdate()
                .set(ModuleNames.MODULE_NAMES.NAME, ModuleNames.MODULE_NAMES.as("excluded").NAME)
                .returning(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME)
                .fetch();
        var map = new HashMap<String, Long>(result.size());
        result.forEach(r -> map.put(r.getName(), r.getId()));
        return map;
    }
}
