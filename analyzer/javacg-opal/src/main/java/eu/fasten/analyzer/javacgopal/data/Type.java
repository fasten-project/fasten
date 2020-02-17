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

import eu.fasten.analyzer.javacgopal.scalawrapper.JavaToScalaConverter;

import java.util.*;

import org.opalj.br.ClassHierarchy;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.br.SourceFile;
import org.opalj.collection.immutable.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Type {

    private static Logger logger = LoggerFactory.getLogger(Type.class);

    final private String sourceFileName;
    final private Map<Method, Integer> methods;
    final private Chain<ObjectType> superClasses;
    final private List<ObjectType> superInterfaces;

    public Map<Method, Integer> getMethods() {
        return methods;
    }

    public Chain<ObjectType> getSuperClasses() {
        return superClasses;
    }

    public List<ObjectType> getSuperInterfaces() {
        return superInterfaces;
    }

    public String getSourceFileName() { return sourceFileName; }

    public Type(final Map<Method, Integer> methods, final Chain<ObjectType> superClasses, final List<ObjectType> superInterfaces) {
        this.methods = methods;
        this.superClasses = superClasses;
        this.superInterfaces = superInterfaces;
        this.sourceFileName = extractSourceFile();
    }


    /**
     * Extracts the source file of this type. In order to track probable future bugs if there are
     * more than one source files for any reason it will show a warning, so that one can investigate
     * why it happened.
     *
     * @return the String name of the source file (.java) that this type lives in it.
     */
    public String extractSourceFile() {
        final Set<SourceFile> allSourceFilesOfType = new HashSet<>();
        final Set<ObjectType> thisType = new HashSet<>();
        for (final org.opalj.br.Method method : this.getMethods().keySet()) {
            method.declaringClassFile().attributes().toList().foreach(
                JavaToScalaConverter.asScalaFunction1(attribute -> {
                    if (attribute instanceof SourceFile) {
                        allSourceFilesOfType.add((SourceFile) attribute);
                    }
                    return true;
                }));
            thisType.add(method.declaringClassFile().thisType());
        }
        if (thisType.size() > 1){
            logger.warn("More than one classes found during checking for source file of a type {}", thisType);
        }
        if (allSourceFilesOfType.size() > 1) {
            logger.warn("More than one source file found for this type {}", thisType.iterator().next());
        }
        if (!allSourceFilesOfType.isEmpty()) {
            return allSourceFilesOfType.iterator().next().sourceFile();
        }
//        logger.warn("Could not find the source file of type {}", thisType.iterator().next());
        return null;
    }
}
