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
import eu.fasten.analyzer.javacgwala.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.analyzer.javacgwala.data.core.ResolvedMethod;
import eu.fasten.core.data.FastenURI;

import java.util.*;
import java.util.stream.Collectors;

public class ClassHierarchyAnalyzer {

    private final Set<FastenURI> processedClasses = new HashSet<>();

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

    public void resolveCHA() {
        IClassLoader classLoader = rawCallGraph.getClassHierarchy().getLoader(ClassLoaderReference.Application);

        // Iterate all classes in Application scope
        for (Iterator<IClass> it = classLoader.iterateAllClasses(); it.hasNext(); ) {
            IClass klass = it.next();
            processClass2(klass);
        }
//        for (var klass : rawCallGraph.getClassHierarchy()) {
//            processClass(klass);
//            for (var method : klass.getDeclaredMethods()) {
//                var newMethod = analysisContext.findOrCreate(method.getReference());
//                partialCallGraph.getClassHierarchy().get(getClassURI(klass)).getMethods()
//                        .putIfAbsent(counter++, newMethod.toCanonicalSchemalessURI());
//            }
//        }
    }

    private void processClass2(IClass klass) {


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

    private void processMethod(IClass klass, IMethod declaredMethod, List<IMethod> methodInterfaces) {
        if (declaredMethod.isPrivate()) {
            // Private methods cannot be overridden, so no need for them.
            return;
        }
        IClass superKlass = klass.getSuperclass();

        Method declaredMethodNode = analysisContext.findOrCreate(declaredMethod.getReference());

        if (!(declaredMethodNode instanceof ResolvedMethod)) {
            return;
        }
        ResolvedMethod resolvedMethod = (ResolvedMethod) declaredMethodNode;
        addMethodToCHA(resolvedMethod, klass.getReference());
//        partialCallGraph.getClassHierarchy().get(getClassURI(resolvedMethod)).getMethods()
//                .putIfAbsent(counter++, resolvedMethod.toCanonicalSchemalessURI());


        IMethod superMethod = superKlass.getMethod(declaredMethod.getSelector());
        if (superMethod != null) {
            Method superMethodNode = analysisContext.findOrCreate(superMethod.getReference());
            if (superMethodNode instanceof ResolvedMethod) {
                addMethodToCHA(superMethodNode, klass.getReference());
            }
//            partialCallGraph.getClassHierarchy().get(getClassURI(superMethodNode)).getMethods()
//                    .putIfAbsent(counter++, superMethodNode.toCanonicalSchemalessURI());
            //graph.addChaEdge(superMethodNode, resolvedMethod, ChaEdge.ChaEdgeType.OVERRIDE);
        }


        if (methodInterfaces != null) {
            for (IMethod interfaceMethod : methodInterfaces) {
                Method interfaceMethodNode = analysisContext.findOrCreate(interfaceMethod.getReference());
                if (interfaceMethodNode instanceof ResolvedMethod) {
                    addMethodToCHA(interfaceMethodNode, klass.getReference());
                }
//                partialCallGraph.getClassHierarchy().get(getClassURI(interfaceMethodNode)).getMethods()
//                        .putIfAbsent(counter++, interfaceMethodNode.toCanonicalSchemalessURI());
                //graph.addChaEdge(interfaceMethodNode, resolvedMethod, ChaEdge.ChaEdgeType.IMPLEMENTS);
            }
        }


        // An abstract class doesn't have to define abstract method for interface methods
        // So if this method doesn't have a super method or an interface method look for them in the interfaces of the abstract superclass
        if (superKlass.isAbstract() && superMethod == null && methodInterfaces == null) {

            Map<Selector, List<IMethod>> abstractSuperClassInterfacesByMethod = superKlass.getDirectInterfaces()
                    .stream()
                    .flatMap(o -> o.getDeclaredMethods().stream())
                    .collect(Collectors.groupingBy(IMethod::getSelector));

            List<IMethod> abstractSuperClassInterfaceMethods = abstractSuperClassInterfacesByMethod.get(declaredMethod.getSelector());
            if (abstractSuperClassInterfaceMethods != null && abstractSuperClassInterfaceMethods.size() > 0) {
                for (IMethod abstractSuperClassInterfaceMethod : abstractSuperClassInterfaceMethods) {
                    Method abstractSuperClassInterfaceMethodNode = analysisContext.findOrCreate(abstractSuperClassInterfaceMethod.getReference());
                    if (abstractSuperClassInterfaceMethodNode instanceof ResolvedMethod) {
                        addMethodToCHA(abstractSuperClassInterfaceMethodNode, klass.getReference());
                    }
//                    partialCallGraph.getClassHierarchy().get(getClassURI(abstractSuperClassInterfaceMethodNode)).getMethods()
//                            .putIfAbsent(counter++, abstractSuperClassInterfaceMethodNode.toCanonicalSchemalessURI());
                    //graph.addChaEdge(abstractSuperClassInterfaceMethodNode, resolvedMethod, ChaEdge.ChaEdgeType.IMPLEMENTS);
                }
            }
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
    public int addMethodToCHA(final Method method, final TypeReference klassRef) {
        final var klass = this.rawCallGraph.getClassHierarchy().lookupClass(klassRef);

        if (!partialCallGraph.getClassHierarchy().containsKey((getClassURI(method)))) {
            processClass(klass);
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
     * Finds ID of a given method.
     *
     * @param method Method
     * @return ID of method
     */
    public int getMethodID(final Method method) {
        int index = -1;
        for (final var entry : partialCallGraph
                .getClassHierarchy().get(getClassURI(method)).getMethods().entrySet()) {
            if (entry.getValue().equals(method.toCanonicalSchemalessURI())) {
                index = entry.getKey();
                break;
            }
        }
        return index;
    }

    /**
     * Find super classes, interfaces and source file name of a given class.
     *
     * @param klass Class
     */
    private void processClass(final IClass klass) {
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
                                                      final LinkedList<FastenURI> aux) {
        if (klass == null) {
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
