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
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.merge.CallGraphUtils;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.json.JSONTokener;
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

	@CommandLine.Option(names = { "-o",
			"--output" }, paramLabel = "OUT", description = "Output directory path", defaultValue = "")
	String output;

	@CommandLine.Option(names = { "-r" }, paramLabel = "REPOS", description = "Maven repositories", split = ",")
	List<String> repos;

	@CommandLine.Option(names = { "-a",
			"--artifact" }, paramLabel = "ARTIFACT", description = "Artifact, Maven coordinate or file path")
	String artifact;

	@CommandLine.Option(names = { "-an" }, paramLabel = "ARTIFACT_NAME")
	String artifactName;

	@CommandLine.Option(names = { "-i",
			"--input-type" }, paramLabel = "INPUTTYPE", description = "Input of algorithms " +
		"are {FILE or COORD}", defaultValue = "FILE")
	String inputType;

	@CommandLine.Option(names = { "-ga",
			"--genAlgorithm" }, paramLabel = "GenALG", description = "gen{RTA,CHA,AllocationSiteBasedPointsTo,TypeBasedPointsTo}", defaultValue = "CHA")
	String genAlgorithm;

	@CommandLine.Option(names = { "-t",
			"--timestamp" }, paramLabel = "TS", description = "Release TS", defaultValue = "-1")
	String timestamp;

	@CommandLine.Option(names = { "-g",
			"--generate" }, paramLabel = "GEN", description = "Generate call graph for artifact")
	boolean doGenerate;

	@CommandLine.Option(names = { "-m",
			"--merge" }, paramLabel = "MERGE", description = "Merge artifact CG to dependencies", required = true)
	boolean doMerge;

	@CommandLine.Option(names = { "-d",
			"--dependencies" }, paramLabel = "DEPS", description = "Dependencies, coordinates or files", split = ",")
	List<String> dependencies;

	@CommandLine.Option(names = { "-dn",
		"--dependencyNames" }, paramLabel = "DEPNAMES", description = "Dependency names", split = ",")
	List<String> dependencyNames;

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
		if (doGenerate) {
			runGenerate();
		}
		if (doMerge) {
			runMerge();
		}

	}

	/**
	 * Run call graph generator.
	 */
	private void runGenerate() {
		boolean writeToFile = !this.output.isEmpty();

		if (inputType.equals("COORD")) {
			final var artifact = getArtifactNameCoordinate();
			logger.info("Generating call graph for the Maven coordinate: {}", artifact.getCoordinate());
			generate(artifact, artifactName, getCGAlgorithm(), writeToFile);
		} else if (inputType.equals("FILE")) {
			File artifactFile = getArtifactFile();
			logger.info("Generating call graph for artifact file: {}", artifactFile);
			generate(artifactFile, artifactName, getCGAlgorithm(), writeToFile);
		}

	}

	/**
	 * Run merge algorithm.
	 */
	private void runMerge() {
		switch (inputType){
			case "JSON":
				merge(deserializeArtifact(artifact), deserializeDeps(dependencies));
				break;
			case "COORD":
				merge(getArtifactNameCoordinate(), getDependenciesCoordinates());
				break;
			case "FILE":
				merge(getArtifactFile(), getDependenciesFiles());
				break;
		}
	}

	private List<PartialJavaCallGraph> deserializeDeps(List<String> dependencies) {
		List<PartialJavaCallGraph> result = new ArrayList<>();
		for (final var dependency : dependencies) {
			result.add(deserializeArtifact(dependency));
		}
		return result;
	}

	private PartialJavaCallGraph deserializeArtifact(String artifact) {
		try {
			var cg = new JSONTokener(new FileReader(new File(artifact)));
			return new PartialJavaCallGraph(new JSONObject(cg));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
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
	 */
	public <T> DirectedGraph merge(final T artifact, final List<T> dependencies) {
		final long startTime = System.currentTimeMillis();
		final DirectedGraph result;
		final List<PartialJavaCallGraph> deps = new ArrayList<>();
		if (artifact instanceof PartialJavaCallGraph){
			deps.add((PartialJavaCallGraph) artifact);
			deps.addAll((Collection<PartialJavaCallGraph>) dependencies);
		} else {
			deps.addAll(generatePCGs(artifact, dependencies));
		}

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
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

			}
		}

		return result;
	}

	@NotNull
	private <T> ArrayList<PartialJavaCallGraph> generatePCGs(T artifact,
															 List<T> dependencies) {
		final var deps = new ArrayList<PartialJavaCallGraph>();
		for (int i = 0; i < dependencies.size(); i++) {
			T dep = dependencies.get(i);
			deps.add(generate(dep, dependencyNames.get(i), getCGAlgorithm(), true));
		}
		final var art = generate(artifact, artifactName, getCGAlgorithm(), true);
		deps.add(art);
		return deps;
	}

	private String getPath(String fileName) {
		var dir = Paths.get(this.output).toString();
		if (!dir.endsWith(File.separator)) {
			dir = dir + File.separator;
		}
		return Paths.get(dir, fileName).toString();
	}

	private CGAlgorithm getCGAlgorithm() {
		return CGAlgorithm.valueOf(genAlgorithm);
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
	 */
	public <T> PartialJavaCallGraph generate(final T artifact, final String artifactName,
											 final CGAlgorithm algorithm,
											 final boolean writeToFile) {
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
					algorithm, Long.parseLong(timestamp),
					(repos == null || repos.size() < 1) ? MavenUtilities.MAVEN_CENTRAL_REPO : repos.get(0),
					CallPreservationStrategy.INCLUDING_ALL_SUBTYPES);
		}

		logger.info("Generated the call graph in {} seconds.",
				new DecimalFormat("#0.000").format((System.currentTimeMillis() - startTime) / 1000d));

		if (writeToFile) {
			try {
				CallGraphUtils.writeToFile(getPath(revisionCallGraph.getRevisionName()),
					JSONUtils.toJSONString(revisionCallGraph),"");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

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
		if (dependencies != null) {
			for (String currentCoordinate : dependencies) {
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
		if (dependencies != null) {
			for (String currentCoordinate : dependencies) {
				MavenCoordinate coordinate = getMavenCoordinate(currentCoordinate);
				if (repos != null && !repos.isEmpty()) {
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
		if (artifact != null) {
			result = new File(artifact);
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
		if (artifact != null) {
			result = MavenCoordinate.fromString(artifactName, "jar");
			if (this.repos != null && !this.repos.isEmpty()) {
				result.setMavenRepos(this.repos);
			}
		}
		return result;
	}
}
