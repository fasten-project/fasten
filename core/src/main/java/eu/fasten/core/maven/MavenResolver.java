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

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Revision;
import eu.fasten.core.maven.utils.MavenUtilities;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;

@CommandLine.Command(name = "MavenResolver")
public class MavenResolver implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MavenResolver.class);

    @CommandLine.Option(names = {"-a", "--artifactId"},
            paramLabel = "ARTIFACT",
            description = "artifactId of the Maven coordinate")
    protected String artifact;

    @CommandLine.Option(names = {"-g", "--groupId"},
            paramLabel = "GROUP",
            description = "groupId of the Maven coordinate")
    protected String group;

    @CommandLine.Option(names = {"-v", "--version"},
            paramLabel = "VERSION",
            description = "version of the Maven coordinate")
    protected String version;

    @CommandLine.Option(names = {"-t", "--timestamp"},
            paramLabel = "TS",
            description = "Timestamp for resolution",
            defaultValue = "-1")
    protected long timestamp;

    @CommandLine.Option(names = {"-d", "--database"},
            paramLabel = "DB_URL",
            description = "Database URL for connection",
            defaultValue = "jdbc:postgresql:postgres")
    protected String dbUrl;

    @CommandLine.Option(names = {"-u", "--user"},
            paramLabel = "DB_USER",
            description = "Database user name",
            defaultValue = "postgres")
    protected String dbUser;

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "COORD_FILE",
            description = "Path to file with the list of coordinates for resolution")
    protected String file;

    @CommandLine.Option(names = {"-sk", "--skip"},
            description = "Skip first line in the file")
    protected boolean skipLine;

    public static void main(String[] args) {
        final int exitCode = new CommandLine(new MavenResolver()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        DSLContext dbContext = null;

        // Database connection is needed only if using filtering by timestamp
        if (timestamp != -1) {
            try {
                dbContext = PostgresConnector.getDSLContext(dbUrl, dbUser);
            } catch (SQLException e) {
                logger.error("Could not connect to the database", e);
                return;
            }
        }
        if (artifact != null && group != null && version != null) {
            Set<Revision> dependencySet;
            try {
                dependencySet = this.resolveFullDependencySetOnline(group, artifact, version, timestamp, dbContext);
            } catch (Exception e) {
                logger.error("Could not resolve dependencies of " + group + Constants.mvnCoordinateSeparator + artifact
                        + Constants.mvnCoordinateSeparator + version, e);
                return;
            }
            logger.info("--------------------------------------------------");
            logger.info("Maven coordinate:");
            logger.info(group + Constants.mvnCoordinateSeparator + artifact
                    + Constants.mvnCoordinateSeparator + version);
            logger.info("--------------------------------------------------");
            logger.info("Found " + dependencySet.size() + " (transitive) dependencies"
                    + (dependencySet.size() > 0 ? ":" : "."));
            dependencySet.forEach(d -> logger.info(d.toString()));
            logger.info("--------------------------------------------------");
        } else if (file != null) {
            List<String> coordinates;
            try {
                coordinates = Files.readAllLines(Paths.get(file));
            } catch (IOException e) {
                logger.error("Could not read from file: " + file, e);
                return;
            }
            if (skipLine) {
                coordinates.remove(0);
            }
            float success = 0;
            float total = 0;
            var errors = new HashMap<Dependency, Throwable>();
            for (var coordinate : coordinates) {
                Set<Revision> dependencySet;
                var coordParts = coordinate.split(Constants.mvnCoordinateSeparator);
                var artifact = new Dependency(coordParts[0], coordParts[1], coordParts[2]);
                total++;
                try {
                    dependencySet = this.resolveFullDependencySetOnline(artifact.groupId,
                            artifact.artifactId, artifact.getVersion(), timestamp, dbContext);
                    success++;
                    logger.info("--------------------------------------------------");
                    logger.info("Maven coordinate:");
                    logger.info(artifact.toCanonicalForm());
                    logger.info("--------------------------------------------------");
                    logger.info("Found " + dependencySet.size() + " (transitive) dependencies"
                            + (dependencySet.size() > 0 ? ":" : "."));
                    dependencySet.forEach(d -> logger.info(d.toString()));
                    logger.info("--------------------------------------------------");
                } catch (Exception e) {
                    logger.error("Error resolving " + artifact.toMavenCoordinate(), e);
                    errors.put(artifact, e);
                }
            }
            logger.info("Finished resolving Maven coordinates");
            logger.info("Success rate is " + success / total + " for " + (int) total + " coordinates");
            if (!errors.isEmpty()) {
                logger.info("Errors encountered:");
                errors.forEach((key, value) -> logger.info(key.toFullCanonicalForm() + " -> " + value.getMessage()));
            }
        } else {
            logger.error("You need to specify Maven coordinate by providing its "
                    + "artifactId ('-a'), groupId ('-g') and version ('-v'). "
                    + "Optional timestamp (-t) can also be provided. "
                    + "Otherwise you need to specify file (-f) which contains "
                    + "the list of coordinates you want to resolve");
        }
    }

    /**
     * Filters dependency versions by timestamp.
     * If the version of some dependency in the given dependency set was released later
     * than the provided timestamp then this dependency version will be downgraded
     * to use latest version which was release earlier or at the given timestamp.
     *
     * @param dependencies Set of dependencies to check
     * @param timestamp    Timestamp to filter by
     * @param context      Database connection context
     * @return same dependency set as given one
     * but with dependency versions released no later than the given timestamp
     */
    public Set<Revision> filterDependenciesByTimestamp(Set<Revision> dependencies,
                                                         Timestamp timestamp, DSLContext context) {
        var filteredDependencies = new HashSet<Revision>(dependencies.size());
        for (var dependency : dependencies) {
            var packageName = dependency.groupId + Constants.mvnCoordinateSeparator + dependency.artifactId;
            var result = context.select(PackageVersions.PACKAGE_VERSIONS.VERSION)
                    .from(PackageVersions.PACKAGE_VERSIONS)
                    .join(Packages.PACKAGES)
                    .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                    .where(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                    .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                    .and(PackageVersions.PACKAGE_VERSIONS.CREATED_AT.lessOrEqual(timestamp))
                    .orderBy(PackageVersions.PACKAGE_VERSIONS.CREATED_AT.desc())
                    .limit(1)
                    .fetchOne();
            String suitableVersion = null;
            if (result != null) {
                suitableVersion = result.value1();
            }
            if (suitableVersion == null) {
                filteredDependencies.add(dependency);
            } else {
                filteredDependencies.add(
                        new Revision(dependency.groupId, dependency.artifactId, suitableVersion, new Timestamp(-1))
                );
            }
        }
        return filteredDependencies;
    }

    /**
     * Resolves full dependency set online.
     *
     * @param group     Group id of the artifact to resolve
     * @param artifact  Artifact id of the artifact to resolve
     * @param version   Version of the artifact to resolve
     * @param timestamp Timestamp for filtering dependency versions (-1 in order not to filter)
     * @param dbContext Database connection context (needed only for filtering by timestamp)
     * @return A dependency set (including all transitive dependencies) of given Maven coordinate
     * @throws NoSuchElementException if pom file was not retrieved
     */
    public Set<Revision> resolveFullDependencySetOnline(String group, String artifact, String version,
                                                          long timestamp, DSLContext dbContext) throws FileNotFoundException {
        Set<Revision> dependencySet = new HashSet<>();

        logger.debug("Downloading artifact's POM file");

        var pomOpt = MavenUtilities.downloadPom(group, artifact, version);
        var pom = pomOpt.orElseGet(() -> {
            throw new RuntimeException("Could not download pom file");
        });
        if (!pom.exists()) {
            throw new FileNotFoundException("Could not find file: " + pom.getAbsolutePath());
        }

        File outputFile = null;
        try {
            logger.debug("Running 'mvn dependency:list'");

            outputFile = Files.createTempFile("fasten", ".txt").toFile();

            String[] cmd = new String[]{
                    "mvn",
                    "dependency:list",
                    "-f", "\"" + pom.getAbsolutePath() + "\"",
                    "-DoutputFile=" + outputFile.getAbsolutePath()
            };
            var process = new ProcessBuilder(cmd).start();
            final var timeoutSeconds = 30;
            var completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                MavenUtilities.forceDeleteFile(pom);
                throw new RuntimeException("Maven resolution process timed out after "
                        + timeoutSeconds + " seconds");
            }
            var exitValue = process.exitValue();

            var stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            var stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            var bufferStr = "";

            logger.debug("Maven resolution finished with exit code " + exitValue);
            if (exitValue != 0) {
                while ((bufferStr = stdInput.readLine()) != null) {
                    logger.debug(bufferStr);
                }
                while ((bufferStr = stdError.readLine()) != null) {
                    logger.error("ERROR: " + bufferStr);
                }
                MavenUtilities.forceDeleteFile(pom);
                throw new RuntimeException("Maven resolution failed with exit code " + exitValue);
            }

            logger.debug("Parsing Maven resolution output");
            cmd = new String[]{
                    "bash",
                    "-c",
                    "cat \"" + outputFile.getAbsolutePath() + "\" " +
                            "| tail -n +3 | grep . | sed -e \"s/^[[:space:]]*//\" | sort | uniq " +
                            "| grep -E \":compile$|:provided$|:runtime$|:test$|:system$|:import$\""};
            process = new ProcessBuilder(cmd).start();
            exitValue = process.waitFor();
            stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            if (exitValue != 0) {
                while ((bufferStr = stdInput.readLine()) != null) {
                    logger.debug(bufferStr);
                }
                while ((bufferStr = stdError.readLine()) != null) {
                    logger.error("ERROR: " + bufferStr);
                }
                MavenUtilities.forceDeleteFile(pom);
                throw new RuntimeException("Maven resolution output parsing failed with exit code " + exitValue);
            }
            logger.debug("Maven resolution output parsing finished with exit code " + exitValue);

            while ((bufferStr = stdInput.readLine()) != null) {
                var coordinates = bufferStr.split(Constants.mvnCoordinateSeparator);
                try {
                    dependencySet.add(new Revision(coordinates[0], coordinates[1], coordinates[3], new Timestamp(-1)));
                } catch (ArrayIndexOutOfBoundsException e) {
                    logger.error("Error parsing {} to a Maven coordinate", bufferStr, e);
                }
            }

            if (timestamp != -1) {
                dependencySet = this.filterDependenciesByTimestamp(
                        dependencySet, new Timestamp(timestamp), dbContext);
            }

        } catch (IOException | InterruptedException e) {
            var coordinate = artifact + Constants.mvnCoordinateSeparator + group + Constants.mvnCoordinateSeparator + version;
            logger.error("Error resolving Maven artifact: " + coordinate, e);
        } finally {
            MavenUtilities.forceDeleteFile(pom);
            MavenUtilities.forceDeleteFile(outputFile);
        }
        return dependencySet;
    }

    /**
     * Resolves full dependency set online using ShrinkWrap's MavenResolver.
     *
     * @param group    Group id of the artifact to resolve
     * @param artifact Artifact id of the artifact to resolve
     * @param version  Version of the artifact to resolve
     * @return A dependency set (including all transitive dependencies) of given Maven coordinate
     */
    public Set<Revision> resolveFullDependencySetOnline(String group, String artifact, String version) throws FileNotFoundException {
        return resolveFullDependencySetOnline(artifact, group, version, -1, null);
    }
}
