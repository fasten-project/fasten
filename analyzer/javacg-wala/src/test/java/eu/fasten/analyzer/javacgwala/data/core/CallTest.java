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

package eu.fasten.analyzer.javacgwala.data.core;

import com.ibm.wala.types.Selector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CallTest {

    private static Call resolvedCall;
    private static Call unresolvedCall;
    private static ResolvedMethod source;
    private static ResolvedMethod target;
    private static UnresolvedMethod unresolvedMethod;

    @BeforeAll
    public static void setUp() {
        source = new ResolvedMethod("name.space.SingleSourceToTarget", Selector.make("sourceMethod()V"), null);
        target = new ResolvedMethod("name.space.SingleSourceToTarget", Selector.make("targetMethod()V"), null);
        unresolvedMethod = new UnresolvedMethod("name.space.SingleSourceToTarget", Selector.make("<init>()V"));
        resolvedCall = new Call(source, target, Call.CallType.STATIC);
        unresolvedCall = new Call(source, unresolvedMethod, Call.CallType.STATIC);
    }

    @Test
    void getSource() {
        assertEquals(source, resolvedCall.getSource());
        assertEquals(source, unresolvedCall.getSource());
    }

    @Test
    void getTarget() {
        assertEquals(target, resolvedCall.getTarget());
        assertEquals(unresolvedMethod, unresolvedCall.getTarget());
    }

    @Test
    void getCallType() {
        assertEquals(Call.CallType.STATIC, resolvedCall.getCallType());
    }

    @Test
    void toURICall() {
        var fastenUri = resolvedCall.toURICall();

        assertEquals("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid",
                fastenUri[0].toString());
        assertEquals("///name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid",
                fastenUri[1].toString());
    }
}