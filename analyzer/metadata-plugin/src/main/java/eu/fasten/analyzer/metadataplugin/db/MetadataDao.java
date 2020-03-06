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

import eu.fasten.analyzer.metadataplugin.db.codegen.tables.Callables;
import eu.fasten.analyzer.metadataplugin.db.codegen.tables.Dependencies;
import eu.fasten.analyzer.metadataplugin.db.codegen.tables.Edges;
import eu.fasten.analyzer.metadataplugin.db.codegen.tables.Files;
import eu.fasten.analyzer.metadataplugin.db.codegen.tables.PackageVersions;
import eu.fasten.analyzer.metadataplugin.db.codegen.tables.Packages;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.json.JSONObject;

public class MetadataDao {

    private final DSLContext context;

    public MetadataDao(DSLContext context) {
        this.context = context;
    }

    /**
     * Inserts a record in 'packages' table in the database.
     *
     * @param packageName Name of the package
     * @param forge Forge of the package
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
                .returning(Packages.PACKAGES.ID).fetchOne();
        return resultRecord.getValue(Packages.PACKAGES.ID);
    }

    /**
     * Inserts multiple records in the 'packages' table in the database.
     *
     * @param packageNames List of names of the packages
     * @param forges List of forges of the packages
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
     * @param packageId    ID of the package (references 'package_versions.id')
     * @param dependencyId ID of the dependency package (references 'packages.id')
     * @param versionRange Range of valid versions
     * @return ID of the package (packageId)
     */
    public long insertDependency(long packageId, long dependencyId, String versionRange) {
        var resultRecord = context.insertInto(Dependencies.DEPENDENCIES,
                Dependencies.DEPENDENCIES.PACKAGE_ID, Dependencies.DEPENDENCIES.DEPENDENCY_ID,
                Dependencies.DEPENDENCIES.VERSION_RANGE)
                .values(packageId, dependencyId, versionRange)
                .returning(Dependencies.DEPENDENCIES.PACKAGE_ID).fetchOne();
        return resultRecord.getValue(Dependencies.DEPENDENCIES.PACKAGE_ID);
    }

    /**
     * Inserts multiple 'dependencies' int the database for certain package.
     *
     * @param packageId       ID of the package
     * @param dependenciesIds List of IDs of dependencies
     * @param versionRanges   List of version ranges
     * @return ID of the package (packageId)
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public long insertDependencies(long packageId, List<Long> dependenciesIds,
                                   List<String> versionRanges)
            throws IllegalArgumentException {
        if (dependenciesIds.size() != versionRanges.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = dependenciesIds.size();
        for (int i = 0; i < length; i++) {
            insertDependency(packageId, dependenciesIds.get(i), versionRanges.get(i));
        }
        return packageId;
    }

    /**
     * Inserts a record in  'files' table in the database.
     *
     * @param packageId  ID of the package version where the file belongs
     *                   (references 'package_versions.id')
     * @param namespaces Namespaces of the file
     * @param sha256     SHA256 of the file
     * @param createdAt  Timestamp when the file was created
     * @param metadata   Metadata of the file
     * @return ID of the new record
     */
    public long insertFile(long packageId, String namespaces, byte[] sha256, Timestamp createdAt,
                           JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Files.FILES,
                Files.FILES.PACKAGE_ID, Files.FILES.NAMESPACES, Files.FILES.SHA256,
                Files.FILES.CREATED_AT, Files.FILES.METADATA)
                .values(packageId, namespaces, sha256, createdAt, metadataJsonb)
                .returning(Files.FILES.ID).fetchOne();
        return resultRecord.getValue(Files.FILES.ID);
    }

    /**
     * Inserts multiple records in the 'files' table in the database.
     *
     * @param packageId      ID of the common package
     * @param namespacesList List of namespaces
     * @param sha256s        List of SHA256s
     * @param createdAt      List of timestamps
     * @param metadata       List of metadata objects
     * @return List of IDs of new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertFiles(long packageId, List<String> namespacesList,
                                  List<byte[]> sha256s, List<Timestamp> createdAt,
                                  List<JSONObject> metadata) throws IllegalArgumentException {
        if (namespacesList.size() != sha256s.size() || sha256s.size() != createdAt.size()
                || createdAt.size() != metadata.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = namespacesList.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertFile(packageId, namespacesList.get(i), sha256s.get(i),
                    createdAt.get(i), metadata.get(i));
            recordIds.add(result);
        }
        return recordIds;
    }

    /**
     * Inserts a record in the 'callables' table in the database.
     *
     * @param fileId    ID of the file where the callable belongs (references 'files.id')
     * @param fastenUri URI of the callable in FASTEN
     * @param isResolvedCall 'true' if call is resolved, 'false' otherwise
     * @param createdAt Timestamp when the callable was created
     * @param metadata  Metadata of the callable
     * @return ID of the new record
     */
    public long insertCallable(long fileId, String fastenUri, boolean isResolvedCall,
                               Timestamp createdAt, JSONObject metadata) {
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Callables.CALLABLES,
                Callables.CALLABLES.FILE_ID, Callables.CALLABLES.FASTEN_URI,
                Callables.CALLABLES.IS_RESOLVED_CALL, Callables.CALLABLES.CREATED_AT,
                Callables.CALLABLES.METADATA)
                .values(fileId, fastenUri, isResolvedCall, createdAt, metadataJsonb)
                .returning(Callables.CALLABLES.ID).fetchOne();
        return resultRecord.getValue(Callables.CALLABLES.ID);
    }

    /**
     * Inserts multiple records in the 'callables' table in the database.
     *
     * @param fileId     ID of the common file
     * @param fastenUris List of FASTEN URIs
     * @param areResolvedCalls List of IsResolvedCall booleans
     * @param createdAt  List of timestamps
     * @param metadata   List of metadata objects
     * @return List of IDs of the new records
     * @throws IllegalArgumentException if lists are not of the same size
     */
    public List<Long> insertCallables(long fileId, List<String> fastenUris,
                                      List<Boolean> areResolvedCalls, List<Timestamp> createdAt,
                                      List<JSONObject> metadata) throws IllegalArgumentException {
        if (fastenUris.size() != areResolvedCalls.size()
                || areResolvedCalls.size() != metadata.size()
                || metadata.size() != createdAt.size()) {
            throw new IllegalArgumentException("All lists should have equal size");
        }
        int length = fastenUris.size();
        var recordIds = new ArrayList<Long>(length);
        for (int i = 0; i < length; i++) {
            long result = insertCallable(fileId, fastenUris.get(i),
                    areResolvedCalls.get(i), createdAt.get(i), metadata.get(i));
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
        var metadataJsonb = metadata != null ? JSONB.valueOf(metadata.toString()) : null;
        var resultRecord = context.insertInto(Edges.EDGES,
                Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.METADATA)
                .values(sourceId, targetId, metadataJsonb)
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
}