package eu.fasten.analyzer.javacgopal.jcg;

import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.data.exceptions.OPALException;
import eu.fasten.core.data.ExtendedRevisionCallGraph;
import eu.fasten.core.merge.CallGraphUtils;
import eu.fasten.core.merge.LocalMerger;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

public class JCGTest {

    @Test
    public void testCFNE1() throws OPALException, IOException {
        final var testCases = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("merge/jcg")).getPath());

        final Map<String, Map<String, List<Pair<String, String>>>> result = new HashMap<>();
        for (final var testCase : Objects.requireNonNull(testCases.listFiles())) {
            for (final var bin : Objects.requireNonNull(testCase.listFiles())) {
                if (bin.getName().contains("bin")) {
                    final var packag = Objects.requireNonNull(bin.listFiles())[0];
                    final var thisTest = JCGTest.getRCG(packag, testCase.getName(), "");

                    List<ExtendedRevisionCallGraph> rcgs = new ArrayList<>();
                    for (final var classfile : Objects.requireNonNull(packag.listFiles())) {
                        if (testCase.getName().equals("Lambda3")) {
                            System.out.println();
                        }
                        rcgs.add(JCGTest.getRCG(classfile,
                                classfile.getName()
                                        .replace("$", "")
                                        .replace(" ", ""), ""));
                    }

                    List<ExtendedRevisionCallGraph> mergedRCGs = new ArrayList<>();
                    for (final var rcg : rcgs) {
                        mergedRCGs.add(new LocalMerger(rcg, rcgs).mergeWithCHA());
                    }
                    result.put(testCase.getName(), JCGTest.compareMergeOPAL(rcgs, thisTest));
                }
            }
        }
        CallGraphUtils.writeToCSV(buildOverallCsv(result), "src/test/resources/merge/jcgEdges.csv");

    }

    public static ExtendedRevisionCallGraph getRCG(final File file, final String product,
                                                   final String version) throws OPALException {
        var opalCG = new CallGraphConstructor(file, "", "RTA");
        var cg = new PartialCallGraph(opalCG);
        return ExtendedRevisionCallGraph.extendedBuilder()
                .graph(cg.getGraph())
                .forge("mvn")
                .product(product)
                .version(version)
                .classHierarchy(cg.getClassHierarchy())
                .nodeCount(cg.getNodeCount())
                .build();
    }

    public static Map<String, List<Pair<String, String>>> compareMergeOPAL(final List<ExtendedRevisionCallGraph> merges,
                                                                           ExtendedRevisionCallGraph opal) {
        final var mergeInternals =
                augmentInternals(merges).stream().sorted().collect(Collectors.toList());
        final var mergeExternals =
                augmentExternals(merges).stream().sorted().collect(Collectors.toList());
        final var opalInternals =
                CallGraphUtils.convertToNodePairs(opal).get("internalTypes");
        final var opalExternals =
                CallGraphUtils.convertToNodePairs(opal).get("externalTypes");

        return Map.of("mergeInternals", mergeInternals,
                "mergeExternals", mergeExternals,
                "opalInternals", opalInternals,
                "opalExternals", opalExternals,
                "mergeInternals - opalInternals", diff(mergeInternals, opalInternals),
                "opalInternals - mergeInternals", diff(opalInternals, mergeInternals),
                "mergeExternals - opalExternals", diff(mergeExternals, opalExternals),
                "opalExternals - mergeExternals", diff(opalExternals, mergeExternals)
        );

    }

    public static ArrayList<Pair<String, String>> diff(final List<Pair<String, String>> firstEdges,
                                                       final List<Pair<String, String>> secondEdges) {
        final var temp1 = new ArrayList<>(firstEdges);
        final var temp2 = new ArrayList<>(secondEdges);
        temp1.removeAll(temp2);
        return temp1;
    }

    private static List<Pair<String, String>> augmentExternals(
            final List<ExtendedRevisionCallGraph> rcgs) {
        List<Pair<String, String>> externals = new ArrayList<>();
        for (final var rcg : rcgs) {
            externals = CallGraphUtils.convertToNodePairs(rcg)
                    .get("externalTypes");
        }

        return externals;
    }

    private static List<Pair<String, String>> augmentInternals(
            final List<ExtendedRevisionCallGraph> rcgs) {
        List<Pair<String, String>> internals = new ArrayList<>();
        for (final var rcg : rcgs) {
            internals = CallGraphUtils.convertToNodePairs(rcg)
                    .get("internalTypes");
            internals.addAll(CallGraphUtils.convertToNodePairs(rcg)
                    .get("resolvedTypes"));
        }

        return internals;
    }

    private List<String[]> buildOverallCsv(
            final Map<String, Map<String, List<Pair<String, String>>>> testCases) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeader());
        int counter = 0;
        for (final var testCase : testCases.entrySet()) {
            dataLines.add(getContent(counter, testCase.getValue(), testCase.getKey()));
            counter++;
        }
        return dataLines;
    }

    private String[] getContent(final int counter,
                                final Map<String, List<Pair<String, String>>> edges,
                                final String testCaseName) {
        return new String[]{
                /* number */ String.valueOf(counter),
                /* testCase */ testCaseName,
                /* mergeInternals */ geteEdgeContent(edges, "mergeInternals"),
                /* mergeInternalsNum */ getSize(edges, "mergeInternals"),
                /* mergeExternals */ geteEdgeContent(edges, "mergeExternals"),
                /* mergeExternalsNum */ getSize(edges, "mergeExternals"),
                /* opalInternals */ geteEdgeContent(edges, "opalInternals"),
                /* opalInternalNum */ getSize(edges, "opalInternals"),
                /* opalExternals */ geteEdgeContent(edges, "opalExternals"),
                /* opalExternalNum */ getSize(edges, "opalExternals"),
                /* mergeInternals - opalInternals */ geteEdgeContent(edges, "mergeInternals - " +
                "opalInternals"),
                /* mergeInternals - opalInternals Num*/
                getSize(edges, "mergeInternals - opalInternals"),
                /* opalInternals - mergeInternals */
                geteEdgeContent(edges, "opalInternals - mergeInternals"),
                /* opalInternals - mergeInternals Num */
                getSize(edges, "opalInternals - mergeInternals"),
                /* mergeExternals - opalExternals */
                geteEdgeContent(edges, "mergeExternals - opalExternals"),
                /* mergeExternals - opalExternals Num */
                getSize(edges, "mergeExternals - opalExternals"),
                /* opalExternals - mergeExternals */
                geteEdgeContent(edges, "opalExternals - mergeExternals"),
                /* opalExternals - mergeExternals Num */
                getSize(edges, "opalExternals - mergeExternals")
        };
    }

    private String getSize(final Map<String, List<Pair<String, String>>> edges, final String key) {
        if (edges != null) {
            return String.valueOf(edges.get(key).size());
        }
        return "";
    }

    private String geteEdgeContent(final Map<String, List<Pair<String, String>>> edges,
                                   final String scope) {
        return CallGraphUtils.toStringEdges(edges.get(scope));
    }

    private String[] getHeader() {
        return new String[]{
                "num", "testCase", "mergeInternals",
                "miNum", "mergeExtenals", "meNum",
                "opalInternals", "oiNum", "opalExternals",
                "oeNum", "mi - oi", "mi - oi Num",
                "oi - mi", "oi - mi Num", "me - oe",
                "me - oe Num", "oe - me", "oe - me Num"
        };
    }

}
