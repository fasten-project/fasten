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

    private ObjectType type;
    private String sourceFileName;
    private Map<Method,Integer> methods;
    private Chain<ObjectType> superClasses;
    private List<ObjectType> superInterfaces;

    public void setMethods(final Map<Method, Integer> methods) {
        this.methods = methods;
    }

    public void setSuperClasses(final Chain<ObjectType> superClasses) {
        this.superClasses = superClasses;
    }

    public ObjectType getType() {
        return type;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSuperInterfaces(final List<ObjectType> superInterfaces) {
        this.superInterfaces = superInterfaces;
    }

    public Map<Method,Integer> getMethods() {
        return methods;
    }

    public Chain<ObjectType> getSuperClasses() {
        return superClasses;
    }

    public List<ObjectType> getSuperInterfaces() {
        return superInterfaces;
    }

    public Type(final ObjectType type, final Map<Method,Integer> methods, final ClassHierarchy classHierarchy) {
        this.type = type;
        this.methods = methods;
        this.sourceFileName = extractSourceFile();
        setSupers(classHierarchy,type);
    }

    /**
     * Sets super classes and super interfaces of this type
     *
     * @param classHierarchy org.opalj.br.ClassHierarchy
     * @param currentClass org.opalj.br.ObjectType. The type that its supper types should be set.
     */
    public void setSupers(final ClassHierarchy classHierarchy, final ObjectType currentClass) {

        if (classHierarchy.supertypes().contains(currentClass)) {

            try {
                this.superClasses = classHierarchy
                    .allSuperclassTypesInInitializationOrder(currentClass).s();
            } catch (NoSuchElementException e) {
                logger.error("This type {} doesn't have allSuperclassTypesInInitializationOrder" +
                    " method.", currentClass, e);
            } catch (OutOfMemoryError e) {
                logger.error("This type {} made an out of memory Exception in calculation of its" +
                    "supper types!", currentClass, e);
            } catch (Exception e) {
                logger.error("This type made an Exception in calculation of its supper types!", e);
            }

            if (superClasses != null) {
                superClasses.reverse();
            }

            this.superInterfaces = new ArrayList<>();
            classHierarchy.allSuperinterfacetypes(currentClass, false).foreach(
                JavaToScalaConverter.asScalaFunction1(anInterface -> this.superInterfaces.add((ObjectType) anInterface))
            );
        }else {
            logger.warn("Opal class hierarchy didn't include super types of {}", currentClass);
        }
    }

    /**
     * Extracts the source file of this type. In order to track probable future bugs if there are
     * more than one source files for any reason it will show a warning, so that one can investigate
     * why it happened.
     * @return the String name of the source file (.java) that this type lives in it.
     */
    public String extractSourceFile() {
        final Set<SourceFile> allSourceFilesOfType = new HashSet<>();
        for (org.opalj.br.Method method : this.getMethods().keySet()) {
            method.declaringClassFile().attributes().toList().foreach(
                JavaToScalaConverter.asScalaFunction1(attribute -> {
                if (attribute instanceof SourceFile) {
                    allSourceFilesOfType.add((SourceFile) attribute);
                }
                return true;
            }));
        }
        if (allSourceFilesOfType.size() > 1) {
            logger.warn("Two source files for type {}", this.type);
        }
        if (!allSourceFilesOfType.isEmpty()) {
            logger.warn("Could not find the source file of type {}", this.type);
            return allSourceFilesOfType.iterator().next().sourceFile();
        }
        return null;
    }
}
