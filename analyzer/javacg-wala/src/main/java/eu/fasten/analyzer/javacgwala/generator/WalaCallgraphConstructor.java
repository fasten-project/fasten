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


package eu.fasten.analyzer.javacgwala.generator;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileEntry;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeClass;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import eu.fasten.analyzer.javacgwala.data.callgraph.MethodHierarchy;
import eu.fasten.analyzer.javacgwala.data.callgraph.ResolvedCall;
import eu.fasten.analyzer.javacgwala.data.callgraph.WalaCallGraph;
import eu.fasten.analyzer.javacgwala.data.type.MavenResolvedCoordinate;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class WalaCallgraphConstructor {

    //A filter that accepts WALA objects that "belong" to the application loader.
    private static Predicate<CGNode> applicationLoaderFilter = node -> isApplication(node
            .getMethod().getDeclaringClass());

    /**
     * Build new Wala Call Graph from maven coordinates.
     *
     * @param coordinates - maven coordinates list
     * @return - wala call graph
     */
    public static WalaCallGraph build(List<MavenResolvedCoordinate> coordinates) {
        try {
            String classpath = coordinates.get(0).jarPath.toString();
            CallGraph callGraph = buildCallGraph(classpath);
            return new WalaCallGraph(callGraph, coordinates);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Create a call graph instance given a class path.
     *
     * @param classpath - path to class or jar file
     * @return - Call Graph
     * @throws IOException                     - file reading exception
     * @throws ClassHierarchyException         - exception in making a class hierarchy
     * @throws CallGraphBuilderCancelException - exception of call graph creation process
     */
    public static CallGraph buildCallGraph(String classpath)
            throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {
        //1. Fetch exclusion file
        var classLoader = Thread.currentThread().getContextClassLoader();
        var exclusionFile = new File(classLoader
                .getResource("Java60RegressionExclusions.txt").getFile());

        //2. Set the analysis scope
        AnalysisScope scope = AnalysisScopeReader
                .makeJavaBinaryAnalysisScope(classpath, exclusionFile);

        //3. Class Hierarchy for name resolution -> missing superclasses are
        // replaced by the ClassHierarchy root, i.e. java.lang.Object
        var cha = ClassHierarchyFactory.makeWithRoot(scope);

        //4. Specify Entrypoints -> all non-primordial public entrypoints
        // (also with declared parameters, not sub-types)
        var entryPoints = getEntrypoints(cha);

        //5. Encapsulates various analysis options
        var options = new AnalysisOptions(scope, entryPoints);
        var cache = new AnalysisCacheImpl();

        //6 Build the call graph
        //0-CFA points-to analysis
        var builder = Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha, scope);
        //var builder = Util.makeRTABuilder(options, cache, cha, scope);

        return builder.makeCallGraph(options, null);
    }

    /**
     * Resolve calls of a given call graph.
     *
     * @param cg - raw call graph
     * @return - list of resolved calls
     */
    public static List<ResolvedCall> resolveCalls(CallGraph cg) {
        Iterable<CGNode> cgNodes = () -> cg.iterator();
        List<ResolvedCall> calls = StreamSupport
                .stream(cgNodes.spliterator(), false)
                .filter(applicationLoaderFilter)
                .flatMap(node -> {
                    Iterable<CallSiteReference> callSites = () -> node.iterateCallSites();
                    return StreamSupport
                            .stream(callSites.spliterator(), false)
                            .map(callsite -> {
                                MethodReference ref = callsite.getDeclaredTarget();
                                IMethod target = cg.getClassHierarchy().resolveMethod(ref);
                                if (target == null) {
                                    return null;
                                } else {
                                    return new ResolvedCall(node.getMethod(),
                                            callsite.getInvocationCode(), target);
                                }
                            });
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return calls;
    }

    /**
     * Get all methods from the class hierarchy.
     *
     * @param cha - class hierarchy to extract methods from
     * @return - list of method hierarchies
     */
    public static List<MethodHierarchy> getAllMethods(ClassHierarchy cha) {
        Iterable<IClass> classes = () -> cha.getLoader(ClassLoaderReference.Application)
                .iterateAllClasses();
        Stream<IMethod> methods = StreamSupport.stream(classes.spliterator(), false)
                .flatMap(klass -> klass.getDeclaredMethods().parallelStream());

        List<MethodHierarchy> info = methods.map(m -> {
            //Check inheritance
            Optional<IMethod> inheritM = getOverriden(m);

            if (inheritM.isPresent()) {
                return new MethodHierarchy(m, MethodHierarchy.Relation.OVERRIDES, inheritM);
            } else {
                //Check implemented interfaces
                Optional<IMethod> ifaceM = getImplemented(m);
                if (ifaceM.isPresent()) {
                    return new MethodHierarchy(m, MethodHierarchy.Relation.IMPLEMENTS, ifaceM);
                } else {
                    return new MethodHierarchy(m, MethodHierarchy.Relation.CONCRETE,
                            Optional.empty());

                }
            }
        }).collect(Collectors.toList());
        return info;
    }

    /**
     * Get overriden or implemented methods.
     *
     * @param method - method to find overriden or implemented methods of
     * @return - optional overriden or implemented methods
     */
    public static Optional<IMethod> getOverriden(IMethod method) {
        IClass c = method.getDeclaringClass();
        IClass parent = c.getSuperclass();
        if (parent == null) {
            return Optional.empty();
        } else {
            MethodReference ref = MethodReference.findOrCreate(parent.getReference(),
                    method.getSelector());
            IMethod m2 = method.getClassHierarchy().resolveMethod(ref);
            if (m2 != null && !m2.equals(method)) {
                return Optional.of(m2);
            }
            return Optional.empty();
        }
    }

    /**
     * Get implemented methods.
     *
     * @param method - method to find implemented methods of
     * @return - optional implemented methods
     */
    public static Optional<IMethod> getImplemented(IMethod method) {
        return method.getDeclaringClass()
                .getAllImplementedInterfaces() //As interfaces can extend other interfaces,
                // we get all ancestors
                .stream()
                .map(intrface -> {
                    MethodReference ref = MethodReference.findOrCreate(intrface.getReference(),
                            method.getSelector());
                    IMethod m2 = method.getClassHierarchy().resolveMethod(ref);
                    if (m2 != null && !m2.equals(method)) {
                        return m2;
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * Fetch Jar file.
     *
     * @param klass - class in the jar file
     * @return - jar file
     */
    public static String fetchJarFile(IClass klass) {
        ShrikeClass shrikeKlass = (ShrikeClass) klass;
        JarFileEntry moduleEntry = (JarFileEntry) shrikeKlass.getModuleEntry();
        JarFile jarFile = moduleEntry.getJarFile();
        String jarPath = jarFile.getName();
        return jarPath;
    }

    /**
     * Create entry points for call graph creation
     * (stuff taken from  woutrrr/lapp).
     *
     * @param cha - class hierarchy
     * @return - list of entry points
     */
    private static ArrayList<Entrypoint> getEntrypoints(ClassHierarchy cha) {
        Iterable<IClass> classes = () -> cha.iterator();
        List<Entrypoint> entryPoints = StreamSupport.stream(classes.spliterator(), false)
                .filter(WalaCallgraphConstructor::isPublicClass)
                .flatMap(klass -> klass.getAllMethods().parallelStream())
                .filter(WalaCallgraphConstructor::isPublicMethod)
                .map(m -> new DefaultEntrypoint(m, cha))
                .collect(Collectors.toList());
        return new ArrayList<>(entryPoints);
    }

    ///
    /// Helper functions
    ///

    private static boolean isPublicClass(IClass klass) {
        return isApplication(klass)
                && !klass.isInterface()
                && klass.isPublic();
    }

    private static boolean isPublicMethod(IMethod method) {
        return isApplication(method.getDeclaringClass())
                && method.isPublic()
                && !method.isAbstract();
    }

    private static Boolean isApplication(IClass klass) {
        return klass.getClassLoader().getReference().equals(ClassLoaderReference.Application);
    }

}
