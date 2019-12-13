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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

public class Type {

    List<Method> methods;
    List<ObjectType> superClasses;
    List<ObjectType> superInterfaces;

    public Type() {
        this.methods = new ArrayList<>();
        this.superClasses = new ArrayList<>();
        this.superInterfaces = new ArrayList<>();
    }

    public synchronized void setSupers(ClassHierarchy classHierarchy, ObjectType currentClass) {

        try {
            classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s().foreach(
                JavaToScalaConverter.asScalaFunction1(cl -> this.superClasses.add((ObjectType) cl))
            );
        } catch (NoSuchElementException e) {
            classHierarchy.allSupertypes(currentClass, false).foreach(
                JavaToScalaConverter.asScalaFunction1(cl -> this.superClasses.add((ObjectType) cl))
            );
        }
        Collections.reverse(this.superClasses);

        classHierarchy.allSuperinterfacetypes(currentClass, false).foreach(
            JavaToScalaConverter.asScalaFunction1(anInterface -> this.superInterfaces.add((ObjectType) anInterface))
        );
    }
}
