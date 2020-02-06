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


package eu.fasten.analyzer.javacgwala.data.callgraph;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.shrikeBT.IInvokeInstruction;

import java.io.Serializable;

public final class ResolvedCall implements Serializable {

    public final IMethod target;
    public final IMethod source;
    public final IInvokeInstruction.Dispatch invoke;

    /**
     * Construct resolved call.
     *
     * @param source - caller
     * @param invoke - invoke
     * @param target - callee
     */
    public ResolvedCall(IMethod source, IInvokeInstruction.IDispatch invoke, IMethod target) {
        this.source = source;
        this.target = target;
        this.invoke = (IInvokeInstruction.Dispatch) invoke;
    }
}
