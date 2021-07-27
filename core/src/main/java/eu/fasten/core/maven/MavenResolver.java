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

package eu.fasten.core.maven;

import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Pattern;

@CommandLine.Command(name = "MavenResolver")
public class MavenResolver implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MavenResolver.class);

    @CommandLine.Option(names = {"-c", "--coordinate"},
            required = true,
            paramLabel = "Maven coordinate",
            description = "Maven coordinate for resolution")
    protected String coordinate;

    @CommandLine.Option(names = {"-d", "--direct-deps-only"},
            paramLabel = "Only direct dependencies",
            description = "Do not resolve transitive dependencies if set",
            defaultValue = "false")
    protected boolean onlyDirectDependencies;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new MavenResolver()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var deps = resolveDependencies(this.coordinate, this.onlyDirectDependencies);
        if (deps != null) {
            System.out.println("The dependencies of " + this.coordinate + " are:");
            deps.forEach(System.out::println);
        }
    }

    public Set<Revision> resolveDependencies(String mavenCoordinate) {
        return this.resolveDependencies(mavenCoordinate, false);
    }

    public Set<Revision> resolveDependencies(String mavenCoordinate, boolean onlyDirectDependencies) {
        try {
            var parts = mavenCoordinate.split(":");
            var pomFile = MavenUtilities.downloadPom(parts[0], parts[1], parts[2]);
            if (pomFile.isEmpty()) {
                logger.error("Could not download POM file of {}", mavenCoordinate);
                return null;
            }
            return getDependencies(pomFile.get(), onlyDirectDependencies);
        } catch (MavenInvocationException | IOException e) {
            logger.error("Error resolving dependencies for {}", mavenCoordinate, e);
            return null;
        }
    }

    public Set<Revision> getDependencies(File pomFile, boolean onlyDirectDependencies) throws MavenInvocationException, IOException {
        Set<Revision> deps;
        File outputFile = Files.createTempFile("deps", ".txt").toFile();
        Properties properties = new Properties();
        properties.setProperty("excludeTransitive", Boolean.toString(onlyDirectDependencies));
        properties.setProperty("includeParents", "true");
        properties.setProperty("excludeReactor", "false");
        InvocationResult mvnInvocation = invokeMavenDependencyList(pomFile, outputFile, properties);
        try {
            if(mvnInvocation.getExitCode() == 0) {
                deps = new HashSet<>(parseMavenDependencyList(outputFile));
            }
            else {
                throw new MavenInvocationException("Maven dependency:list failed with exit code " +
                        mvnInvocation.getExitCode(), mvnInvocation.getExecutionException());
            }
        }
        finally {
            MavenUtilities.forceDeleteFile(outputFile);
        }
        return deps;
    }

    private InvocationResult invokeMavenDependencyList(File pomFile, File outputFile, Properties properties)
            throws IOException, MavenInvocationException {
        var output = new PrintStreamHandler(new PrintStream(outputFile), true);
        var request = new DefaultInvocationRequest();
        request.setPomFile(pomFile);
        request.setGoals(Collections.singletonList("dependency:list"));
        request.setProperties(properties);
        request.setOutputHandler(output);
        request.setBatchMode(true);
        var invoker = new DefaultInvoker();
        return (invoker.execute(request));
    }

    private Set<Revision> parseMavenDependencyList(File outputFile) throws IOException {
        Set<Revision> deps = new HashSet<>();
        var scanner = new Scanner(outputFile);
        var pat = Pattern.compile(
                "\\[INFO]\\s*(?<groupId>[\\w.\\-]+):" +
                        "(?<artifactId>[\\w.\\-]+):" +
                        "(?<artifactType>[\\w.\\-]+):" +
                        "(?<version>[\\w.\\-]+):" +
                        "(?<scope>[\\w.\\-]+)\\s*");
        scanner.findAll(pat).forEach((m) ->
                deps.add(new Revision(m.group(1), m.group(2), m.group(4), new Timestamp(-1))));
        scanner.close();
        return deps;
    }
}
