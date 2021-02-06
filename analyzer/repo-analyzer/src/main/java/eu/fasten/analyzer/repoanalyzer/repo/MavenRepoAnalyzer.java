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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class MavenRepoAnalyzer extends RepoAnalyzer {

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path         path to the repository
     * @param buildManager build manager info
     */
    public MavenRepoAnalyzer(final String path, final BuildManager buildManager) {
        super(path, buildManager);
    }

    @Override
    protected Path getPathToSourcesRoot(final Path root) throws IOException {
        var pomContent = Files.readString(Path.of(root.toString(), "pom.xml"));
        var sourcePath = StringUtils.substringBetween(pomContent, "<sourceDirectory>", "</sourceDirectory>");
        sourcePath = sourcePath == null ? DEFAULT_SOURCES_PATH : sourcePath;
        while (sourcePath.contains("$")) {
            sourcePath = sourcePath.replaceFirst("\\$\\{.*}", "");
        }
        return Path.of(root.toAbsolutePath().toString(), sourcePath);
    }

    @Override
    protected Path getPathToTestsRoot(final Path root) throws IOException {
        var pomContent = Files.readString(Path.of(root.toString(), "pom.xml"));
        var sourcePath = StringUtils.substringBetween(pomContent, "<testSourceDirectory>", "</testSourceDirectory>");
        sourcePath = sourcePath == null ? DEFAULT_TESTS_PATH : sourcePath;
        while (sourcePath.contains("$")) {
            sourcePath = sourcePath.replaceFirst("\\$\\{.*}", "");
        }
        return Path.of(root.toAbsolutePath().toString(), sourcePath);
    }

    @Override
    protected List<Path> extractModuleRoots(final Path root) throws IOException {
        var moduleRoots = new ArrayList<Path>();

        var pomContent = Files.readString(Path.of(root.toAbsolutePath().toString(), "pom.xml"));
        var modules = StringUtils.substringBetween(pomContent, "<modules>", "</modules>");

        if (modules == null) {
            moduleRoots.add(root);
            return moduleRoots;
        }

        var moduleTags = modules.split("</module>");
        var moduleNames = Arrays.stream(moduleTags)
                .filter(t -> t.contains("<module>"))
                .map(t -> t.substring(t.indexOf("<module>") + 8))
                .map(t -> Path.of(root.toAbsolutePath().toString(), t))
                .collect(Collectors.toList());
        for (var module : moduleNames) {
            moduleRoots.addAll(extractModuleRoots(module));
        }
        return moduleRoots;
    }
}
