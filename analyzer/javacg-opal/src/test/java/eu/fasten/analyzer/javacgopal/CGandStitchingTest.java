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

import static eu.fasten.core.merge.CallGraphUtils.decode;
import static org.junit.jupiter.api.Assertions.*;
import com.github.javaparser.utils.Log;
import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.analyzer.sourceanalyzer.CommentParser;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.merge.CallGraphUtils;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.collections.map.CompositeMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class CGandStitchingTest {

    public static ExtendedRevisionJavaCallGraph getRCG(final String path, final String product,
                                                       final String version) throws OPALException, URISyntaxException {
        final var file =
            new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource(path)).toURI().getPath());

        return getRCG(file, product, version);
    }

    private static ExtendedRevisionJavaCallGraph getRCG(final File file, final String product,
                                                        final String version)
        throws OPALException {
        var opalCG = new CallGraphConstructor(file, "", "RTA");
        var cg = new PartialCallGraph(opalCG);
        return ExtendedRevisionJavaCallGraph.extendedBuilder()
            .graph(cg.getGraph())
            .product(product)
            .version(version)
            .classHierarchy(cg.getClassHierarchy())
            .nodeCount(cg.getNodeCount())
            .build();
    }

    @Test
    public void staticInitializer() throws OPALException, URISyntaxException {
        final var importer = getRCG("merge/staticInitializer/Importer.class","importer","0.0.0");
        final var imported = getRCG("merge/staticInitializer/Imported.class", "imported", "1.1.1");
        Assertions.assertEquals("/merge.staticInitializer/Importer.<init>()/java.lang/VoidType '->'\n" +
                "/java.lang/Object.<init>()/java.lang/VoidType\n" +
                "\n" +
                "/merge.staticInitializer/Importer.sourceMethod()/java.lang/VoidType '->'\n" +
                "/merge.staticInitializer/Imported.<init>()/java.lang/VoidType\n" +
                "\n",
            toString(importer));

        final var mergedRcg = merge(importer, Arrays.asList(importer, imported));

        Assertions.assertEquals("fasten://mvn!importer$0.0.0/merge.staticInitializer/Importer" +
            ".sourceMethod()/java.lang/VoidType '->'\n" +
            "fasten://mvn!imported$1.1.1/merge.staticInitializer/Imported.<init>()/java.lang/VoidType\n" +
            "\n", toString(mergedRcg));
    }

    private String toString(List<Pair<String, String>> mergedRcg) {
        return CallGraphUtils.toStringEdges(mergedRcg);
    }

    private String toString(ExtendedRevisionJavaCallGraph mergedRcg) {
        return CallGraphUtils.getString(CallGraphUtils.convertToNodePairs(mergedRcg));
    }
    private List<Pair<String, String>> merge(ExtendedRevisionJavaCallGraph artifact,
                                        List<ExtendedRevisionJavaCallGraph> deps) {
        final var cgMerger = new CGMerger(deps);
        final var mergedCG = cgMerger.mergeWithCHA(artifact);
        List<Pair<String, String>> result = new ArrayList<>();
        for (LongLongPair edge : mergedCG.edgeSet()) {
            final var firstUri = cgMerger.getAllUris().get(edge.firstLong());
            final var secondUri = cgMerger.getAllUris().get(edge.secondLong());
            result.add(ImmutablePair.of(firstUri, secondUri));
        }
        return result;
    }

    @Test
    public void edgeExplosion() throws OPALException, URISyntaxException {
        final List<ExtendedRevisionJavaCallGraph> deps = getDepSet("merge/hashCode");

        final var user =
            deps.stream().filter(ercg -> ercg.product.equals("User.class")).findAny().get();

        final var merged =  merge(user, deps);

        Assertions.assertEquals(
                "fasten://mvn!User.class$0.0.0/merge.hashCode/User.main(/java.lang/String[])/java.lang/VoidType '->'\n" +
                "fasten://mvn!Parent.class$0.0.0/merge.hashCode/Parent.hashCode()/java.lang/IntegerType\n\n",
            toString(merged));

    }

    private List<ExtendedRevisionJavaCallGraph> getDepSet(final String path) throws OPALException, URISyntaxException {
        final List<ExtendedRevisionJavaCallGraph> result = new ArrayList<>();

        final var depFiles = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
            .getResource(path)).toURI().getPath()).listFiles(f -> f.getPath().endsWith(".class"));
        for (final var depFile : depFiles) {
            if (!depFile.getName().contains(" ") ) {
                result.add(getRCG(depFile, depFile.getName(), "0.0.0"));
            }
        }
        return result;
    }

    @Test
    public void testAllCases() {
        Log.setAdapter(new Log.StandardOutStandardErrorAdapter());
        final var basePath = "merge/annotated-tests";
        Supplier<Stream<String>> projects = () -> Stream.of("app", "dep1", "dep2");
        final var version = "1.0";

        final var expected = new HashMap<String,Set<String>>();
        projects.get().forEach(p -> expected.putAll(getExpected(p)));
        expected.putAll(getOtherPackages());

        final var deps =
            projects.get().map(s -> generate(basePath, s,version)).collect(Collectors.toList());
        final var actual =
            deps.stream().map(ercg -> toMap(merge(ercg, deps))).reduce(CompositeMap::new).get();
        assertEqual(expected, ((Map<String, Set<String>>)actual));
    }

    private Map<? extends String,? extends Set<String>> getOtherPackages() {
        final var root = "src/test/resources/merge/annotated-tests/app" +
            "/src/main/java";
        final var pckg1 = new CommentParser().extractComments(root, "inheritedandsubtyped");
        final var pckg2 = new CommentParser().extractComments(root, "implementedmethod");
        pckg1.putAll(pckg2);
        return pckg1;
    }


    private void assertEqual(final Map<String, Set<String>> expected, final Map<String,
        Set<String>> actual) {

        for (final var entry : expected.entrySet()) {
            if (!entry.getValue().isEmpty()) {

                assertEquals(entry.getValue(), actual.get(entry.getKey()));
            }
        }
    }

    private Map<String, Set<String>> getExpected(final String artifact) {
        return new CommentParser().extractComments("src/test/resources/merge/annotated-tests/"+artifact+
            "/src/main/java", artifact+"package");
    }

    private ExtendedRevisionJavaCallGraph generate(final String base, final String artifact,
                                                   final String version) {
        try {
            return getRCG(base+"/"+artifact+"/target/"+artifact+"-"+version+"-SNAPSHOT.jar", artifact,
                version);
        } catch (OPALException | URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static Map<String,Set<String>> toMap(final List<Pair<String, String>> nodePairs) {
        Map<String, Set<String>> result = new HashMap<>();
        for (final var edge : nodePairs) {
            final var source =
                decode(getClass(edge.getLeft()) + "." + getMethod(edge.getLeft()));
            final var target =
                decode(getClass(edge.getRight()) + "." + getMethod(edge.getRight()));
            final var current = result.getOrDefault(source, new HashSet<>());
            current.add(target);
            result.put(source, current);
        }
        return result;
    }

    public static Map<String,Set<String>> toMap(final Map<String,
        List<Pair<String, String>>> nodePairs) {
        Map<String, Set<String>> result = new HashMap<>();
        for (final var edge : aggregateAllEdges(nodePairs)) {
            final var source =
                getClass(edge.getLeft()) + "." + getMethod(edge.getLeft());
            final var target =
                getClass(edge.getRight()) + "." + getMethod(edge.getRight());
            final var current = result.getOrDefault(source, new HashSet<>());
            current.add(target);
            result.put(source, current);
        }
        return result;
    }

    private static String getArtifact(final String uri) {
        if (uri.startsWith("//")) {
            final var product = StringUtils.substringBetween(uri, "//","$");
            final var version = StringUtils.substringBetween(uri, "$", "/");
            return product + ":" + version + "/";
        }
        return "";
    }

    private static String getMethod(final String uri) {
        final var decodedUri = java.net.URLDecoder.decode(uri, StandardCharsets.UTF_8);
        var partialUriFormatException = "Invalid partial FASTEN URI. The format is corrupted.\nMust be: `/{namespace}/{class}.{method}({signature.args})/{signature.returnType}`";

        // Method: `.{method}(`
        Pattern methodNamePattern = Pattern.compile("(?<=\\.)([^.]+)(?=\\()");
        Matcher methodNameMatcher = methodNamePattern.matcher(decodedUri);
        if (!methodNameMatcher.find() || methodNameMatcher.group(0).isEmpty())
            throw new IllegalArgumentException(partialUriFormatException);

        var method = methodNameMatcher.group(0) + "(";
        final var params = StringUtils.substringBetween(uri, "(", ")").split(",");
        for (String param : params) {
            param = decode(param);
            final var paramUri = param.split("/");
            final var paramCLas = paramUri[paramUri.length-1];
            method = method + paramCLas + ",";
        }
        if (params.length!=0) {
            method = StringUtils.removeEnd(method, ",");
        }
        return method + ")";
    }

    private static String getClass(final String uri){
        // Class: `/{class}.*(`
        Pattern classPattern = Pattern.compile("(?<=/)([^\\/]+)(?=\\.([^./]+)\\()");
        var partialUriFormatException = "Invalid partial FASTEN URI. The format is corrupted.\nMust be: `/{namespace}/{class}.{method}({signature.args})/{signature.returnType}`";

        Matcher classMatcher = classPattern.matcher(uri);
        if (!classMatcher.find() || classMatcher.group(0).isEmpty())
            throw new IllegalArgumentException(partialUriFormatException);
        var result = classMatcher.group(0);
        if (classMatcher.group(0).contains("$")) {
            result = StringUtils.substringAfter(classMatcher.group(0), "$");
        }
        return result;
    }

    private static List<Pair<String, String>> aggregateAllEdges(
        Map<String, List<Pair<String, String>>> edges) {
        List<Pair<String, String>> result = new ArrayList<>();
        for (final var scope : edges.values()) {
            result.addAll(scope);
        }
        return result;
    }

    @Test
    public void testAllCasesJ8() {
        //runTestsIn("resources/example-workspace-java8/");
    }


    @Test
    public void virtualReceiverTypes() throws OPALException, URISyntaxException {
        var cg = getRCG("merge/hashCode/complex/User.class","importer","0.0.0");
        asserReceiver(cg, "invokedynamic","[/merge.hashCode.complex/Child]");
        cg = getRCG("merge/hashCode/interFace/User.class","importer","0.0.0");
        asserReceiver(cg, "invokeinterface","[/merge.hashCode.interFace/Child]");
        cg = getRCG("merge/hashCode/User.class","importer","0.0.0");
        asserReceiver(cg, "invokedynamic","[/merge.interFace/Child]");

    }

    public void asserReceiver(ExtendedRevisionJavaCallGraph cg, String callType, String type){
        for (final var edge : cg.getGraph().getExternalCalls().entrySet()) {
            for (final var cs : edge.getValue().entrySet()) {
                final var metadata = (Map<Object,Object>)cs.getValue();
                if ((metadata.get("type").equals(callType))) {
                    Assertions.assertEquals(type, metadata.get("receiver"));
                }
            }
        }
    }

    @Test
    public void missingEdges() throws OPALException, URISyntaxException {
        final var cg = getRCG("merge/missingEdge/User.class","importer","0.0.0");
        assertTrue(toString(cg).contains("/merge.missingEdge/User.main(/java.lang/String[])/java.lang/VoidType '->'\n" +
            "/merge.missingEdge/Child.hashCode()/java.lang/IntegerType"));
    }

    @Test
    public void souldNotGetIllegalArgumentExceptionWhileMerging() throws
        OPALException, MissingArtifactException {

        final var depSet = new ArrayList<>(Arrays.asList(getRCG("info.picocli:picocli:4.0.4"),
            getRCG("net.bytebuddy:byte-buddy:1.10.5"),
            getRCG("org.apache.kafka:kafka-clients:2.3.0")));

        var merger = new CGMerger(depSet);
        merger.mergeWithCHA(depSet.get(2));
    }

    private ExtendedRevisionJavaCallGraph getRCG(String coordStr)
        throws OPALException, MissingArtifactException {
        final var coord = MavenCoordinate.fromString(coordStr,"jar");

        final var dep1 =
            new MavenCoordinate.MavenResolver().downloadArtifact(coord,"jar");
        final var opalCG = new CallGraphConstructor(dep1, "", "RTA");
        final var cg = new PartialCallGraph(opalCG);
        return ExtendedRevisionJavaCallGraph.extendedBuilder()
            .graph(cg.getGraph())
            .product(coord.getProduct())
            .version(coord.getVersionConstraint())
            .classHierarchy(cg.getClassHierarchy())
            .nodeCount(cg.getNodeCount())
            .build();
    }

}