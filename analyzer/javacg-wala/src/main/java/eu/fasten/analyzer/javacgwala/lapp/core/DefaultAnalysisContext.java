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

import java.util.HashMap;
import java.util.Objects;
import java.util.jar.JarFile;

public class DefaultAnalysisContext implements AnalysisContext {

    private final HashMap<String, ResolvedMethod> resolvedDictionary = new HashMap<>();
    private final HashMap<String, UnresolvedMethod> unresolvedDictionary = new HashMap<>();


    @Override
    public synchronized ResolvedMethod makeResolved(String namespace, Selector symbol,
                                                    JarFile artifact) {
        //Objects.requireNonNull(artifact);
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(symbol);

        String key = ResolvedMethod.toID(namespace, symbol, artifact);

        ResolvedMethod val = resolvedDictionary.get(key);
        if (val != null) {
            return val;
        }

        val = new ResolvedMethod(namespace, symbol, artifact);
        resolvedDictionary.put(key, val);
        return val;

    }

    @Override
    public synchronized UnresolvedMethod makeUnresolved(String namespace, Selector symbol) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(symbol);

        String key = UnresolvedMethod.toID(namespace, symbol);

        UnresolvedMethod val = unresolvedDictionary.get(key);
        if (val != null) {
            return val;
        }

        val = new UnresolvedMethod(namespace, symbol);
        unresolvedDictionary.put(key, val);
        return val;

    }

}
