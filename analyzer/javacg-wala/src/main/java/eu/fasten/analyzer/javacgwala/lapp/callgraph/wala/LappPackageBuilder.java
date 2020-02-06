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


package eu.fasten.analyzer.javacgwala.lapp.callgraph.wala;

import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import eu.fasten.analyzer.javacgwala.lapp.call.Call;
import eu.fasten.analyzer.javacgwala.lapp.call.ChaEdge;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.ClassToArtifactResolver;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.folderlayout.ArtifactFolderLayout;
import eu.fasten.analyzer.javacgwala.lapp.core.LappPackage;
import eu.fasten.analyzer.javacgwala.lapp.core.Method;
import eu.fasten.analyzer.javacgwala.lapp.core.ResolvedMethod;
import eu.fasten.analyzer.javacgwala.lapp.core.UnresolvedMethod;

import java.util.List;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LappPackageBuilder {

    private static Logger logger = LoggerFactory.getLogger(LappPackageBuilder.class);
    private final LappPackage lappPackage;
    private ArtifactFolderLayout folderLayout;

    private ClassToArtifactResolver artifactResolver;

    enum MethodType {
        INTERFACE, ABSTRACT, IMPLEMENTATION
    }

    /**
     * Constructs a Lapp Package builder from an artifact resolver
     * and a folder layout of an artifact.
     *
     * @param artifactResolver - Artifact resolver
     * @param folderLayout     - Folder Layout extracted from a jar file
     */
    public LappPackageBuilder(ClassToArtifactResolver artifactResolver,
                              ArtifactFolderLayout folderLayout) {
        this.artifactResolver = artifactResolver;
        this.folderLayout = folderLayout;

        this.lappPackage = new LappPackage();
    }

    /**
     * Add packages to the set of Artifacts in a lapp package if a package is a Jar module.
     *
     * @param modules - list of modules
     * @return - Builder
     */
    public LappPackageBuilder setPackages(List<Module> modules) {
        for (Module m : modules) {
            if (m instanceof JarFileModule) {
                JarFileModule jfm = ((JarFileModule) m);
                lappPackage.artifacts.add(folderLayout.artifactRecordFromJarFile(jfm.getJarFile()));
            } else {
                logger.warn("Unknown module to analyse found.");
            }
        }

        return this;
    }

    /**
     * Add class hierarchy to a lapp package.
     *
     * @param cha - class hierarchy to add
     * @return - Builder
     */
    public LappPackageBuilder insertCha(IClassHierarchy cha) {
        ClassHierarchyInserter chaInserter = new ClassHierarchyInserter(cha, this);
        chaInserter.insertCHA();
        return this;
    }

    /**
     * Add a call graph to a lapp package.
     *
     * @param callGraph - call graph to add
     * @return - Builder
     */
    public LappPackageBuilder insertCallGraph(CallGraph callGraph) {
        if (callGraph == null) {
            // Package probably didn't contain entry points
            return this;
        }
        CallGraphInserter cgInserter = new CallGraphInserter(callGraph,
                callGraph.getClassHierarchy(), this);
        cgInserter.insertCallGraph();

        return this;
    }

    /**
     * Add method and add metadata to it to lapp package.
     *
     * @param nodeReference - method reference
     * @param type          - metadata
     * @return - Method with metadata added
     */
    public Method addMethod(MethodReference nodeReference, MethodType type) {
        Method method = addMethod(nodeReference);
        method.metadata.put("type", type.toString());

        return method;
    }

    /**
     * Add method to the lapp package.
     *
     * @param reference - method reference
     * @return - added Method
     */
    public Method addMethod(MethodReference reference) {

        String namespace = reference.getDeclaringClass().getName().toString().substring(1)
                .replace('/', '.');
        Selector symbol = reference.getSelector();


        if (inApplicationScope(reference)) {

            JarFile jarfile = artifactResolver.findJarFileUsingMethod(reference);

            /*if (jarfile==null){
                ArtifactRecord record = artifactResolver
                .artifactRecordFromMethodReference(reference);
                jarfile.;
            }*/
            ResolvedMethod resolvedMethod = ResolvedMethod.findOrCreate(namespace, symbol, jarfile);

            lappPackage.addResolvedMethod(resolvedMethod);

            return resolvedMethod;

        } else {
            UnresolvedMethod unresolvedMethod = UnresolvedMethod.findOrCreate(namespace, symbol);
            return unresolvedMethod;
        }
    }

    /**
     * Add call to a lapp package.
     *
     * @param source - caller
     * @param target - callee
     * @param type   - call type
     * @return - true if a call was added
     */
    public boolean addCall(Method source, Method target, Call.CallType type) {

        return lappPackage.addCall(source, target, type);

    }

    /**
     * Add cha edge to a lapp package.
     *
     * @param related - related method
     * @param subject - subject
     * @param type    - type of cha edge
     * @return - true if added successfully
     */
    public boolean addChaEdge(Method related, ResolvedMethod subject, ChaEdge.ChaEdgeType type) {

        return lappPackage.addChaEdge(related, subject, type);

    }

    /**
     * Build a lapp package.
     *
     * @return - a new {@link LappPackage}
     */
    public LappPackage build() {
        return this.lappPackage;
    }

    private boolean inApplicationScope(MethodReference reference) {
        return reference.getDeclaringClass().getClassLoader()
                .equals(ClassLoaderReference.Application);
    }
}
