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
package eu.fasten.analyzer.qualityanalyzer;

import eu.fasten.analyzer.qualityanalyzer.data.CallableHolder;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Files;
import eu.fasten.core.data.metadatadb.codegen.tables.ModuleContents;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.FilesRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.ModuleContentsRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.PackageVersionsRecord;
import eu.fasten.core.data.metadatadb.codegen.tables.records.PackagesRecord;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.impl.DSL;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MetadataUtils {

    private final Logger logger = LoggerFactory.getLogger(MetadataUtils.class.getName());
    private final Map<String, DSLContext> dslContexts;
    private DSLContext selectedContext = null;
    private Long recordId = null;
    public MetadataUtils(Map<String, DSLContext> contexts) {
        this.dslContexts = contexts;
    }

    /**
     *
     * @param forge         String which could have value MVN, PyPI or C.
     * @param jsonRecord    Object that contains quality analysis metadata.
     * @return              id of the callable record
     */
    public Long updateMetadataInDB(String forge, JSONObject jsonRecord) throws RuntimeException {

        selectedContext = dslContexts.get(forge);

        //could return an empty List
        List<CallableHolder> callableHolderList = getCallables(forge, jsonRecord);

        if(callableHolderList.isEmpty() ) {
            throw new IllegalStateException("Empty list of callables");
        }

        var metadataDao = new MetadataDao(selectedContext);
        selectedContext.transaction(transaction -> {
            // Start transaction
            metadataDao.setContext(DSL.using(transaction));
            for (CallableHolder callable : callableHolderList) {
                recordId = metadataDao.updateCallableMetadata(callable.getModuleId(), callable.getFastenUri(), callable.isInternal(), callable.getCallableMetadata());
            }
        });

        return recordId;
    }

    private List<CallableHolder> getCallables(String forge, JSONObject jsonRecord) throws IllegalStateException {

        //1. get package and version
        //2. get packageversionid
        //3. getfileid
        //4. getmoduleid
        //5. get callables for module id
        //6. filter callables that contain "good" values for start and end line

        String product;
        String version;
        String rapid_version = jsonRecord.getString("plugin_version");
        JSONObject payload;

        if (jsonRecord.has("payload")) {
            payload = jsonRecord.getJSONObject("payload");
            product = payload.getString("product");
            version = payload.getString("version");
        } else {
            logger.error("JSON record does not contain payload");
            throw new IllegalStateException("Non-existing payload in JSON record");
        }

        if( (product == null) || (version == null)) {
            logger.error("Product or version are null");
            throw new IllegalStateException("Product or version are null");
        }

        Long pckVersionId = getPackageVersionId(product, forge, version);

        if( pckVersionId == null ) {
            logger.error("Could not fetch package version id for product = " + product +
                    " forge = " + forge + " version = " + version);
            throw new IllegalStateException("Could not find package version id");
        }

        String filename = payload.getString("filename");
        logger.info("Filename from RapidPlugin is " + filename);

        int lineStart = payload.getInt("start_line");
        int lineEnd = payload.getInt("end_line");

        Long fileId = getFileId(pckVersionId, filename);

        if(fileId == null ) {
            logger.error("Could not fetch fileID for package version id = " + pckVersionId +
                    " and filename = " + filename);
            throw new IllegalStateException("Could not find file id");
        }

        JSONObject tailored = new JSONObject(payload, new String[] {
                "quality_analyzer_name",
                "quality_analyzer_version",
                "quality_analysis_timestamp",
                "callable_name",
                "callable_long_name",
                "callable_parameters",
                "metrics"});
        tailored.put("rapid_plugin_version", rapid_version);

        JSONObject metadata = new JSONObject();
        metadata.put("quality", tailored);

        List<Long> modulesId = getModuleIds(fileId);
        logger.info("Found " + modulesId.size() + " modules");

        ArrayList<CallableHolder> callables = new ArrayList<>();

        String qaCallableName = payload.getString("callable_name");

        if(!modulesId.isEmpty()) {
            for(Long moduleId : modulesId) {
                logger.info("Found module with moduleId: " + moduleId);
                callables.addAll(getCallablesInformation(moduleId, qaCallableName, lineStart, lineEnd, metadata));
            }
            logger.info("Found " + callables.size() + " methods for which startLine and endline are in [" + lineStart + ", " + lineEnd + "]");
            logger.info("Callables details ###");
            for(CallableHolder callable : callables ){
                logger.info(" CallableId = " + callable.getCallableId() + ", and fastenURI = " + callable.getFastenUri());
            }
            logger.info("End of callables details ###");
        }
        return callables;
    }

    /**
     * Retrieves the package_version_id given the purl of the package version.
     * @return negative if it cannot be found
     */
    private Long getPackageVersionId(String coordinate, String forge, String version) {

        logger.info("Looking for package_version_id of " + coordinate);

        Long packageId = getPackageIdFromCoordinate(coordinate, forge);

        if (packageId != null) {
            return getPackageVersionIdFromVersion(packageId, version);
        }

        return null;
    }

    /**
     * Finds the ID of the package given coordinate and forge.
     * This is ecosystem agnostic
     * @param coordinate - includes information about the package
     * @param forge - ['mvn', 'PyPI', 'Debian']
     * @return - Value of the package ID if found, null otherwise.
     */
    private Long getPackageIdFromCoordinate(String coordinate, String forge) {

        logger.info("getPackageIdFromCoordinate: coordinate = " + coordinate + ", forge = " + forge);

        PackagesRecord record = selectedContext.selectFrom(Packages.PACKAGES)
                .where(Packages.PACKAGES.PACKAGE_NAME.equal(coordinate))
                .and(Packages.PACKAGES.FORGE.equal(forge))
                .fetchOne();

        if (record != null) {
            return record.getId();
        }

        return null;
    }

    /**
     * Finds the ID of the package_version in all the version of the package.
     * @param pkgId - ID of the package
     * @param version - String of the version of the package_version
     * @return - Value of the package_version if found, -1 otherwise.
     */
    private Long getPackageVersionIdFromVersion(Long pkgId, String version) {

        // Find the package version record
        PackageVersionsRecord pkgVersionRecord = selectedContext.selectFrom(PackageVersions.PACKAGE_VERSIONS)
                .where(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.equal(pkgId))
                .and(PackageVersions.PACKAGE_VERSIONS.VERSION.equal(version))
                .fetchOne();

        if(pkgVersionRecord != null) {
            return pkgVersionRecord.getId();
        }

        //otherwise return null, -1 is "reserved" for external callable reference
        return null;

    }
    /**
     * Retrieve the fileId of the file
     * @param packageVersionId - package version ID
     * @param filename - path to the file
     * @return - Long value of fileId or -1 if the file cannot be found
     */
    private Long getFileId(Long packageVersionId, String filename) {
        FilesRecord fr = selectedContext.selectFrom(Files.FILES)
                .where(Files.FILES.PACKAGE_VERSION_ID.equal(packageVersionId))
                .and(Files.FILES.PATH.equal(filename))
                .fetchOne();

        if (fr != null) {
            return fr.getId();
        }

        return null;
    }

    /**
     * Gets the moduleId that corresponds to the file.
     * @param fileId - Long fileId
     * @return list of module Ids
     */
    public List<Long> getModuleIds(Long fileId) {
        List<Long> moduleIds = new ArrayList<>();
        Result<ModuleContentsRecord> mcrs = selectedContext.selectFrom(ModuleContents.MODULE_CONTENTS)
                .where(ModuleContents.MODULE_CONTENTS.FILE_ID.equal(fileId))
                .fetch();

        if(mcrs.isNotEmpty()) {
            for (ModuleContentsRecord mcr : mcrs) {
                moduleIds.add(mcr.getModuleId());
            }
        }

        return moduleIds; //we cannot return-1 here since that implies external callable
    }

    /**
     * Retrieves the callables information from the DB with a given values for the start and end line.
     *
     * @param moduleId - Long ID of the file where the callable belongs.
     * @param qaName - String that represents callable name from Lizard tool.
     * @param lineStart - int value that indicates start callable line in source file from Lizard tool.
     * @param lineEnd - int value that indicates the last callable line in source file from Lizard tool.
     * @param metadata - JSON object that contains callable metadata, to be stored in Metadata DB.
     *
     * @return List of CallableHolder (empty List if no callable could be found)
     */
    private List<CallableHolder> getCallablesInformation(Long moduleId, String qaName, int lineStart, int lineEnd, JSONObject metadata)  {

        List<CallableHolder> calls = new ArrayList<>();

        /*
         Get all the records with the moduleId given, with callable name, and
         for which there is an overlap between line intervals from Lizard
         and OPAL.
        */

        Result<CallablesRecord> crs = selectedContext.selectFrom(Callables.CALLABLES)
                .where(Callables.CALLABLES.MODULE_ID.equal(moduleId))
                .andNot(Callables.CALLABLES.LINE_START.gt(lineEnd))
                .andNot(Callables.CALLABLES.LINE_END.lt(lineStart))
                .fetch();

        logger.info("Fetched " + crs.size() + " entries from callables table");

        for (CallablesRecord cr : crs) {
            String fastenUri = cr.getFastenUri();

            String separator = ":";
            int position = qaName.lastIndexOf(separator);
            String methodName = qaName.substring(position+separator.length());

            logger.info("fastenUri (from DB) is " + fastenUri);
            logger.info("Lizard callable name is " + qaName + " parsed methodName is " + methodName);

            if(fastenUri.contains(methodName)) {
                // Create callable object
                CallableHolder ch = new CallableHolder(cr);
                ch.setCallableMetadata(metadata);
                //filter and store callable only if start and end line overlap with input
                calls.add(ch);
            }
        }
        return calls;
    }
}
