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

import com.ibm.wala.types.Selector;

public class UnresolvedMethod extends Method {

    public static final AnalysisContext DEFAULT_CONTEXT = new DefaultAnalysisContext();

    public UnresolvedMethod(String namespace, Selector symbol) {
        super(namespace, symbol);
    }

    public String toID() {
        return toID(namespace, symbol);
    }

    /**
     * Convert {@link UnresolvedMethod} to ID representation.
     *
     * @param namespace - namespace
     * @param symbol    - symbol
     * @return - method ID
     */
    public static String toID(String namespace, Selector symbol) {
        return "__::" + namespace + "." + symbol.toString();
    }

    /**
     * Find a unresolved method in the Unresolved Dictionary or create a new one and add it to
     * the dictionary.
     *
     * @param namespace - namespace
     * @param symbol    - symbol
     * @return - found or created method
     */
    public static synchronized UnresolvedMethod findOrCreate(String namespace, Selector symbol) {
        return DEFAULT_CONTEXT.makeUnresolved(namespace, symbol);
    }
}
