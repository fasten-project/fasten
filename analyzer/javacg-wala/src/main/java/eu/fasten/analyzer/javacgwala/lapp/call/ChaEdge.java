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


package eu.fasten.analyzer.javacgwala.lapp.call;

import eu.fasten.analyzer.javacgwala.lapp.core.Method;
import eu.fasten.analyzer.javacgwala.lapp.core.ResolvedMethod;

public class ChaEdge extends Edge {

    public enum ChaEdgeType {

        OVERRIDE("overridden by"),
        IMPLEMENTS("implemented by"),
        UNKNOWN("unknown");

        public final String label;

        ChaEdgeType(String label) {
            this.label = label;
        }
    }

    public final ChaEdgeType type;

    public ChaEdge(Method related, ResolvedMethod subject, ChaEdgeType type) {
        super(related, subject);
        this.type = type;
    }

    @Override
    public String getLabel() {
        return type.label;
    }
}