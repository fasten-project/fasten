package eu.fasten.analyzer.javacgopal.version3.data;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opalj.br.analyses.Project;
import org.opalj.tac.cg.AllocationSiteBasedPointsToCallGraphKey$;
import org.opalj.tac.cg.CHACallGraphKey$;
import org.opalj.tac.cg.CTACallGraphKey$;
import org.opalj.tac.cg.CallGraph;
import org.opalj.tac.cg.FTACallGraphKey$;
import org.opalj.tac.cg.MTACallGraphKey$;
import org.opalj.tac.cg.RTACallGraphKey$;
import org.opalj.tac.cg.TypeBasedPointsToCallGraphKey$;
import org.opalj.tac.cg.XTACallGraphKey$;

public class CallGraphConstructor {

    private final Project<URL> project;
    private final CallGraph callGraph;

    /**
     * Constructs a call graph given file, algorithm and a main class in case of application.
     *
     * @param file      file of the package to analyze
     * @param mainClass main class of the package in case of application
     * @param algorithm algorithm for generating call graph
     */
    public CallGraphConstructor(final File file, final String mainClass, final String algorithm) {
        final var log = Project.apply(file);
        this.project = Project.apply(file, log.logContext(), createConfig(mainClass));
        this.callGraph = generateCallGraph(project, algorithm);
    }

    public Project<URL> getProject() {
        return project;
    }

    public CallGraph getCallGraph() {
        return callGraph;
    }

    /**
     * Get configuration for call graph generator.
     *
     * @param mainClass main class in case the project is an application
     * @return configuration for running call graph generator
     */
    private static Config createConfig(String mainClass) {
        Config baseConfig = ConfigFactory.load()
                .withValue("org.opalj.br.reader.ClassFileReader.Invokedynamic.rewrite",
                        ConfigValueFactory.fromAnyRef(true));

        Config config;
        if (mainClass == null || mainClass.isEmpty()) {
            config = baseConfig
                    .withValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
                            ConfigValueFactory
                                    .fromAnyRef("org.opalj.br.analyses.cg.LibraryEntryPointsFinder"))

                    .withValue("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
                            ConfigValueFactory
                                    .fromAnyRef("org.opalj.br.analyses.cg.LibraryInstantiatedTypesFinder")
                    );
        } else {
            config = baseConfig
                    .withValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.analysis",
                            ConfigValueFactory
                                    .fromAnyRef("org.opalj.br.analyses.cg.ConfigurationEntryPointsFinder"))

                    .withValue("org.opalj.br.analyses.cg.InitialEntryPointsKey.entryPoints",
                            ConfigValueFactory.fromIterable(Stream.of(ConfigValueFactory.fromMap(
                                    Map.of("declaringClass", mainClass.replace('.', '/'), "name", "main")))
                                    .collect(Collectors.toList())))

                    .withValue("org.opalj.br.analyses.cg.InitialInstantiatedTypesKey.analysis",
                            ConfigValueFactory.fromAnyRef("org.opalj.br.analyses.cg.ApplicationInstantiatedTypesFinder"));
        }

        return config;
    }

    /**
     * Generates a call graph for a given project using given algorithm.
     *
     * @param project {@link Project} the project to generate call graph for
     * @return {@link CallGraph} resulting call graph
     */
    private static CallGraph generateCallGraph(final Project<?> project,
                                               final String algorithm) {
        final CallGraph result;
        switch (algorithm) {
            case "RTA":
                result = project.get(RTACallGraphKey$.MODULE$);
                break;
            case "CHA":
                result = project.get(CHACallGraphKey$.MODULE$);
                break;
            case "AllocationSiteBasedPointsTo":
                result = project.get(AllocationSiteBasedPointsToCallGraphKey$.MODULE$);
                break;
            case "TypeBasedPointsTo":
                result = project.get(TypeBasedPointsToCallGraphKey$.MODULE$);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + algorithm);
        }
        return result;
    }
}
