/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.core.maven.runners;

import eu.fasten.core.maven.resolution.NativeMavenResolver;
import picocli.CommandLine;

@CommandLine.Command(name = "MavenResolver")
public class MavenNativeResolverRunner implements Runnable {

    @CommandLine.Option( //
            names = { "-c", "--coordinate" }, //
            paramLabel = "Maven coordinate", //
            description = "Maven coordinate for resolution", //
            required = true)
    protected String coordinate;

    @CommandLine.Option( //
            names = { "-d", "--direct-deps-only" }, //
            paramLabel = "Only direct dependencies", //
            description = "Do not resolve transitive dependencies if set", //
            defaultValue = "false")
    protected boolean onlyDirectDependencies;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new NativeMavenResolver()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        var deps = new NativeMavenResolver().resolveDependencies(this.coordinate, this.onlyDirectDependencies);
        if (deps != null) {
            System.out.println("The dependencies of " + this.coordinate + " are:");
            deps.forEach(System.out::println);
        }
    }
}