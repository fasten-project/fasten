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
import java.util.LinkedList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VulnerableCallChainJsonUtilsTest {

    @Test
    public void basicDataStructure() {
        final var input =
            FastenURI.create("fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType");

        String json = VulnerableCallChainJsonUtils.toJson(input);
        FastenURI output = VulnerableCallChainJsonUtils.fromJson(json, FastenURI.class);

        Assertions.assertNotSame(input, output);
        Assertions.assertEquals(input, output);
    }

    @Test
    public void emptyCase() {
        var vc = new VulnerableCallChain(new LinkedList<>(), new LinkedList<>());

        var actual = VulnerableCallChainJsonUtils.toJson(vc);
        var expected = "{\"vulnerabilities\":[],\"chain\":[]}";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void serializationRoundtrip() {
        var input = new VulnerableCallChain(new LinkedList<>(), new LinkedList<>());

        var json = VulnerableCallChainJsonUtils.toJson(input);
        var output = VulnerableCallChainJsonUtils.fromJson(json, VulnerableCallChain.class);
        Assertions.assertEquals(input, output);
    }
    @Test
    public void nestingTypesWorks() {
        final var chains = new LinkedList<FastenURI>();
        chains.add(FastenURI.create("fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType"));
        var vc = new VulnerableCallChain(new LinkedList<>(), chains);

        var actual = VulnerableCallChainJsonUtils.toJson(vc);
        var expected = "{\"vulnerabilities\":[],\"chain\":[\"fasten://mvn!g:a$1.0.0/x/C.m()" +
            "%2Fjava.lang%2FVoidType\"]}";
        Assertions.assertEquals(expected, actual);
    }
}
