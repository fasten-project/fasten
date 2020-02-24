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

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.types.TypeReference;
import eu.fasten.analyzer.javacgwala.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ClassHierarchyAnalyzer {

    private final Set<FastenURI> processedClasses = new HashSet<>();

    private final CallGraph rawCallGraph;

    private final PartialCallGraph partialCallGraph;

    private int counter;

    /**
     * Construct class hierarchy analyzer.
     *
     * @param rawCallGraph     Call graph in Wala format
     * @param partialCallGraph Partial call graph
     */
    public ClassHierarchyAnalyzer(CallGraph rawCallGraph, PartialCallGraph partialCallGraph) {
        this.rawCallGraph = rawCallGraph;
        this.partialCallGraph = partialCallGraph;
        this.counter = 0;
    }

    /**
     * Adds new method to class hierarchy. In case class in which given method is defined already
     * exists in CHA - method is ust being appended to the list of methods of this class,
     * otherwise a new class is created.
     *
     * @param method   Method to add
     * @param klassRef Class reference
     */
    public void addMethodToCHA(Method method, TypeReference klassRef) {
        var klass = this.rawCallGraph.getClassHierarchy().lookupClass(klassRef);

        if (!processedClasses.contains(getClassURI(method))) {
            if (klass == null) {
                processedClasses.add(getClassURI(method));
                partialCallGraph.getClassHierarchy().put(getClassURI(method),
                        new ExtendedRevisionCallGraph.Type(null,
                                new HashMap<>(), new LinkedList<>(), new ArrayList<>()));
            } else if (processedClasses.add(getClassURI(method))) {
                processClass(method, klass);
            }
        }
        partialCallGraph.getClassHierarchy().get(getClassURI(method)).getMethods()
                .putIfAbsent(method.toCanonicalSchemalessURI(), counter++);
    }

    /**
     * Find super classes, interfaces and source file name of a given class.
     *
     * @param klass Class
     */
    private void processClass(Method method, IClass klass) {
        String sourceFileName = klass.getSourceFileName();
        List<FastenURI> interfaces = new ArrayList<>();

        for (IClass implementedInterface : klass.getAllImplementedInterfaces()) {
            interfaces.add(getClassURI(implementedInterface));
        }

        LinkedList<FastenURI> superClasses = superClassHierarchy(klass.getSuperclass(),
                new LinkedList<>());

        partialCallGraph.getClassHierarchy().put(getClassURI(method),
                new ExtendedRevisionCallGraph.Type(sourceFileName,
                        new HashMap<>(), superClasses, interfaces));
    }

    /**
     * Recursively creates a list of super classes of a given class in the order of inheritance.
     *
     * @param klass Class
     * @param aux   Auxiliary list
     * @return List of super classes
     */
    private LinkedList<FastenURI> superClassHierarchy(IClass klass, LinkedList<FastenURI> aux) {
        if (klass == null || aux == null) {
            return new LinkedList<>();
        }
        aux.add(getClassURI(klass));
        if (klass.getSuperclass() == null) {
            return aux;
        }

        return superClassHierarchy(klass.getSuperclass(), aux);
    }

    /**
     * Convert class to FastenURI format.
     *
     * @param klass Class
     * @return URI of class
     */
    private FastenURI getClassURI(IClass klass) {
        String packageName = Method.getPackageName2(klass.getReference());
        String className = Method.getClassName2(klass.getReference());
        return FastenURI.create("/" + packageName + "/" + className);
    }

    /**
     * Convert class to FastenURI format.
     *
     * @param method Method in declaring class
     * @return URI of class
     */
    private FastenURI getClassURI(Method method) {
        return FastenURI.create("/" + method.getPackageName() + "/" + method.getClassName());
    }
}
