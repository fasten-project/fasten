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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

public class MavenRepoAnalyzer extends RepoAnalyzer {

    /**
     * Constructs a Repo Analyzer given a path the root of a repository to analyze.
     *
     * @param path         path to the repository
     * @param buildManager build manager info
     */
    public MavenRepoAnalyzer(final Path path, final BuildManager buildManager) {
        super(path, buildManager);
    }

    @Override
    protected Map<TestCoverageType, Float> getTestCoverage(Path root) {
        var ranTests = false;
        try {
            var oldPom = addJacocoPluginToPomFile(Path.of(root.toString(), "pom.xml"));
            ranTests = true;
            var successful = runMvnTest(root);
            if (!successful) {
                return null;
            }
            Files.writeString(Path.of(root.toString(), "pom.xml"), oldPom);
            return extractTestCoverageFromReport(root);
        } catch (IOException | DocumentException | InterruptedException e) {
            if (!ranTests) {
                try {
                    if (!runMvnTest(root)) {
                        return null;
                    }
                } catch (IOException | InterruptedException ioException) {
                    return null;
                }
            }
            return Collections.emptyMap();
        }
    }

    private Map<TestCoverageType, Float> extractTestCoverageFromReport(Path root) throws IOException, DocumentException {
        var reportPath = Path.of(root.toString(), "target", "site", "jacoco", "jacoco.xml");
        var reportRoot = new SAXReader().read(new StringReader(Files.readString(reportPath))).getRootElement();
        var coverageNodes = reportRoot.selectNodes("./*[local-name()='counter']");
        if (coverageNodes != null) {
            var map = new HashMap<TestCoverageType, Float>();
            for (var node : coverageNodes) {
                var elem = (Element) node;
                switch (elem.attributeValue("type")) {
                    case "INSTRUCTION": {
                        var missed = elem.attributeValue("missed");
                        var covered = elem.attributeValue("covered");
                        var coverage = Float.parseFloat(covered) / (Float.parseFloat(missed) + Float.parseFloat(covered));
                        map.put(TestCoverageType.instructionCoverage, coverage);
                    }
                    case "BRANCH": {
                        var missed = elem.attributeValue("missed");
                        var covered = elem.attributeValue("covered");
                        var coverage = Float.parseFloat(covered) / (Float.parseFloat(missed) + Float.parseFloat(covered));
                        map.put(TestCoverageType.branchCoverage, coverage);
                    }
                    case "LINE": {
                        var missed = elem.attributeValue("missed");
                        var covered = elem.attributeValue("covered");
                        var coverage = Float.parseFloat(covered) / (Float.parseFloat(missed) + Float.parseFloat(covered));
                        map.put(TestCoverageType.lineCoverage, coverage);
                    }
                    case "COMPLEXITY": {
                        var missed = elem.attributeValue("missed");
                        var covered = elem.attributeValue("covered");
                        var coverage = Float.parseFloat(covered) / (Float.parseFloat(missed) + Float.parseFloat(covered));
                        map.put(TestCoverageType.complexityCoverage, coverage);
                    }
                    case "METHOD": {
                        var missed = elem.attributeValue("missed");
                        var covered = elem.attributeValue("covered");
                        var coverage = Float.parseFloat(covered) / (Float.parseFloat(missed) + Float.parseFloat(covered));
                        map.put(TestCoverageType.methodCoverage, coverage);
                    }
                    case "CLASS": {
                        var missed = elem.attributeValue("missed");
                        var covered = elem.attributeValue("covered");
                        var coverage = Float.parseFloat(covered) / (Float.parseFloat(missed) + Float.parseFloat(covered));
                        map.put(TestCoverageType.classCoverage, coverage);
                    }
                }
            }
            return map;
        }
        return Collections.emptyMap();
    }

    private String addJacocoPluginToPomFile(Path pomPath) throws IOException, DocumentException {
        var pomContent = Files.readString(pomPath);
        var pomRoot = new SAXReader().read(new StringReader(pomContent)).getRootElement();
        var build = pomRoot.selectSingleNode("./*[local-name()='build']");
        if (build == null) {
            build = pomRoot.addElement("build");
        }
        var plugins = (Element) build.selectSingleNode("./*[local-name()='plugins']");
        if (plugins == null) {
            plugins = ((Element) build).addElement("plugins");
        }
        var plugin = plugins.addElement("plugin");
        var group = plugin.addElement("groupId");
        group.setText("org.jacoco");
        var artifact = plugin.addElement("artifactId");
        artifact.setText("jacoco-maven-plugin");
        var version = plugin.addElement("version");
        version.setText("0.8.2");
        var executions = plugin.addElement("executions");
        var execution1 = executions.addElement("execution");
        var goals1 = execution1.addElement("goals");
        var goal1 = goals1.addElement("goal");
        goal1.setText("prepare-agent");
        var execution2 = executions.addElement("execution");
        var id2 = execution2.addElement("id");
        id2.setText("report");
        var phase = execution2.addElement("phase");
        phase.setText("test");
        var goals2 = execution2.addElement("goals");
        var goal2 = goals2.addElement("goal");
        goal2.setText("report");
        var document = pomRoot.getDocument();
        var writer = new OutputStreamWriter(new FileOutputStream(pomPath.toFile()));
        document.write(writer);
        writer.close();
        return pomContent;
    }

    private boolean runMvnTest(Path root) throws IOException, InterruptedException {
        var cmd = new String[] {
                "bash",
                "-c",
                "mvn clean test"
        };
        var process = new ProcessBuilder(cmd).directory(root.toFile()).start();
        return process.waitFor(3, TimeUnit.MINUTES);
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
    protected Set<Path> extractModuleRoots(final Path root) throws DocumentException {
        var moduleRoots = new HashSet<Path>();

        var reader = new SAXReader();
        var document = reader.read(Path.of(root.toAbsolutePath().toString(), "pom.xml").toFile());
        var rootElement = document.getRootElement();

        var modules = rootElement.selectSingleNode("./*[local-name()='modules']");

        if (modules == null) {
            moduleRoots.add(root);
            return moduleRoots;
        }

        var moduleNames = modules.selectNodes("./*[local-name()='module']")
                .stream()
                .map(m -> Path.of(root.toAbsolutePath().toString(), m.getText()))
                .collect(Collectors.toList());

        for (var module : moduleNames) {
            moduleRoots.addAll(extractModuleRoots(module));
        }

        return moduleRoots;
    }
}
