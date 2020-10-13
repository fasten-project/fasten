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

import org.jooq.Record;
import org.json.JSONObject;

public class CallableHolder {

    private JSONObject callableMetadata = null;
    private Integer line_start = null;
    private Integer line_end = null;

    private String fastenUri = null;
    private Long moduleId;
    private boolean is_internal = true;
    private Long callableId = null;

    private String partialType = null;

    public CallableHolder(JSONObject payload) {
        line_start = Integer.parseInt(payload.get(QAConstants.LINE_START).toString());
        line_end = Integer.parseInt(payload.get(QAConstants.LINE_END).toString());
        partialType = payload.get(QAConstants.RETURN_TYPE).toString();
    }

    public JSONObject getCallableMetadata() {
        return callableMetadata;
    }

    public Integer getLine_start() {
        return line_start;
    }

    public Integer getLine_end() {
        return line_end;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public boolean isInternal() {
        return is_internal;
    }

    public String getFastenUri(){
        return fastenUri;
    }

    //JSON object contains partial return type
    public String getPartialType() {
        return partialType;
    }

    public void setValues(Record cr) {
        callableId = (Long) cr.get(0);
        moduleId = (Long) cr.get(1);
        fastenUri = (String) cr.get(2);
        is_internal = (boolean) cr.get(3);
    }
}
