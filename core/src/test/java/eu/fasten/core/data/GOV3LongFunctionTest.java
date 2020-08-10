package eu.fasten.core.data;

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


import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.fasten.core.data.GOV3LongFunction.Builder;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.sux4j.mph.Hashes;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class GOV3LongFunctionTest {
    @Test
    public void testHash() {
        final long[] expected = new long[2];
        final long[] actual = new long[2];
        final long seed = 0x1;
        final long l = 0x12345678;
        Hashes.spooky4(TransformationStrategies.rawFixedLong().toBitVector(Long.valueOf(l)), seed, expected);
        GOV3LongFunction.spooky4(l, seed, actual);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void testFunction() throws IOException {
        final List<Long> keys = Arrays.asList(new Long[]{Long.valueOf(7), Long.valueOf(9), Long.valueOf(12), Long.valueOf(-4), Long.valueOf(-2)});
        final Builder builder = new GOV3LongFunction.Builder();
        final GOV3LongFunction function = builder.keys(keys).build();

        for (int i = 0; i < keys.size(); i++)
            assertEquals(i, function.getLong(keys.get(i).longValue()));
    }
}
