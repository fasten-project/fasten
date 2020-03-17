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

package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import eu.fasten.analyzer.javacgwala.data.ArtifactResolver;
import eu.fasten.analyzer.javacgwala.data.core.ExternalMethod;
import eu.fasten.analyzer.javacgwala.data.core.InternalMethod;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import java.util.HashMap;

public class AnalysisContext {

    private final ArtifactResolver artifactResolver;

    private final HashMap<String, InternalMethod> internalDictionary;
    private final HashMap<String, ExternalMethod> externalDictionary;

    /**
     * Construct analysis context.
     *
     * @param cha Class hierarchy analysis
     */
    public AnalysisContext(final IClassHierarchy cha) {
        this.internalDictionary = new HashMap<>();
        this.externalDictionary = new HashMap<>();
        this.artifactResolver = new ArtifactResolver(cha);
    }

    /**
     * Check if given method was already added to the list of calls. If call was already added,
     * return this call.
     *
     * @param reference Method reference
     * @return Duplicate or newly created method
     */
    public Method findOrCreate(final MethodReference reference) {
        if (inApplicationScope(reference)) {

            final var jarfile = artifactResolver.findJarFileUsingMethod(reference);
            final var method = new InternalMethod(reference, jarfile);
            final var key = method.toID();

            final var val = internalDictionary.get(key);
            if (val != null) {
                return val;
            }

            internalDictionary.put(key, method);
            return method;
        } else {
            final var method = new ExternalMethod(reference);
            final var key = method.toID();

            final var val = externalDictionary.get(key);
            if (val != null) {
                return val;
            }

            externalDictionary.put(key, method);
            return method;
        }
    }

    /**
     * Check if given method "belongs" to application call.
     *
     * @param reference Method reference
     * @return true if method "belongs" to application scope, false otherwise
     */
    private boolean inApplicationScope(final MethodReference reference) {
        return reference.getDeclaringClass().getClassLoader()
                .equals(ClassLoaderReference.Application);
    }
}
