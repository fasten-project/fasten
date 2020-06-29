package eu.fasten.analyzer.javacgopal.version3.data;

import com.google.common.collect.Lists;
import eu.fasten.analyzer.javacgopal.version3.ExtendedRevisionCallGraphV3;
import eu.fasten.analyzer.javacgopal.version3.scalawrapper.JavaToScalaConverter;
import eu.fasten.core.data.FastenURI;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.br.analyses.Project;
import org.opalj.tac.cg.CallGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.JavaConverters;

/**
 * Call graphs that are not still fully resolved. i.e. isolated call graphs which within-artifact
 * calls (edges) are known as internal calls and Cross-artifact calls are known as external. calls.
 */
public class PartialCallGraph {

    private static final Logger logger = LoggerFactory.getLogger(PartialCallGraph.class);

    private final Map<ExtendedRevisionCallGraphV3.Scope, Map<FastenURI, ExtendedRevisionCallGraphV3.Type>> classHierarchy;
    private final ExtendedRevisionCallGraphV3.Graph graph;
    private final int nodeCount;

    /**
     * Given a file, algorithm and main class (in case of application package)
     * it creates a {@link PartialCallGraph} for it using OPAL.
     *
     * @param file      file to be process
     * @param mainClass main class of the package in case the package is an application
     * @param algorithm algorithm to be used for generating a call graph
     */
    public PartialCallGraph(final File file, final String mainClass, final String algorithm) {
        CallGraphConstructor constructor = new CallGraphConstructor(file, mainClass, algorithm);
        this.graph = new ExtendedRevisionCallGraphV3.Graph();

        logger.info("Creating internal CHA");
        final var cha = createInternalCHA(constructor.getProject());

        logger.info("Creating graph with external CHA");
        createGraphWithExternalCHA(constructor.getCallGraph(), cha);

        this.nodeCount = cha.getNodeCount();
        this.classHierarchy = cha.asURIHierarchy(constructor.getProject().classHierarchy());
    }

    public Map<ExtendedRevisionCallGraphV3.Scope, Map<FastenURI, ExtendedRevisionCallGraphV3.Type>> getClassHierarchy() {
        return classHierarchy;
    }

    public ExtendedRevisionCallGraphV3.Graph getGraph() {
        return graph;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    /**
     * Creates RevisionCallGraph using OPAL call graph generator for a given maven
     * coordinate. It also sets the forge to "mvn".
     *
     * @param coordinate maven coordinate of the revision to be processed
     * @param timestamp  timestamp of the revision release
     * @return RevisionCallGraph of the given coordinate.
     * @throws FileNotFoundException in case there is no jar file for the given coordinate on the
     *                               Maven central it throws this exception.
     */
    public static ExtendedRevisionCallGraphV3 createExtendedRevisionCallGraph(
            final MavenCoordinate coordinate, final String mainClass,
            final String algorithm, final long timestamp)
            throws FileNotFoundException {
        final var partialCallGraph = new PartialCallGraph(
                MavenCoordinate.MavenResolver.downloadJar(coordinate)
                        .orElseThrow(RuntimeException::new), mainClass, algorithm
        );
        return new ExtendedRevisionCallGraphV3("mvn", coordinate.getProduct(),
                coordinate.getVersionConstraint(), timestamp, partialCallGraph.getNodeCount(), "OPAL",
                MavenCoordinate.MavenResolver.resolveDependencies(coordinate),
                partialCallGraph.getClassHierarchy(),
                partialCallGraph.getGraph());
    }

    /**
     * Creates a class hierarchy for the given call graph's artifact with entries
     * only in internalCHA. ExternalCHA to be added at a later stage.
     *
     * @param project OPAL {@link Project}
     * @return class hierarchy for a given package
     * @implNote Inside {@link OPALType} all of the methods are indexed, it means one can use the
     * ids assigned to each method instead of the method itself.
     */
    private OPALClassHierarchy createInternalCHA(final Project<?> project) {
        final Map<ObjectType, OPALType> result = new HashMap<>();
        final AtomicInteger methodNum = new AtomicInteger();

        final var objs = Lists.newArrayList(JavaConverters.asJavaIterable(project.allClassFiles()));
        objs.sort(Comparator.comparing(Object::toString));

        for (final var classFile : objs) {
            final var currentClass = classFile.thisType();
            final var methods =
                    getMethodsMap(methodNum.get(), JavaConverters.asJavaIterable(classFile.methods()));

            final var superClasses = OPALType.extractSuperClasses(project.classHierarchy(), currentClass);

            final var type = new OPALType(methods,
                    superClasses,
                    OPALType.extractSuperInterfaces(project.classHierarchy(), currentClass),
                    classFile.sourceFile()
                            .getOrElse(JavaToScalaConverter.asScalaFunction0OptionString("NotFound")));

            result.put(currentClass, type);
            methodNum.addAndGet(methods.size());
        }
        return new OPALClassHierarchy(result, new HashMap<>(), methodNum.get());
    }

    /**
     * Assign each method an id. Ids start from the the first parameter and increase by one number
     * for every method.
     *
     * @param methods Iterable of {@link Method} to get mapped to ids.
     * @return A map of passed methods and their ids.
     * @implNote Methods are keys of the result map and values are the generated Integer keys.
     */
    private Map<Method, Integer> getMethodsMap(final int keyStartsFrom,
                                               final Iterable<Method> methods) {
        final Map<Method, Integer> result = new HashMap<>();
        final AtomicInteger i = new AtomicInteger(keyStartsFrom);
        for (final var method : methods) {
            result.put(method, i.get());
            i.addAndGet(1);
        }
        return result;
    }

    /**
     * Given a call graph generated by OPAL and class hierarchy iterates over methods
     * declared in the package that call external methods and add them to externalCHA of
     * a call hierarchy. Build a graph for both internal and external calls in parallel.
     *
     * @param cg  call graph from OPAL generator
     * @param cha class hierarchy
     */
    private void createGraphWithExternalCHA(final CallGraph cg, final OPALClassHierarchy cha) {
        for (final var sourceDeclaration : JavaConverters
                .asJavaIterable(cg.reachableMethods().toIterable())) {

            if (sourceDeclaration.hasMultipleDefinedMethods()) {
                for (final var source : JavaConverters
                        .asJavaIterable(sourceDeclaration.definedMethods())) {
                    cha.appendGraph(source, cg.calleesOf(sourceDeclaration), graph);
                }
            } else if (sourceDeclaration.hasSingleDefinedMethod()) {
                cha.appendGraph(sourceDeclaration.definedMethod(),
                        cg.calleesOf(sourceDeclaration), graph);

            } else if (sourceDeclaration.isVirtualOrHasSingleDefinedMethod()) {
                cha.appendGraph(sourceDeclaration, cg.calleesOf(sourceDeclaration), graph);
            }
        }
    }
}
