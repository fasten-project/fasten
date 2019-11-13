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
import eu.fasten.analyzer.javacgwala.lapp.callgraph.FolderLayout.ArtifactFolderLayout;
import eu.fasten.analyzer.javacgwala.lapp.call.Call;
import eu.fasten.analyzer.javacgwala.lapp.call.ChaEdge;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.ClassToArtifactResolver;
import eu.fasten.analyzer.javacgwala.lapp.core.LappPackage;
import eu.fasten.analyzer.javacgwala.lapp.core.Method;
import eu.fasten.analyzer.javacgwala.lapp.core.ResolvedMethod;
import eu.fasten.analyzer.javacgwala.lapp.core.UnresolvedMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.jar.JarFile;

public class LappPackageBuilder {

    private static Logger logger = LoggerFactory.getLogger(LappPackageBuilder.class);
    private final LappPackage lappPackage;
    private ArtifactFolderLayout folderLayout;

    private ClassToArtifactResolver artifactResolver;

    enum MethodType {
        INTERFACE, ABSTRACT, IMPLEMENTATION
    }

    public LappPackageBuilder(ClassToArtifactResolver artifactResolver, ArtifactFolderLayout folderLayout) {
        this.artifactResolver = artifactResolver;
        this.folderLayout = folderLayout;

        this.lappPackage = new LappPackage();
    }


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

    public LappPackageBuilder insertCha(IClassHierarchy cha) {
        ClassHierarchyInserter chaInserter = new ClassHierarchyInserter(cha, this);
        chaInserter.insertCHA();
        return this;
    }

    public LappPackageBuilder insertCallGraph(CallGraph callGraph) {
        if (callGraph == null) {
            // Package probably didn't contain entry points
            return this;
        }
        CallGraphInserter cgInserter = new CallGraphInserter(callGraph, callGraph.getClassHierarchy(), this);
        cgInserter.insertCallGraph();

        return this;
    }

    public Method addMethod(MethodReference nodeReference, MethodType type) {
        Method method = addMethod(nodeReference);
        method.metadata.put("type", type.toString());

        return method;
    }


    public Method addMethod(MethodReference reference) {

        String namespace = reference.getDeclaringClass().getName().toString().substring(1).replace('/', '.');
        Selector symbol = reference.getSelector();


        if (inApplicationScope(reference)) {

            JarFile jarfile = artifactResolver.findJarFileUsingMethod(reference);

    /*        if (jarfile==null){
                ArtifactRecord record = artifactResolver.artifactRecordFromMethodReference(reference);
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

    public boolean addCall(Method source, Method target, Call.CallType type) {

        return lappPackage.addCall(source, target, type);

    }

    public boolean addChaEdge(Method related, ResolvedMethod subject, ChaEdge.ChaEdgeType type) {

        return lappPackage.addChaEdge(related, subject, type);

    }

    public LappPackage build() {
        return this.lappPackage;
    }

    private boolean inApplicationScope(MethodReference reference) {
        return reference.getDeclaringClass().getClassLoader().equals(ClassLoaderReference.Application);
    }
}
