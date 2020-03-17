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
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import eu.fasten.analyzer.javacgwala.data.core.InternalMethod;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClassHierarchyAnalyzer {

    private final CallGraph rawCallGraph;

    private final PartialCallGraph partialCallGraph;

    private final AnalysisContext analysisContext;

    private int counter;

    /**
     * Construct class hierarchy analyzer.
     *
     * @param rawCallGraph     Call graph in Wala format
     * @param partialCallGraph Partial call graph
     */
    public ClassHierarchyAnalyzer(final CallGraph rawCallGraph,
                                  final PartialCallGraph partialCallGraph,
                                  final AnalysisContext analysisContext) {
        this.rawCallGraph = rawCallGraph;
        this.partialCallGraph = partialCallGraph;
        this.analysisContext = analysisContext;
        this.counter = 0;
    }

    /**
     * Add all classes in application scope to class hierarchy.
     */
    public void resolveCHA() throws NullPointerException {
        IClassLoader classLoader = rawCallGraph.getClassHierarchy()
                .getLoader(ClassLoaderReference.Application);

        for (Iterator<IClass> it = classLoader.iterateAllClasses(); it.hasNext(); ) {
            IClass klass = it.next();
            processClass(klass);
        }
    }

    /**
     * Adds new method to class hierarchy. In case class in which given method is defined already
     * exists in CHA - method is ust being appended to the list of methods of this class,
     * otherwise a new class is created.
     *
     * @param method   Method to add
     * @param klassRef Class reference
     */
    public int addMethodToCHA(final Method method, final TypeReference klassRef)
            throws NullPointerException {
        final var klass = this.rawCallGraph.getClassHierarchy().lookupClass(klassRef);

        if (!partialCallGraph.getClassHierarchy().containsKey(getClassURI(method))) {
            addClassToCHA(klass);
        }

        if (!partialCallGraph.getClassHierarchy().get(getClassURI(method)).getMethods()
                .containsValue(method.toCanonicalSchemalessURI())) {
            partialCallGraph.getClassHierarchy().get(getClassURI(method)).getMethods()
                    .putIfAbsent(counter++, method.toCanonicalSchemalessURI());

            return counter - 1;
        }

        return getMethodID(method);
    }

    /**
     * Process class.
     *
     * @param klass Class
     */
    private void processClass(IClass klass) {
        Map<Selector, List<IMethod>> interfaceMethods = klass.getDirectInterfaces()
                .stream()
                .flatMap(o -> o.getDeclaredMethods().stream())
                .collect(
                        Collectors.groupingBy(IMethod::getSelector)
                );

        for (IMethod declaredMethod : klass.getDeclaredMethods()) {

            List<IMethod> methodInterfaces = interfaceMethods.get(declaredMethod.getSelector());

            processMethod(klass, declaredMethod, methodInterfaces);
        }
    }

    /**
     * Process method, it's super methods and interfaces.
     *
     * @param klass          Class
     * @param declaredMethod Method
     * @param interfaces     Interfaces implemented by method
     */
    private void processMethod(IClass klass, IMethod declaredMethod, List<IMethod> interfaces) {
        if (declaredMethod.isPrivate()) {
            return;
        }
        IClass superKlass = klass.getSuperclass();
        addMethod(declaredMethod);

        IMethod superMethod = superKlass.getMethod(declaredMethod.getSelector());
        if (superMethod != null) {
            addMethod(superMethod);
        }

        if (interfaces != null) {
            for (IMethod interfaceMethod : interfaces) {
                addMethod(interfaceMethod);
            }
        }

        if (superKlass.isAbstract() && superMethod == null && interfaces == null) {

            Map<Selector, List<IMethod>> derivedInterfaces = superKlass.getDirectInterfaces()
                    .stream()
                    .flatMap(o -> o.getDeclaredMethods().stream())
                    .collect(Collectors.groupingBy(IMethod::getSelector));

            List<IMethod> derivedInterfacesMethods =
                    derivedInterfaces.get(declaredMethod.getSelector());

            if (derivedInterfacesMethods != null
                    && derivedInterfacesMethods.size() > 0) {
                for (IMethod method : derivedInterfacesMethods) {
                    addMethod(method);
                }
            }
        }
    }

    /**
     * Add method to class hierarchy.
     *
     * @param method Method
     */
    private void addMethod(IMethod method) {
        Method methodNode = analysisContext.findOrCreate(method.getReference());
        if (methodNode instanceof InternalMethod) {
            if (!partialCallGraph.getClassHierarchy()
                    .containsKey(getClassURI(method.getDeclaringClass()))) {
                addClassToCHA(method.getDeclaringClass());
            }
            var methods = partialCallGraph.getClassHierarchy()
                    .get(getClassURI(method.getDeclaringClass())).getMethods();

            if (!methods.containsValue(methodNode.toCanonicalSchemalessURI())) {
                methods.put(counter++, methodNode.toCanonicalSchemalessURI());
            }
        }
    }

    /**
     * Finds ID of a given method.
     *
     * @param method Method
     * @return ID of method
     */
    public int getMethodID(final Method method) throws NullPointerException {
        Integer index = null;
        for (final var entry : partialCallGraph
                .getClassHierarchy().get(getClassURI(method)).getMethods().entrySet()) {
            if (entry.getValue().equals(method.toCanonicalSchemalessURI())) {
                index = entry.getKey();
                break;
            }
        }
        return Integer.valueOf(index);
    }

    /**
     * Find super classes, interfaces and source file name of a given class.
     *
     * @param klass Class
     */
    private void addClassToCHA(final IClass klass) {
        final var className = Method.getClassName(klass.getReference());

        final var sourceFileName = className.split("[$%]")[0] + ".java";
        final List<FastenURI> interfaces = new ArrayList<>();

        for (final var implementedInterface : klass.getAllImplementedInterfaces()) {
            interfaces.add(getClassURI(implementedInterface));
        }

        final LinkedList<FastenURI> superClasses = superClassHierarchy(klass.getSuperclass(),
                new LinkedList<>());

        partialCallGraph.getClassHierarchy().put(getClassURI(klass),
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
    private LinkedList<FastenURI> superClassHierarchy(final IClass klass,
                                                      final LinkedList<FastenURI> aux)
            throws NullPointerException {
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
    private FastenURI getClassURI(final IClass klass) {
        final String packageName = Method.getPackageName(klass.getReference());
        final String className = Method.getClassName(klass.getReference());
        return FastenURI.create("/" + packageName + "/" + className);
    }

    /**
     * Convert class to FastenURI format.
     *
     * @param method Method in declaring class
     * @return URI of class
     */
    private FastenURI getClassURI(final Method method) {
        return FastenURI.create("/" + method.getPackageName() + "/" + method.getClassName());
    }
}
