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

public class GradleRepoAnalyzer extends RepoAnalyzer {

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path         path to the repository
     * @param buildManager build manager info
     */
    public GradleRepoAnalyzer(final String path, final BuildManager buildManager) {
        super(path, buildManager);
    }

    @Override
    protected Path getPathToSourcesRoot(final Path root) {
        return Path.of(root.toAbsolutePath().toString(), DEFAULT_SOURCES_PATH);
    }

    @Override
    protected Path getPathToTestsRoot(final Path root) {
        return Path.of(root.toAbsolutePath().toString(), DEFAULT_TESTS_PATH);
    }

    @Override
    protected List<Path> extractModuleRoots(final Path root) throws IOException {
        var moduleRoots = new ArrayList<Path>();

        if (Arrays.stream(root.toFile().listFiles())
                .noneMatch(f -> f.getName().equals("settings.gradle")
                        || f.getName().equals("settings.gradle.kts"))) {
            moduleRoots.add(root);
            return moduleRoots;
        }

        var settings = this.getBuildManager() == BuildManager.gradleKotlin
                ? Files.readString(Path.of(root.toAbsolutePath().toString(), "settings.gradle.kts"))
                : Files.readString(Path.of(root.toAbsolutePath().toString(), "settings.gradle"));

        var moduleTags = settings.split("\n");
        var modules = Arrays.stream(moduleTags)
                .filter(t -> t.contains("include"))
                .map(t -> t.substring((t.contains("\"") ? t.indexOf("\"") : t.indexOf("'")) + 1,
                        (t.contains("\"") ? t.lastIndexOf("\"") : t.lastIndexOf("'") - 1)))
                .map(t -> Path.of(root.toAbsolutePath().toString(), t))
                .collect(Collectors.toList());
        for (var module : modules) {
            moduleRoots.addAll(extractModuleRoots(module));
        }
        return moduleRoots;
    }
}
