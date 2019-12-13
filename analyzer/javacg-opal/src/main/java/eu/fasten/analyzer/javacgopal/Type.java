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

package eu.fasten.analyzer.javacgopal;

import org.opalj.br.ClassHierarchy;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.collection.immutable.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class Type {

    private static Logger logger = LoggerFactory.getLogger(Type.class);

    List<Method> methods;
    Chain<ObjectType> superClasses;
    List<ObjectType> superInterfaces;

    public Type() {
        this.methods = new ArrayList<>();
        this.superClasses = null;
        this.superInterfaces = new ArrayList<>();
    }

    /**
     * Sets super classes and super interfaces of this type
     *
     * @param classHierarchy org.opalj.br.ClassHierarchy
     * @param currentClass org.opalj.br.ObjectType. The type that its supper types should be set.
     */
    public synchronized void setSupers(ClassHierarchy classHierarchy, ObjectType currentClass) {

        try {
            this.superClasses = classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s();
        } catch (NoSuchElementException e) {
            logger.error("This type doesn't have allSuperclassTypesInInitializationOrder method.", e);
        }
        superClasses.reverse();

        classHierarchy.allSuperinterfacetypes(currentClass, false).foreach(
            JavaToScalaConverter.asScalaFunction1(anInterface -> this.superInterfaces.add((ObjectType) anInterface))
        );
    }
}
