/*
 * Copyright 2021 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.core.vulchains;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.vulnerability.Vulnerability;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

public class VulnerableCallChainTest {

    private static VulnerableCallChain sut1;
    private static VulnerableCallChain sut;
    private static VulnerableCallChain sut1Prime;

    @BeforeAll
    public static void setUp() {
        sut = new VulnerableCallChain(new LinkedList<>(), new LinkedList<>());
        sut1 = new VulnerableCallChain(List.of(new Vulnerability("1234")),
            List.of(FastenURI.create("fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType")));
        sut1Prime = new VulnerableCallChain(List.of(new Vulnerability("1234")),
            List.of(FastenURI.create("fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType")));
    }

    @Test
    void testEquals() {
        Assertions.assertEquals(sut1, sut1Prime);
    }

    @Test
    void testNotEquals() {
        Assertions.assertNotEquals(sut, sut1);
    }

    @Test
    void testNotSame() {
        Assertions.assertNotSame(sut1, sut1Prime);
    }

    @Test
    void testHashCode() {
        Assertions.assertEquals(sut1.hashCode(), sut1Prime.hashCode());
        Assertions.assertNotEquals(sut.hashCode(), sut1.hashCode());
    }

    @Test
    void testToString() {
        var sutJson = new JSONObject(sut.toString());
        Assertions.assertTrue(sutJson.has("chain"));
        Assertions.assertEquals(0, sutJson.getJSONArray("chain").length());
        Assertions.assertTrue(sutJson.has("vulnerabilities"));
        Assertions.assertEquals(0, sutJson.getJSONArray("vulnerabilities").length());

        var sut1Json = new JSONObject(sut1.toString());
        Assertions.assertTrue(sut1Json.has("chain"));
        var chain = new JSONArray();
        chain.put("fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType");
        Assertions.assertEquals(chain.toString(), sut1Json.getJSONArray("chain").toString());
        Assertions.assertTrue(sut1Json.has("vulnerabilities"));
    }
}
