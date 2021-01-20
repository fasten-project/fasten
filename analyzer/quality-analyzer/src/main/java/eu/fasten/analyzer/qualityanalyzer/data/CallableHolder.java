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

import org.jooq.JSONB;
import org.jooq.Record;
import org.json.JSONObject;

public class CallableHolder {

    //Fields in the callables table
    private Long callableId = null;
    private Long moduleId = null;
    private String fastenUri = null;
    private boolean is_internal_call = false;
    private Long createdAt = null;
    private Integer line_start = null;
    private Integer line_end = null;
    private JSONObject callableMetadata = null;

    public CallableHolder(Record dbRecord) {

        callableId = (Long) dbRecord.get(0);
        moduleId = (Long) dbRecord.get(1);
        fastenUri = (String) dbRecord.get(2);
        is_internal_call = (boolean) dbRecord.get(3);

        createdAt = (Long) dbRecord.get(4);
        line_start = (Integer) dbRecord.get(5);
        line_end = (Integer) dbRecord.get(6);

        JSONB metadata = (JSONB) dbRecord.get(7);
        callableMetadata = new JSONObject(metadata.data());

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

    public Long getCreatedAt() {
        return createdAt;
    }

    public Integer getLine_start() {
        return line_start;
    }

    public Integer getLine_end() {
        return line_end;
    }

    public JSONObject getCallableMetadata() {
        return callableMetadata;
    }

    public void setCallableMetadata(JSONObject metadata) {
        this.callableMetadata = metadata;
    }

}
