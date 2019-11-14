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

import org.opalj.ai.analyses.cg.UnresolvedMethodCall;
import org.opalj.br.ClassHierarchy;

import java.util.ArrayList;
import java.util.List;

public class PartialCallGraph {
    private List<UnresolvedMethodCall> unresolvedCalls;
    private List<ResolvedCall> resolvedCalls;
    private ClassHierarchy classHierarchy;

    public PartialCallGraph(List<UnresolvedMethodCall> unresolvedCalls, List<ResolvedCall> ResolvedCalls, ClassHierarchy classHierarchy) {
        this.unresolvedCalls = unresolvedCalls;
        this.resolvedCalls = ResolvedCalls;
        this.classHierarchy = classHierarchy;
    }

    public PartialCallGraph() {
        this.resolvedCalls = new ArrayList<>();
        this.unresolvedCalls = new ArrayList<>();
    }

    public void setUnresolvedCalls(List<UnresolvedMethodCall> unresolvedCalls) {
        this.unresolvedCalls = unresolvedCalls;
    }

    public void setResolvedCalls(List<ResolvedCall> resolvedCalls) {
        this.resolvedCalls = resolvedCalls;
    }

    public void setClassHierarchy(ClassHierarchy classHierarchy) {
        this.classHierarchy = classHierarchy;
    }

    public List<UnresolvedMethodCall> getUnresolvedCalls() {
        return unresolvedCalls;
    }

    public List<ResolvedCall> getResolvedCalls() {
        return resolvedCalls;
    }

    public ClassHierarchy getClassHierarchy() {
        return classHierarchy;
    }
}
