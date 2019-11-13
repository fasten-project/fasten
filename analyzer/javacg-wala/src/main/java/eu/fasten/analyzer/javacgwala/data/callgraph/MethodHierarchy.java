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

import java.io.Serializable;
import java.util.Optional;

public final class MethodHierarchy implements Serializable {

    public final IMethod child;
    public final Optional<IMethod> parent;
    public final Relation type;

    public MethodHierarchy(IMethod child, Relation type, Optional<IMethod>
            parent) {
        this.child = child;
        this.parent = parent;
        this.type = type;
    }

    public enum Relation {
        IMPLEMENTS,
        OVERRIDES,
        CONCRETE
    }

}
