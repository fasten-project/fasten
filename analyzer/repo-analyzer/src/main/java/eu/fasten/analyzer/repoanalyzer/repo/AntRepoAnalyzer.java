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
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class AntRepoAnalyzer extends RepoAnalyzer {

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path         path to the repository
     * @param buildManager build manager info
     */
    public AntRepoAnalyzer(final Path path, final BuildManager buildManager) {
        super(path, buildManager);
    }

    @Override
    protected Map<TestCoverageType, Float> getTestCoverage(Path root) {
        try {
            var cmd = new String[]{
                    "bash",
                    "-c",
                    "ant junit"
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
    protected Path getPathToSourcesRoot(final Path root) throws IOException, DocumentException {
        var reader = new SAXReader();
        var buildContent = reader.read(Path.of(root.toAbsolutePath().toString(), "build.xml").toFile());
        var rootElement = buildContent.getRootElement();

        var compileTarget = rootElement.selectNodes("./*[local-name()='target']").stream()
                .filter(n -> n.valueOf("@name").equals("compile"))
                .collect(Collectors.toList());
        if (compileTarget.isEmpty()) {
            throw new IOException("No compile target found");
        }
        var srcDir = compileTarget.get(0).selectSingleNode("./*[local-name()='javac']").valueOf("@srcdir");
        return Path.of(root.toAbsolutePath().toString(), resolveProperty(rootElement, srcDir));
    }

    @Override
    protected Path getPathToTestsRoot(final Path root) throws IOException, DocumentException {
        var reader = new SAXReader();
        var buildContent = reader.read(Path.of(root.toAbsolutePath().toString(), "build.xml").toFile());
        var rootElement = buildContent.getRootElement();

        var compileTarget = rootElement.selectNodes("./*[local-name()='target']").stream()
                .filter(n -> n.valueOf("@name").equals("compileTest"))
                .collect(Collectors.toList());
        if (compileTarget.isEmpty()) {
            throw new IOException("No compileTest target found");
        }
        var srcDir = compileTarget.get(0).selectSingleNode("./*[local-name()='javac']").valueOf("@srcdir");
        return Path.of(root.toAbsolutePath().toString(), resolveProperty(rootElement, srcDir));
    }

    /**
     * Resolve property from Ant build.xml file.
     *
     * @param rootElement XML root element
     * @param property    String with property to resolve
     * @return String with all properties resolved
     */
    private String resolveProperty(final Element rootElement, String property) {
        while (property.contains("${")) {
            var finalProperty = property.substring(2, property.length() - 1);
            var values = rootElement.selectNodes("./*[local-name()='property']").stream()
                    .filter(n -> n.valueOf("@name").equals(finalProperty))
                    .map(n -> n.valueOf("@value"))
                    .collect(Collectors.toList());

            values = !values.isEmpty() && values.get(0).isEmpty()
                    ? rootElement.selectNodes("./*[local-name()='property']").stream()
                    .filter(n -> n.valueOf("@name").equals(finalProperty))
                    .map(n -> n.valueOf("@location"))
                    .collect(Collectors.toList())
                    : values;

            var value = values.isEmpty() ? "" : values.get(0);

            property = property.replaceFirst("\\$\\{.*}", resolveProperty(rootElement, value));
        }
        return property;
    }

    @Override
    protected Set<Path> extractModuleRoots(final Path root) {
        return Set.of(root);
    }
}
