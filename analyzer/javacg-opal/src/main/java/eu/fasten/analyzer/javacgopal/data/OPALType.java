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

package eu.fasten.analyzer.javacgopal.data;

import java.util.List;
import java.util.Map;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.collection.immutable.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OPALType {

    private static Logger logger = LoggerFactory.getLogger(OPALType.class);

    private final String sourceFileName;
    private final Map<Method, Integer> methods;
    private final Chain<ObjectType> superClasses;
    private final List<ObjectType> superInterfaces;

    public OPALType(final Map<Method, Integer> methods, final Chain<ObjectType> superClasses,
                    final List<ObjectType> superInterfaces, final String sourceFileName) {
        this.methods = methods;
        this.superClasses = superClasses;
        this.superInterfaces = superInterfaces;
        this.sourceFileName = sourceFileName;
    }

    public Map<Method, Integer> getMethods() {
        return methods;
    }

    public Chain<ObjectType> getSuperClasses() {
        return superClasses;
    }

    public List<ObjectType> getSuperInterfaces() {
        return superInterfaces;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

}
