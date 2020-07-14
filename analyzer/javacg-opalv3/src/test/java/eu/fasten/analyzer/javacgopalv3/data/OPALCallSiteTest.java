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

package eu.fasten.analyzer.javacgopalv3.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.fasten.analyzer.javacgopalv3.data.analysis.OPALCallSite;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OPALCallSiteTest {

    @Test
    void testConstructFromParameters() {
        var callSite = new OPALCallSite(10, "a", "b");

        assertEquals(10, callSite.getLine());
        assertEquals("a", callSite.getType());
        assertEquals("b", callSite.getReceiver());
    }

    @Test
    void testConstructFromString() {
        var callSite = new OPALCallSite("{line=10, type='a', receiver='b'}");

        assertEquals(10, callSite.getLine());
        assertEquals("a", callSite.getType());
        assertEquals("b", callSite.getReceiver());
    }

    @Test
    void testConstructFromJSON() {
        var json = new JSONObject();
        json.put("line", 10);
        json.put("type", "a");
        json.put("receiver", "b");

        var callSite = new OPALCallSite(json);

        assertEquals(10, callSite.getLine());
        assertEquals("a", callSite.getType());
        assertEquals("b", callSite.getReceiver());
    }

    @Test
    void is() {
        var callSite = new OPALCallSite(10, "a", "b");

        assertFalse(callSite.is("c", "d", "e"));
        assertTrue(callSite.is("a"));
        assertTrue(callSite.is("c", "a", "d"));
    }

    @Test
    void testToString() {
        var callSite = new OPALCallSite(10, "a", "b");

        assertEquals("{line=10, type='a', receiver='b'}", callSite.toString());
    }
}