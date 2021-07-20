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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GradleRepoAnalyzer extends RepoAnalyzer {

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path         path to the repository
     * @param buildManager build manager info
     */
    public GradleRepoAnalyzer(final Path path, final BuildManager buildManager) {
        super(path, buildManager);
    }

    @Override
    protected Map<TestCoverageType, Float> getTestCoverage(Path root) {
        try {
            var cmd = new String[]{
                    "bash",
                    "-c",
                    "gradle test"
            };
            var process = new ProcessBuilder(cmd).directory(root.toFile()).start();
            if (process.waitFor(3, TimeUnit.MINUTES)) {
                return Collections.emptyMap();
            } else {
                return null;
            }
        } catch (IOException | InterruptedException e) {
            return null;
        }
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
    protected Set<Path> extractModuleRoots(final Path root) throws IOException {
        var modules = new HashSet<Path>();

        var directories = Files.walk(root)
                .map(Path::toFile)
                .filter(File::isDirectory)
                .collect(Collectors.toList());

        for (var dir : directories) {
            var files = Arrays.stream(dir.listFiles())
                    .collect(Collectors.toMap(File::getName, File::getAbsolutePath));

            if (files.containsKey("src")) {
                modules.add(dir.toPath());
            }
        }
        return modules;
    }
}
