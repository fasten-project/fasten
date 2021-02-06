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

package eu.fasten.analyzer.repoanalyzer.repo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class AntRepoAnalyzer extends RepoAnalyzer {

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path         path to the repository
     * @param buildManager build manager info
     */
    public AntRepoAnalyzer(String path, BuildManager buildManager) {
        super(path, buildManager);
    }

    @Override
    protected Path getPathToSourcesRoot(Path root) throws IOException {
        var buildContent = Files.readString(Path.of(root.toAbsolutePath().toString(), "build.xml"));

        var compileTarget = StringUtils.substringBetween(buildContent, "<target name=\"compile\"", "</target>");
        return extractPathFromTarget(root, buildContent, compileTarget);
    }

    @Override
    protected Path getPathToTestsRoot(Path root) throws IOException {
        var buildContent = Files.readString(Path.of(root.toAbsolutePath().toString(), "build.xml"));

        var testCompileTarget = StringUtils.substringBetween(buildContent, "<target name=\"compileTest\"", "</target>");
        return extractPathFromTarget(root, buildContent, testCompileTarget);
    }

    private Path extractPathFromTarget(Path root, String buildContent, String compileTarget) throws IOException {
        if (compileTarget != null) {
            var srcDir = StringUtils.substringBetween(compileTarget, "srcdir=\"", "\"");

            if (srcDir != null) {
                srcDir = resolveProperties(buildContent, srcDir);
            }
            return Path.of(root.toAbsolutePath().toString(), srcDir);
        }
        throw new IOException("Error parsing build.xml");
    }

    private String resolveProperties(final String buildContent, String value) {
        while (value.contains("${")) {
            var prop = StringUtils.substringBetween(value, "${", "}");
            var property = StringUtils
                    .substringBetween(buildContent, "<property name=\"" + prop + "\" value=\"", "\"/>");
            property = property == null ? "" : property;
            value = value.replaceFirst("\\$\\{.*}", resolveProperties(buildContent, property));
        }
        return value;
    }

    @Override
    protected List<Path> extractModuleRoots(Path root) {
        return List.of(root);
    }
}
