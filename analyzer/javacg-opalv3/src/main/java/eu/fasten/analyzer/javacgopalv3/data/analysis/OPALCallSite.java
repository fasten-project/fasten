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

package eu.fasten.analyzer.javacgopalv3.data.analysis;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

public class OPALCallSite {

    private final Integer line;
    private final String type;
    private final String receiver;

    /**
     * Constructs a Call Site from a line, type, and receiver parameters.
     *
     * @param line     lines number
     * @param type     type
     * @param receiver receiver
     */
    public OPALCallSite(Integer line, String type, String receiver) {
        this.line = line;
        this.type = type;
        this.receiver = receiver;
    }

    /**
     * Constructs a Call Site from a String containing line, type, and receiver information.
     *
     * @param callSite String to construct CallSite from
     */
    public OPALCallSite(String callSite) {
        this.line = Integer.valueOf(StringUtils.substringBetween(callSite, "line=", ", type='"));
        this.type = StringUtils.substringBetween(callSite, ", type='", "', receiver='");
        this.receiver = StringUtils.substringBetween(callSite, ", receiver='", "'}");
    }

    /**
     * Constructs a Call Site from a JSONObject containing line, type, and receiver information.
     *
     * @param callSite JSONObject to construct CallSite from
     */
    public OPALCallSite(JSONObject callSite) {
        this.line = callSite.getInt("line");
        this.type = callSite.getString("type");
        this.receiver = callSite.getString("receiver");
    }

    public Integer getLine() {
        return line;
    }

    public String getType() {
        return type;
    }

    public String getReceiver() {
        return receiver;
    }

    /**
     * Checks if any of passed types are of the same type as this CallSite.
     *
     * @param types types to check against
     * @return true if any of types matched this CallSite type, else false
     */
    public boolean is(String... types) {
        for (final var type : types) {
            if (this.type.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts this call site to a map.
     *
     * @return a map representation of this call site
     */
    public Map<String, Object> toMap() {
        var map = new HashMap<String, Object>();
        map.put("line", this.line);
        map.put("type", this.type);
        map.put("receiver", this.receiver);
        return map;
    }

    @Override
    public String toString() {
        return "{line=" + line + ", type='" + type + "', receiver='" + receiver + "'}";
    }
}
