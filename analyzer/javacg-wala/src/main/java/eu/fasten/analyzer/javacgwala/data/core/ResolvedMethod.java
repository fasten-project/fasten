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

import com.ibm.wala.types.MethodReference;
import java.util.jar.JarFile;

public class ResolvedMethod extends Method {

    private final JarFile artifact;

    /**
     * Construct Resolved method form {@link MethodReference} and artifact.
     *
     * @param reference Method Reference
     * @param artifact  Artifact
     */
    public ResolvedMethod(MethodReference reference, JarFile artifact) {
        super(reference);
        this.artifact = artifact;
    }

    @Override
    public String toID() {
        return artifact == null ? "Unknown"
                : artifact.getName() + "::" + namespace + "." + symbol.toString();
    }
}
