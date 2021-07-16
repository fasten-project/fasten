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

package eu.fasten.analyzer.qualityanalyzer.data;

import eu.fasten.core.data.metadatadb.codegen.tables.records.CallablesRecord;
import org.json.JSONObject;

public class CallableHolder {

    //Fields in the callables table
    private final Long callableId;
    private final Long moduleId;
    private final String fastenUri;
    private final boolean is_internal_call;
    private final Integer line_start;
    private final Integer line_end;
    private JSONObject callableMetadata;

    public CallableHolder(CallablesRecord cr) {
        callableId = cr.getId();
        moduleId = cr.getModuleId();
        fastenUri = cr.getFastenUri();
        is_internal_call = cr.getIsInternalCall();
        line_start = cr.getLineStart();
        line_end = cr.getLineEnd();
        callableMetadata = new JSONObject(cr.getMetadata().data());
    }

    public Long getCallableId() {
        return callableId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public String getFastenUri(){
        return fastenUri;
    }

    public boolean isInternal() {
        return is_internal_call;
    }

    public Integer getLineStart() {
        return line_start;
    }

    public Integer getLineEnd() {
        return line_end;
    }

    public JSONObject getCallableMetadata() {
        return callableMetadata;
    }

    public void setCallableMetadata(JSONObject metadata) {
        this.callableMetadata = metadata;
    }

}
