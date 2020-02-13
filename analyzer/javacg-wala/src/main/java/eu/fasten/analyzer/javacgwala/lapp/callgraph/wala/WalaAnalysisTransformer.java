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

import com.ibm.wala.types.ClassLoaderReference;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.ClassToArtifactResolver;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.folderlayout.ArtifactFolderLayout;
import eu.fasten.analyzer.javacgwala.lapp.core.LappPackage;

public class WalaAnalysisTransformer {

    /**
     * Transforms result of Wala call graph analysis to a {@link LappPackage}.
     *
     * @param analysisResult Wala call graph analysis
     * @param layout         Folder Layout extracted from a jar file.
     * @return A new lapp package
     */
    public static LappPackage toPackage(WalaAnalysisResult analysisResult,
                                        ArtifactFolderLayout layout) {
        ClassToArtifactResolver artifactResolver =
                new ClassToArtifactResolver(analysisResult.extendedCha, layout);

        LappPackageBuilder builder = new LappPackageBuilder(artifactResolver, layout);
        return builder.setPackages(analysisResult.extendedCha.getScope()
                .getModules(ClassLoaderReference.Application))
                .insertCha(analysisResult.extendedCha)
                .insertCallGraph(analysisResult.cg)
                .build();

    }
}
