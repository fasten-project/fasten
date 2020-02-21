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

import eu.fasten.analyzer.metadataplugin.db.tables.Callables;
import eu.fasten.analyzer.metadataplugin.db.tables.Dependencies;
import eu.fasten.analyzer.metadataplugin.db.tables.Files;
import eu.fasten.analyzer.metadataplugin.db.tables.PackageVersions;
import eu.fasten.analyzer.metadataplugin.db.tables.Packages;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.json.JSONObject;

import java.sql.Timestamp;

public class MetadataDao {

    private final DSLContext context;

    public MetadataDao(DSLContext context) {
        this.context = context;
    }

    /**
     * Inserts a record in 'packages' table in the database.
     *
     * @param packageName Name of the package
     * @param projectName Project name to which package belongs
     * @param repository  Repository to which package belongs
     * @param createdAt   Timestamp when package was created
     * @return ID of the new record
     */
    public long insertPackage(String packageName, String projectName, String repository, Timestamp createdAt) {
        Record resultRecord = context.insertInto(Packages.PACKAGES,
                Packages.PACKAGES.PACKAGE_NAME, Packages.PACKAGES.PROJECT_NAME, Packages.PACKAGES.REPOSITORY, Packages.PACKAGES.CREATED_AT)
                .values(packageName, projectName, repository, createdAt)
                .returning(Packages.PACKAGES.ID).fetchOne();
        return resultRecord.getValue(Packages.PACKAGES.ID);
    }

    /**
     * Inserts a record in 'package_versions' table in the database.
     *
     * @param packageId ID of the package (references 'packages.id')
     * @param version   Version of the package
     * @param createdAt Timestamp when the package version was created
     * @param metadata  Metadata of the package version
     * @return ID of the new record
     */
    public long insertPackageVersion(long packageId, String version, Timestamp createdAt, JSONObject metadata) {
        JSONB metadataJsonb = JSONB.valueOf(metadata.toString());
        Record resultRecord = context.insertInto(PackageVersions.PACKAGE_VERSIONS,
                PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID, PackageVersions.PACKAGE_VERSIONS.VERSION,
                PackageVersions.PACKAGE_VERSIONS.CREATED_AT, PackageVersions.PACKAGE_VERSIONS.METADATA)
                .values(packageId, version, createdAt, metadataJsonb)
                .returning(PackageVersions.PACKAGE_VERSIONS.ID).fetchOne();
        return resultRecord.getValue(PackageVersions.PACKAGE_VERSIONS.ID);
    }

    /**
     * Inserts a record in the 'dependencies' table in the database.
     *
     * @param packageId    ID of the package (references 'package_versions.id')
     * @param dependencyId ID of the dependency package (references 'package_versions.id')
     * @param versionRange Range of valid versions
     * @return ID of the package (packageId)
     */
    public long insertDependency(long packageId, long dependencyId, String versionRange) {
        Record resultRecord = context.insertInto(Dependencies.DEPENDENCIES,
                Dependencies.DEPENDENCIES.PACKAGE_ID, Dependencies.DEPENDENCIES.DEPENDENCY_ID, Dependencies.DEPENDENCIES.VERSION_RANGE)
                .values(packageId, dependencyId, versionRange)
                .returning(Dependencies.DEPENDENCIES.PACKAGE_ID).fetchOne();
        return resultRecord.getValue(Dependencies.DEPENDENCIES.PACKAGE_ID);
    }

    /**
     * Inserts a record in the 'files' table in the database.
     *
     * @param packageId  ID of the package (version) where the file belongs (references 'package_versions.id')
     * @param namespaces Namespaces of the file
     * @param sha256     SHA256 of the file
     * @param createdAt  Timestamp when the file was created
     * @param metadata   Metadata of the file
     * @return ID of the new record
     */
    public long insertFile(long packageId, String namespaces, byte[] sha256, Timestamp createdAt, JSONObject metadata) {
        JSONB metadataJsonb = JSONB.valueOf(metadata.toString());
        Record resultRecord = context.insertInto(Files.FILES,
                Files.FILES.PACKAGE_ID, Files.FILES.NAMESPACES, Files.FILES.SHA256, Files.FILES.CREATED_AT, Files.FILES.METADATA)
                .values(packageId, namespaces, sha256, createdAt, metadataJsonb)
                .returning(Files.FILES.ID).fetchOne();
        return resultRecord.getValue(Files.FILES.ID);
    }

    /**
     * Inserts a record in the 'callables' table in the database.
     *
     * @param fileId    ID of the file where the callable belongs (references 'files.id')
     * @param fastenUri URI of the callable in FASTEN
     * @param createdAt Timestamp when the callable was created
     * @param metadata  Metadata of the callable
     * @return ID of the new record
     */
    public long insertCallable(long fileId, String fastenUri, Timestamp createdAt, JSONObject metadata) {
        JSONB metadataJsonb = JSONB.valueOf(metadata.toString());
        Record resultRecord = context.insertInto(Callables.CALLABLES,
                Callables.CALLABLES.FILE_ID, Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.CREATED_AT, Callables.CALLABLES.METADATA)
                .values(fileId, fastenUri, createdAt, metadataJsonb)
                .returning(Callables.CALLABLES.ID).fetchOne();
        return resultRecord.getValue(Callables.CALLABLES.ID);
    }
}