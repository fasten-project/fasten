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

import eu.fasten.analyzer.qualityanalyzer.data.*;

import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;

import org.json.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Vector;

public class MetadataUtils {

    private HashMap<String, DSLContext> contexts;
    private final Logger logger = LoggerFactory.getLogger(MetadataUtils.class.getName());

    private String product = null;
    private String artifact = null;
    private String group = null;
    private String version = null;

    private JSONObject metrics = null;

    private boolean restartTransaction = false;
    private boolean processedRecord = false;

    DSLContext selectedContext = null;

    public MetadataUtils(HashMap<String, DSLContext> contexts) {
        this.contexts = contexts;
    }

    public void insertMetadataIntoDB(String forge, JSONObject jsonRecord) {

        this.processedRecord = false;
        this.restartTransaction = false;

        var payload = jsonRecord.getJSONObject("payload");

        selectedContext = contexts.get(forge);

        switch(forge) {
            case QAConstants.MVN_FORGE:
                insertMvnMetadataIntoDB(payload);
                break;
            case QAConstants.PyPI_FORGE:
                insertPyPIMetadataIntoDB(payload);
                break;
            case QAConstants.C_FORGE:
                insertDebianMetadataIntoDB(payload);
                break;
            default:
                logger.error("Forge type is not defined!");
        }
    }

        // Inject all the info at the package-level and store packageVersionsId
    private void insertMvnMetadataIntoDB(JSONObject payload) {

        CallableHolder callableHolder = new CallableHolder(payload);

        var startLine = callableHolder.getLine_start();
        var endLine = callableHolder.getLine_end();
        var retType = callableHolder.getPartialType();

        Result<Record> crs = selectedContext.select()
                .from(Callables.CALLABLES)
                .where(Callables.CALLABLES.LINE_START.equal(startLine))
                .and(Callables.CALLABLES.LINE_END.equal(endLine))
                .and(Callables.CALLABLES.FASTEN_URI.contains(retType))
                .fetch();

        //TODO: we need a unique record here? Not, if the method is the same, the quality params are the same?

        Vector<CallableHolder> callables = new Vector<CallableHolder>();

        for (Record cr : crs) {
            callableHolder.setValues(cr);
            callables.add(callableHolder);
        }

        logger.info("Found " + callables.size() + " methods for which startLine= " + startLine + " and endLine= " + endLine);

        var metadataDao = new MetadataDao(selectedContext);
        for(CallableHolder callable : callables){
            metadataDao.updateCallableMetadata(callable.getModuleId(), callable.getFastenUri(), callable.isInternal(), callable.getCallableMetadata());
        }
    }


    private void insertDebianMetadataIntoDB(JSONObject jsonRecord) {
        //TODO: check whether this insertion is the same as insertMvnMetadataIntoDB
        this.insertMvnMetadataIntoDB(jsonRecord);

    }

    private void insertPyPIMetadataIntoDB(JSONObject jsonRecord) {
        //TODO: check whether this insertion is the same as insertMvnMetadataIntoDB
        this.insertMvnMetadataIntoDB(jsonRecord);
    }
}
