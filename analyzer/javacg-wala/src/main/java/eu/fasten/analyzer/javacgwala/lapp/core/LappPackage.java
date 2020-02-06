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


package eu.fasten.analyzer.javacgwala.lapp.core;

import eu.fasten.analyzer.javacgwala.lapp.call.Call;
import eu.fasten.analyzer.javacgwala.lapp.call.ChaEdge;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.ArtifactRecord;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LappPackage {
    public final Set<ArtifactRecord> artifacts = new HashSet<>();

    public final Set<ResolvedMethod> methods = new HashSet<>();
    public final Set<Call> resolvedCalls = new HashSet<>();
    public final Set<Call> unresolvedCalls = new HashSet<>();

    public final Set<ChaEdge> cha = new HashSet<>();
    public final Set<ChaEdge> unresolvedCha = new HashSet<>();

    public final Map<String, String> metadata = new HashMap<>();

    public void addResolvedMethod(ResolvedMethod resolvedMethod) {
        methods.add(resolvedMethod);
    }

    /**
     * Add a new call to the set of Resolved calls in case both source and target of are resolved,
     * or to the set of Unresolved calls if at least one of the parameters is not resolved.
     *
     * @param source - Caller
     * @param target - Callee
     * @param type   - Call type
     * @return - true if call was added successfully
     */
    public boolean addCall(Method source, Method target, Call.CallType type) {

        if (target instanceof ResolvedMethod
                && source instanceof ResolvedMethod) {
            return addResolvedCall((ResolvedMethod) source, (ResolvedMethod) target, type);
        }

        return addUnresolvedCall(source, target, type);
    }

    private boolean addUnresolvedCall(Method source, Method target, Call.CallType type) {
        Call call = new Call(source, target, type);

        return unresolvedCalls.add(call);
    }

    private boolean addResolvedCall(ResolvedMethod source, ResolvedMethod target,
                                    Call.CallType type) {
        Call call = new Call(source, target, type);

        return resolvedCalls.add(call);
    }

    /**
     * Add a new CHA Edge to the set of Resolved Cha Edges if {@param related} is a resolved method,
     * or to the set of Unresolved Cha Edges otherwise.
     *
     * @param related - related method
     * @param subject - subject
     * @param type    - type of cha edge
     * @return - true if added successfully
     */
    public boolean addChaEdge(Method related, ResolvedMethod subject, ChaEdge.ChaEdgeType type) {
        if (related instanceof ResolvedMethod) {
            return addResolvedChaEdge((ResolvedMethod) related, subject, type);
        }

        return addUnresolvedChaEdge(related, subject, type);

    }

    private boolean addResolvedChaEdge(ResolvedMethod related, ResolvedMethod subject,
                                      ChaEdge.ChaEdgeType type) {
        return cha.add(new ChaEdge(related, subject, type));
    }

    private boolean addUnresolvedChaEdge(Method related, ResolvedMethod subject,
                                        ChaEdge.ChaEdgeType type) {
        return unresolvedCha.add(new ChaEdge(related, subject, type));
    }

}
