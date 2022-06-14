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

import com.github.t9t.jooq.json.JsonbDSL;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Files;
import eu.fasten.core.data.metadatadb.codegen.tables.ModuleContents;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.exception.TooManyRowsException;
import org.jooq.impl.DSL;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static eu.fasten.analyzer.qualityanalyzer.data.QAConstants.QA_CALLABLE_START_END_LINE_TOLERANCE;
import static eu.fasten.analyzer.qualityanalyzer.data.QAConstants.QA_VERSION_NUMBER;

public class MetadataUtils {

    private final Logger logger = LoggerFactory.getLogger(MetadataUtils.class.getName());
    private final Map<String, DSLContext> dslContexts;
    public MetadataUtils(Map<String, DSLContext> contexts) {
        this.dslContexts = contexts;
    }

    public void processJsonRecord(JSONObject jsonRecord) throws IllegalStateException {
        String rapidVersion = jsonRecord.getString("plugin_version");
        String forge;
        String packageName;
        String packageVersion;
        JSONObject payload;

        if (jsonRecord.has("payload")) {
            payload = jsonRecord.getJSONObject("payload");
            forge = payload.getString("forge");
            packageName = payload.getString("product");
            packageVersion = payload.getString("version");
        } else {
            var message = "JSON record does not contain payload field: " + jsonRecord;
            logger.error(message);
            throw new IllegalStateException(message);
        }

        if( (forge == null) || (packageName == null) || (packageVersion == null)) {
            var message = "Forge, product or version is missing in payload: " + jsonRecord;
            logger.error(message);
            throw new IllegalStateException(message);
        }

        String path = payload.getString("filename");
        int startLine = payload.getInt("start_line");
        int endLine = payload.getInt("end_line");
        String methodName = getMethodName(payload);
        JSONObject metadata = getQualityMetadata(payload, rapidVersion);
        updateCallableMetadata(forge, packageName, packageVersion, methodName, path, startLine, endLine, metadata);
    }

    private String getMethodName(JSONObject payload) {
        String separator = ":";
        String callableName = payload.getString("callable_name");
        int position = callableName.lastIndexOf(separator);
        return callableName.substring(position+separator.length());
    }

    private JSONObject getQualityMetadata(JSONObject payload, String rapidVersion) {
        JSONObject quality = new JSONObject(payload, new String[] {
                "quality_analyzer_name",
                "quality_analyzer_version",
                "quality_analysis_timestamp",
                "callable_name",
                "callable_long_name",
                "callable_parameters",
                "metrics"});
        quality.put("rapid_plugin_version", rapidVersion);
        quality.put("rapid_metadata_plugin_version", QA_VERSION_NUMBER);
        var metadata = new JSONObject();
        metadata.put("quality", quality);
        return metadata;
    }

    public void updateCallableMetadata(String forge, String packageName, String packageVersion, String methodName, String path, int lineStart, int lineEnd, JSONObject metadata) {
        var context = dslContexts.get(forge);
        context.transaction(configuration -> {
            Record1<Long> result;
            try {
                result = DSL.using(configuration).update(Callables.CALLABLES)
                        .set(Callables.CALLABLES.METADATA, JsonbDSL.concat(Callables.CALLABLES.METADATA, JsonbDSL.field(metadata.toString())))
                        .from(Packages.PACKAGES, PackageVersions.PACKAGE_VERSIONS, Files.FILES, ModuleContents.MODULE_CONTENTS, Modules.MODULES)
                        .where(Packages.PACKAGES.FORGE.equal(forge))
                        .and(Packages.PACKAGES.PACKAGE_NAME.equal(packageName))
                        .and(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.equal(Packages.PACKAGES.ID))
                        .and(PackageVersions.PACKAGE_VERSIONS.VERSION.equal(packageVersion))
                        .and(Files.FILES.PACKAGE_VERSION_ID.equal(PackageVersions.PACKAGE_VERSIONS.ID))
                        .and(Files.FILES.PATH.equal(path))
                        .and(ModuleContents.MODULE_CONTENTS.FILE_ID.equal(Files.FILES.ID))
                        .and(Modules.MODULES.ID.equal(ModuleContents.MODULE_CONTENTS.MODULE_ID))
                        .and(Callables.CALLABLES.MODULE_ID.equal(Modules.MODULES.ID))
                        .and(Callables.CALLABLES.LINE_START.between(
                                lineStart - QA_CALLABLE_START_END_LINE_TOLERANCE,
                                lineStart + QA_CALLABLE_START_END_LINE_TOLERANCE))
                        .and(Callables.CALLABLES.LINE_END.between(
                                lineEnd - QA_CALLABLE_START_END_LINE_TOLERANCE,
                                lineEnd + QA_CALLABLE_START_END_LINE_TOLERANCE))
                        .and(Callables.CALLABLES.FASTEN_URI.contains(methodName))
                        .returningResult(Callables.CALLABLES.ID)
                        .fetchOne();
            }
            catch(TooManyRowsException e) {
                var errorMessage = "Error: more than one callable matched.";
                logger.info(errorMessage);
                throw new IllegalStateException(errorMessage);
            }

            if(result == null) {
                throw new IllegalStateException("Error: no callables matched.");
            }
        });
    }
}
