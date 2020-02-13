package eu.fasten.analyzer.javacgwala.data.callgraph;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.util.config.AnalysisScopeReader;
import eu.fasten.analyzer.javacgwala.data.MavenResolvedCoordinate;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallGraphConstructor {

    private static Logger logger = LoggerFactory.getLogger(CallGraphConstructor.class);

    /**
     * Build a {@link PartialCallGraph} given classpath.
     *
     * @param classpath Classpath
     * @return Partial call graph
     */
    public static PartialCallGraph build(String classpath) throws FileNotFoundException {
        var classPaths = buildClasspath(classpath);
        logger.debug("Building call graph....");
        long start = System.currentTimeMillis();
        var rawGraph = generateCallGraph(classPaths.get(0).jarPath.toString());
        logger.debug("Call graph construction took {}ms", System.currentTimeMillis() - start);
        return WalaResultAnalyzer.wrap(rawGraph, classPaths);
    }

    /**
     * Build a class path for given maven coordinate.
     *
     * @param mavenCoordinate Maven coordinate
     * @return List of resolved maven coordinates
     */
    private static List<MavenResolvedCoordinate> buildClasspath(String mavenCoordinate) throws FileNotFoundException {
        try {
            var artifacts = Maven.resolver()
                    .resolve(mavenCoordinate)
                    .withTransitivity()
                    .asResolvedArtifact();

            return Arrays.stream(artifacts)
                    .map(MavenResolvedCoordinate::of)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new FileNotFoundException();
        }
    }

    /**
     * Create a call graph instance given a class path.
     *
     * @param classpath Path to class or jar file
     * @return Call Graph
     */
    public static CallGraph generateCallGraph(String classpath) {
        try {
            var classLoader = Thread.currentThread().getContextClassLoader();
            var exclusionFile = new File(Objects.requireNonNull(classLoader
                    .getResource("Java60RegressionExclusions.txt")).getFile());

            AnalysisScope scope = AnalysisScopeReader
                    .makeJavaBinaryAnalysisScope(classpath, exclusionFile);

            var cha = ClassHierarchyFactory.makeWithRoot(scope);

            EntryPointsGenerator entryPointsGenerator = new EntryPointsGenerator(cha);
            var entryPoints = entryPointsGenerator.getEntryPoints();
            var options = new AnalysisOptions(scope, entryPoints);
            var cache = new AnalysisCacheImpl();

            var builder = Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha, scope);

            return builder.makeCallGraph(options, null);
        } catch (Exception e) {
            return null;
        }
    }
}
