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


package eu.fasten.analyzer.lapp.callgraph.wala;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import eu.fasten.analyzer.lapp.call.ChaEdge;
import eu.fasten.analyzer.lapp.callgraph.wala.LappPackageBuilder.MethodType;
import eu.fasten.analyzer.lapp.core.Method;
import eu.fasten.analyzer.lapp.core.ResolvedMethod;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassHierarchyInserter {


    private final IClassHierarchy cha;
    private final LappPackageBuilder graph;

    public ClassHierarchyInserter(IClassHierarchy cha, LappPackageBuilder graph) {
        this.cha = cha;
        this.graph = graph;
    }

    public void insertCHA() {
        IClassLoader classLoader = cha.getLoader(ClassLoaderReference.Application);

        Set<TypeReference> unresolved = cha.getUnresolvedClasses();
        // Iterate all classes in Application scope
        for (Iterator<IClass> it = classLoader.iterateAllClasses(); it.hasNext(); ) {
            IClass klass = it.next();
            processClass(klass);
        }
    }

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

    private void processMethod(IClass klass, IMethod declaredMethod, List<IMethod> methodInterfaces) {
        if (declaredMethod.isPrivate()) {
            // Private methods cannot be overridden, so no need for them.
            return;
        }
        IClass superKlass = klass.getSuperclass();

        Method declaredMethodNode = graph.addMethod(declaredMethod.getReference(), getMethodType(klass, declaredMethod));

        if (!(declaredMethodNode instanceof ResolvedMethod)) {
            return;
        }
        ResolvedMethod resolvedMethod = (ResolvedMethod) declaredMethodNode;


        IMethod superMethod = superKlass.getMethod(declaredMethod.getSelector());
        if (superMethod != null) {
            Method superMethodNode = graph.addMethod(superMethod.getReference());

            graph.addChaEdge(superMethodNode, resolvedMethod, ChaEdge.ChaEdgeType.OVERRIDE);
        }


        if (methodInterfaces != null) {
            for (IMethod interfaceMethod : methodInterfaces) {
                Method interfaceMethodNode = graph.addMethod(interfaceMethod.getReference(), MethodType.INTERFACE);

                graph.addChaEdge(interfaceMethodNode, resolvedMethod, ChaEdge.ChaEdgeType.IMPLEMENTS);
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
                    Method abstractSuperClassInterfaceMethodNode = graph.addMethod(abstractSuperClassInterfaceMethod.getReference(), MethodType.INTERFACE);
                    graph.addChaEdge(abstractSuperClassInterfaceMethodNode, resolvedMethod, ChaEdge.ChaEdgeType.IMPLEMENTS);
                }
            }
        }
    }

    private MethodType getMethodType(IClass klass, IMethod declaredMethod) {
        if (declaredMethod.isAbstract()) {

            if (klass.isInterface()) {
                return MethodType.INTERFACE;
            } else {
                return MethodType.ABSTRACT;
            }

        } else {
            return MethodType.IMPLEMENTATION;
        }
    }
}
