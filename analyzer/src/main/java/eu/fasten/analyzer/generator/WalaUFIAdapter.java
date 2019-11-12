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


package eu.fasten.analyzer.generator;


import eu.fasten.analyzer.data.callgraph.WalaCallGraph;
import eu.fasten.analyzer.data.type.*;
import eu.fasten.analyzer.data.ufi.ArrayType;
import eu.fasten.analyzer.data.ufi.UFI;
import eu.fasten.analyzer.data.ufi.UniversalFunctionIdentifier;
import eu.fasten.analyzer.data.ufi.UniversalType;
import eu.fasten.analyzer.lapp.callgraph.FolderLayout.ArtifactFolderLayout;
import eu.fasten.analyzer.lapp.callgraph.FolderLayout.DollarSeparatedLayout;
import eu.fasten.analyzer.lapp.callgraph.wala.WalaAnalysisResult;
import eu.fasten.analyzer.lapp.callgraph.wala.WalaAnalysisTransformer;
import eu.fasten.analyzer.lapp.core.LappPackage;
import com.ibm.wala.classLoader.ArrayClass;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

public final class WalaUFIAdapter implements UniversalFunctionIdentifier<IMethod>, Serializable {


    public final WalaCallGraph callGraph;
    public final Map<String, MavenResolvedCoordinate> jarToCoordinate;
    public final LappPackage lappPackage;

    public WalaUFIAdapter(WalaCallGraph callGraph) {
        this.callGraph = callGraph;
        this.jarToCoordinate = callGraph.analyzedClasspath.stream().collect(toMap(c -> c.jarPath.toString(), Function.identity()));
        ArtifactFolderLayout layoutTransformer = new DollarSeparatedLayout();
        WalaAnalysisResult analysis = new WalaAnalysisResult(callGraph.rawcg, callGraph.rawcg.getClassHierarchy());
        this.lappPackage = WalaAnalysisTransformer.toPackage(analysis, layoutTransformer);
    }

    public static WalaUFIAdapter wrap(WalaCallGraph callGraph) {
        return new WalaUFIAdapter(callGraph);
    }

    private Optional<Namespace> getGlobalPortion(IClass klass) {

        try {
            var jarFile = WalaCallgraphConstructor.fetchJarFile(klass);

            if (jarFile.endsWith("jmod") ||
                    jarFile.endsWith("rt.jar") ||
                    jarFile.endsWith("classes.jar")
            ) {
                return Optional.of(JDKPackage.getInstance());
            } else {
                return Optional.of(this.jarToCoordinate.get(jarFile));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private UniversalType resolveArrayType(TypeReference tyref) {
        assert tyref.isArrayType();

        TypeName ty = tyref.getName().parseForArrayElementName();
        String ref = tyref.getName().toString();
        int countBrackets = ref.length() - ref.replace("[", "").length();

        if (ty.isPrimitiveType()) {
            return new ArrayType(Optional.of(JDKPackage.getInstance()), JavaPrimitive.of(tyref), countBrackets);
        } else if (ty.isClassType()) {
            IClass klass = this.callGraph.rawcg.getClassHierarchy().lookupClass(tyref);
            Namespace inner = klass != null ?
                    new JavaPackage(((klass.getName().toString()).substring(1)).substring(1).split("/")) :
                    new JavaPackage("_unknownType", tyref.getName().toString());
            Optional<Namespace> outer = klass != null ? getGlobalPortion(klass) : Optional.empty();
            return new ArrayType(outer, inner, countBrackets);
        } else {
            return new UniversalType(Optional.empty(), new JavaPackage("unknown"));
        }
    }


    private UniversalType resolveTypeRef(TypeReference tyref) {
        if (tyref.isPrimitiveType()) {
            return new UniversalType(Optional.of(JDKPackage.getInstance()), JavaPrimitive.of(tyref));
        } else if (tyref.isClassType()) {
            IClass klass = this.callGraph.rawcg.getClassHierarchy().lookupClass(tyref);
            Namespace inner = klass != null ?
                    new JavaPackage((klass.getName().toString()).substring(1).split("/")) :
                    new JavaPackage("_unknownType", tyref.getName().toString());
            Optional<Namespace> outer = klass != null ? getGlobalPortion(klass) : Optional.empty();
            return new UniversalType(outer, inner);
        } else if (tyref.isArrayType()) {
            return resolveArrayType(tyref);
        } else {
            return new UniversalType(Optional.empty(), new JavaPackage("unknown"));
        }
    }

    public void toFile(String path, String filename) throws IOException {
        String content = WalaCallgraphConstructor.resolveCalls(this.callGraph.rawcg)
                .stream()
                .map(call -> String.format("\"%s\" -> \"%s\"",
                        convertToUFI(call.source).toString(), convertToUFI(call.target).toString()))
                .collect(Collectors.joining("\n"));
        Files.write(Paths.get(path, filename), content.getBytes());
    }

    @Override
    public UFI convertToUFI(IMethod item) {
        //1. create resolved path
        Optional<Namespace> outer = getGlobalPortion(item.getDeclaringClass());
        Namespace inner = new JavaPackage((item.getDeclaringClass().getName().toString()).substring(1).split("/"));
        UniversalType path = new UniversalType(outer, inner);
        //2. extract methodname
        String methodName = item.getName().toString();
        //3. resolve return type
        UniversalType returnType = item.isInit() ? resolveTypeRef(item.getParameterType(0))
                : resolveTypeRef(item.getReturnType());
        //4. resolve parameter types
        Optional<List<UniversalType>> args = item.getNumberOfParameters() > 0 ?
                Optional.of(IntStream.range(1, item.getNumberOfParameters())
                        .mapToObj(i -> resolveTypeRef(item.getParameterType(i)))
                        .collect(Collectors.toList())) : Optional.empty();

        return new UFI(path, methodName, args, returnType);

    }

    @Override
    public Map<UFI, IMethod> mappings() {
        return WalaCallgraphConstructor
                .resolveCalls(callGraph.rawcg)
                .stream()
                .flatMap(call -> Stream.of(call.source, call.target))
                .collect(toMap(c -> convertToUFI(c), Function.identity(), (v1, v2) -> v1));
    }

}
