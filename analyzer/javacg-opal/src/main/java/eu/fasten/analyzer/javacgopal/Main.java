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

package eu.fasten.analyzer.javacgopal;

import eu.fasten.analyzer.javacgopal.data.CGAlgorithm;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraphConstructor;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.OPALPartialCallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.CallPreservationStrategy;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.merge.CallGraphUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes javacg-opal module runnable from command line.
 */
@CommandLine.Command(name = "JavaCGOpal", mixinStandardHelpOptions = true)
public class Main implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(Main.class);

	@CommandLine.ArgGroup(multiplicity = "1")
	Commands commands;

	@CommandLine.Option(names = { "-o",
			"--output" }, paramLabel = "OUT", description = "Output directory path", defaultValue = "")
	String output;

	@CommandLine.Option(names = { "-r" }, paramLabel = "REPOS", description = "Maven repositories", split = ",")
	List<String> repos;

	static class Commands {
		@CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
		Computations computations;
	}

	static class Computations {
		@CommandLine.Option(names = { "-a",
				"--artifact" }, paramLabel = "ARTIFACT", description = "Artifact, Maven coordinate or file path")
		String artifact;

		@CommandLine.Option(names = { "-an" }, paramLabel = "ARTIFACT_NAME")
		String artifactName;

		@CommandLine.Option(names = { "-i",
				"--input-type" }, paramLabel = "MODE", description = "Input of algorithms are {FILE or COORD}", defaultValue = "FILE")
		String mode;

		@CommandLine.Option(names = { "-ga",
				"--genAlgorithm" }, paramLabel = "GenALG", description = "gen{RTA,CHA,AllocationSiteBasedPointsTo,TypeBasedPointsTo}", defaultValue = "CHA")
		String genAlgorithm;

		@CommandLine.ArgGroup(multiplicity = "1")
		Tools tools;

		@CommandLine.Option(names = { "-t",
				"--timestamp" }, paramLabel = "TS", description = "Release TS", defaultValue = "-1")
		String timestamp;
	}

	static class Tools {
		@CommandLine.ArgGroup(exclusive = false)
		Opal opal;

		@CommandLine.ArgGroup(exclusive = false)
		Merge merge;
	}

	static class Opal {
		@CommandLine.Option(names = { "-g",
				"--generate" }, paramLabel = "GEN", description = "Generate call graph for artifact")
		boolean doGenerate;
	}

	static class Merge {
		@CommandLine.Option(names = { "-m",
				"--merge" }, paramLabel = "MERGE", description = "Merge artifact CG to dependencies", required = true)
		boolean doMerge;

		@CommandLine.Option(names = { "-d",
				"--dependencies" }, paramLabel = "DEPS", description = "Dependencies, coordinates or files", split = ",")
		List<String> dependencies;

		@CommandLine.Option(names = { "-dn",
			"--dependencyNames" }, paramLabel = "DEPNAMES", description = "Dependency names", split = ",")
		List<String> dependencyNames;
	}

	/**
	 * Generates RevisionCallGraphs using Opal for the specified artifact in the
	 * command line parameters.
	 */
	public static void main(String[] args) {
		new CommandLine(new Main()).execute(args);
	}

	/**
	 * Run the generator, merge algorithm or evaluator depending on parameters
	 * provided.
	 */
	public void run() {
		if (this.commands.computations != null && this.commands.computations.tools != null) {
			if (this.commands.computations.tools.opal != null && this.commands.computations.tools.opal.doGenerate) {
				runGenerate();
			}
			if (this.commands.computations.tools.merge != null && this.commands.computations.tools.merge.doMerge) {
				runMerge();
			}
		}
	}

	/**
	 * Run call graph generator.
	 */
	private void runGenerate() {
		boolean writeToFile = !this.output.isEmpty();

		try {
			if (commands.computations.mode.equals("COORD")) {
				final var artifact = getArtifactNameCoordinate();
				logger.info("Generating call graph for the Maven coordinate: {}", artifact.getCoordinate());
				generate(artifact, commands.computations.artifactName, getCGAlgorithm(), writeToFile);
			} else if (commands.computations.mode.equals("FILE")) {
				File artifactFile = getArtifactFile();
				logger.info("Generating call graph for artifact file: {}", artifactFile);
				generate(artifactFile, commands.computations.artifactName, getCGAlgorithm(), writeToFile);
			}
		} catch (IOException | OPALException | MissingArtifactException e) {
			logger.error("Call graph couldn't be generated", e);
		}
	}

	/**
	 * Run merge algorithm.
	 */
	private void runMerge() {
		if (commands.computations.mode.equals("COORD")) {
			try {
				merge(getArtifactNameCoordinate(), getDependenciesCoordinates());
			} catch (IOException | OPALException | MissingArtifactException e) {
				logger.error("Call graph couldn't be merge for coord: {}", getArtifactNameCoordinate().getCoordinate(), e);
			}

		} else if (commands.computations.mode.equals("FILE")) {
			try {
				merge(getArtifactFile(), getDependenciesFiles());
			} catch (IOException | OPALException | MissingArtifactException e) {
				logger.error("Call graph couldn't be generated for file: {}", getArtifactFile().getName(), e);
			}
		}
	}

	/**
	 * Merge an artifact with a list of it's dependencies using a specified
	 * algorithm.
	 *
	 * @param artifact     artifact to merge
	 * @param dependencies list of dependencies
	 * @param <T>          artifact can be either a file or coordinate
	 * @return a revision call graph with resolved class hierarchy and calls
	 * @throws IOException thrown in case file related exceptions occur, e.g
	 *                     FileNotFoundException
	 */
	public <T> DirectedGraph merge(final T artifact, final List<T> dependencies)
			throws IOException, MissingArtifactException {
		final long startTime = System.currentTimeMillis();
		final DirectedGraph result;
		final var deps = new ArrayList<PartialJavaCallGraph>();
		var depNames = commands.computations.tools.merge.dependencyNames;
		for (int i = 0; i < dependencies.size(); i++) {
			T dep = dependencies.get(i);
			deps.add(generate(dep, depNames.get(i), getCGAlgorithm(), true));
		}
		final var art = generate(artifact, commands.computations.artifactName, getCGAlgorithm(), true);
		deps.add(art);
		final var merger = new CGMerger(deps);
		result = merger.mergeAllDeps();

		if (result != null) {
			logger.info("Resolved {} nodes, {} calls in {} seconds", result.nodes().size(), result.edgeSet().size(),
					new DecimalFormat("#0.000").format((System.currentTimeMillis() - startTime) / 1000d));
			if (!this.output.isEmpty()) {
				try {
					CallGraphUtils.writeToFile(
						getPath(getArtifactNameCoordinate().getCoordinate() + "_merged"),
						JSONUtils.toJSONString(result, getArtifactNameCoordinate()), "");
				} catch (NullPointerException e) {
					logger.error("Provided output path might be incomplete!");
				}
			}
		}

		return result;
	}

	private String getPath(String fileName) {
		final var dir = Paths.get(this.output).toString()+File.separator;
		return Paths.get(dir, fileName).toString();
	}

	private CGAlgorithm getCGAlgorithm() {
		return CGAlgorithm.valueOf(commands.computations.genAlgorithm);
	}

	/**
	 * Generate a revision call graph for a given coordinate using a specified
	 * algorithm. In case the artifact is an application a main class can be
	 * specified. If left empty a library entry point finder algorithm will be used.
	 *
	 * @param artifact    artifact to generate a call graph for
	 * @param artifactName   main class in case the artifact is an application
	 * @param algorithm   algorithm for generating a call graph
	 * @param writeToFile will be written to a file if true
	 * @param <T>         artifact can be either a file or a coordinate
	 * @return generated revision call graph
	 * @throws IOException file related exceptions, e.g. FileNotFoundException
	 */
	public <T> PartialJavaCallGraph generate(final T artifact, final String artifactName,
											 final CGAlgorithm algorithm,
											 final boolean writeToFile) throws MissingArtifactException, IOException {
		final PartialJavaCallGraph revisionCallGraph;

		final long startTime = System.currentTimeMillis();

		if (artifact instanceof File) {
			logger.info("Generating graph for {}", ((File) artifact).getAbsolutePath());
			final var cg = new OPALPartialCallGraphConstructor().construct(new OPALCallGraphConstructor().construct((File) artifact, algorithm), CallPreservationStrategy.ONLY_STATIC_CALLSITES);

			MavenCoordinate coord = getMavenCoordinate(artifactName);
			revisionCallGraph = new PartialJavaCallGraph("mvn", coord.getProduct(),
				coord.getVersionConstraint(), 0, "OPAL", cg.classHierarchy, cg.graph);
		} else {
			revisionCallGraph = OPALPartialCallGraphConstructor.createExtendedRevisionJavaCallGraph((MavenCoordinate) artifact,
					algorithm, Long.parseLong(commands.computations.timestamp),
					(repos == null || repos.size() < 1) ? MavenUtilities.MAVEN_CENTRAL_REPO : repos.get(0),
					CallPreservationStrategy.INCLUDING_ALL_SUBTYPES);
		}

		logger.info("Generated the call graph in {} seconds.",
				new DecimalFormat("#0.000").format((System.currentTimeMillis() - startTime) / 1000d));

		if (writeToFile) {
			CallGraphUtils.writeToFile(getPath(revisionCallGraph.getRevisionName()),
				JSONUtils.toJSONString(revisionCallGraph),"");

		}
		return revisionCallGraph;
	}

	private MavenCoordinate getMavenCoordinate(String artifactCoordinate) {
		if (artifactCoordinate == null || artifactCoordinate.isEmpty()) {
			throw new RuntimeException("coordinate is not specified check the arguments!");
		}
		return MavenCoordinate.fromString(artifactCoordinate, "jar");
	}

	/**
	 * Get a list of files of dependencies.
	 *
	 * @return a list of dependencies files
	 */
	private List<File> getDependenciesFiles() {
		final var result = new ArrayList<File>();
		if (this.commands.computations.tools.merge.dependencies != null) {
			for (String currentCoordinate : this.commands.computations.tools.merge.dependencies) {
				result.add(new File(currentCoordinate));
			}
		}
		return result;
	}

	/**
	 * Get a list of coordinates of dependencies.
	 *
	 * @return a list of Maven coordinates
	 */
	private List<MavenCoordinate> getDependenciesCoordinates() {
		final var result = new ArrayList<MavenCoordinate>();
		if (this.commands.computations.tools.merge.dependencies != null) {
			for (String currentCoordinate : this.commands.computations.tools.merge.dependencies) {
				MavenCoordinate coordinate = getMavenCoordinate(currentCoordinate);
				if (this.repos != null && !this.repos.isEmpty()) {
					coordinate.setMavenRepos(this.repos);
				}
				result.add(coordinate);
			}
		}
		return result;
	}

	/**
	 * Get an artifact file from a provided path.
	 *
	 * @return artifact file
	 */
	private File getArtifactFile() {
		File result = null;
		if (this.commands.computations.artifact != null) {
			result = new File(this.commands.computations.artifact);
		}
		return result;
	}

	/**
	 * Get an artifact coordinate for an artifact.
	 *
	 * @return artifact coordinate
	 */
	private MavenCoordinate getArtifactNameCoordinate() {
		MavenCoordinate result = null;
		if (this.commands.computations.artifact != null) {
			result = MavenCoordinate.fromString(this.commands.computations.artifactName, "jar");
			if (this.repos != null && !this.repos.isEmpty()) {
				result.setMavenRepos(this.repos);
			}
		}
		return result;
	}
}
