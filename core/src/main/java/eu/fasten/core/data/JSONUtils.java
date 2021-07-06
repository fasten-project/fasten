package eu.fasten.core.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.fasten.core.data.opal.MavenCoordinate;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import org.apache.commons.lang3.tuple.Pair;

public class JSONUtils {

    public static String toJSONString(final DirectedGraph graph, final MavenCoordinate coordinate) {
        var result = new StringBuilder("{");
        appendArtifactInformation(result, coordinate, graph.numNodes());
        appendGraph(result, graph);
        if (result.charAt(result.length() - 1) == ',') {
            result.setLength(result.length() - 1);
        }
        result.append("}");
        return result.toString();
    }

    /**
     * Appends general information of the revision to the beginning of the StringBuilder.
     *
     * @param coordinate the object to extract the information from.
     * @param numNodes   number of nodes in the graph
     * @param result     the StringBuilder to append the information.
     */
    private static void appendArtifactInformation(StringBuilder result,
                                                  final MavenCoordinate coordinate, int numNodes) {
        appendKeyValue(result, "product", coordinate.getProduct());
        appendKeyValue(result, "nodes", numNodes);
        appendKeyValue(result, "forge", Constants.mvnForge);
        appendKeyValue(result, "generator", Constants.opalGenerator);
        appendKeyValue(result, "version", coordinate.getVersionConstraint());
    }

    /**
     * Appends graph information of the revision to the StringBuilder.
     *
     * @param graph  the graph object to extract the information from.
     * @param result the StringBuilder to append the information.
     */
    private static void appendGraph(StringBuilder result, final DirectedGraph graph) {
        result.append("\"nodes\":[");
        for (final var node : graph.nodes()) {
            result.append(node).append(",");
        }
        removeLastIfNotEmpty(result, graph.nodes().size());
        result.append("],");
        result.append("\"edges\":[");
        for (final var edge : graph.edgeSet()) {
            result.append("[").append(edge.firstLong()).append(",").append(edge.secondLong()).append("],");
        }
        removeLastIfNotEmpty(result, graph.edgeSet().size());
        result.append("],");
    }

    /**
     * Converts an {@link ExtendedRevisionJavaCallGraph} object to its corresponding JSON String
     * without any object creation in between. It creates a {@link StringBuilder) in the beginning
     * and only appends to it in order to decrease the memory and time consumption.
     *
     * @param erjcg and object of java revision call graph to be converted to JSON String.
     * @return the corresponding JSON String.
     */
    public static String toJSONString(final ExtendedRevisionJavaCallGraph erjcg) {
        var result = new StringBuilder("{");
        appendArtifactInformation(result, erjcg);
        appendCha(result, erjcg.classHierarchy);
        appendGraph(result, erjcg.getGraph());
        if (erjcg.timestamp >= 0) {
            appendKeyValue(result, "timestamp", erjcg.timestamp, true);
        }
        if (result.charAt(result.length() - 1) == ',') {
            result.setLength(result.length() - 1);
        }
        result.append("}");
        return result.toString();
    }

    /**
     * Appends general information of the revision to the beginning of the StringBuilder.
     *
     * @param erjcg  the object to extract the information from.
     * @param result the StringBuilder to append the information.
     */
    private static void appendArtifactInformation(StringBuilder result,
                                                  final ExtendedRevisionJavaCallGraph erjcg) {
        appendKeyValue(result, "product", erjcg.product);
        appendKeyValue(result, "nodes", erjcg.nodeCount);
        appendKeyValue(result, "forge", erjcg.forge);
        appendKeyValue(result, "generator", erjcg.cgGenerator);
        appendKeyValue(result, "version", erjcg.version);
    }

    /**
     * Appends graph information of the revision to the StringBuilder.
     *
     * @param graph  the graph object to extract the information from.
     * @param result the StringBuilder to append the information.
     */
    private static void appendGraph(StringBuilder result, final JavaGraph graph) {
        result.append("\"call-sites\":[");
        for (final var entry : graph.getCallSites().entrySet()) {
            appendCall(result, entry);
        }
        removeLastIfNotEmpty(result, graph.getCallSites().size());
        result.append("],");

    }

    /**
     * Removes the last character of StringBuilder if the second parameter is not zero.
     * This method helps to remove extra "," from the end of multiple element lists.
     *
     * @param result the StringBuilder to remove from.
     * @param size   if the size of the list is zero there is no "," to be removed.
     */
    private static void removeLastIfNotEmpty(StringBuilder result,
                                             int size) {
        if (size != 0) {
            result.setLength(result.length() - 1);
        }
    }

    /**
     * Appends call information of the specified call to the StringBuilder.
     *
     * @param entry  the call Map Entry to extract the information from.
     * @param result the StringBuilder to append the information.
     */
    private static void appendCall(StringBuilder result,
                                   final Map.Entry<IntIntPair, Map<Object, Object>> entry) {
        result.append("[").append(quote(entry.getKey().first().toString())).append(",");
        result.append(quote(entry.getKey().second().toString())).append(",{");
        appendCallableMetadataJson(result, entry.getValue());
        result.append("}],");
    }

    /**
     * Appends metadata information of the callable to the StringBuilder.
     *
     * @param metadata of the call Map to extract the information from.
     * @param result   the StringBuilder to append the information.
     */
    private static void appendCallableMetadataJson(StringBuilder result,
                                                   final Map<Object, Object> metadata) {
        for (final var entry : metadata.entrySet()) {
            final var callSite = (HashMap<String, Object>) entry.getValue();
            result.append(quote(entry.getKey().toString())).append(":{");
            appendMetadata(result, callSite);
            result.append("},");
        }
        removeLastIfNotEmpty(result, metadata.size());
    }

    /**
     * Appends cha information of the cha to the StringBuilder.
     *
     * @param cha    the cha Map to extract the information from.
     * @param result the StringBuilder to append information.
     */
    private static void appendCha(StringBuilder result, final Map<JavaScope,
            Map<String, JavaType>> cha) {
        result.append("\"cha\":{\"externalTypes\":{");
        for (final var entry : cha.get(JavaScope.externalTypes).entrySet()) {
            appendType(result, entry.getKey(), entry.getValue());
        }
        removeLastIfNotEmpty(result, cha.get(JavaScope.externalTypes).size());

        result.append("},\"internalTypes\":{");
        for (final var entry : cha.get(JavaScope.internalTypes).entrySet()) {
            appendType(result, entry.getKey(), entry.getValue());
        }
        removeLastIfNotEmpty(result, cha.get(JavaScope.internalTypes).size());

        result.append("},\"resolvedTypes\":{");
        for (final var entry : cha.get(JavaScope.resolvedTypes).entrySet()) {
            appendType(result, entry.getKey(), entry.getValue());
        }
        removeLastIfNotEmpty(result, cha.get(JavaScope.resolvedTypes).size());

        result.append("}},");

    }

    /**
     * Appends {@link JavaType} information of the specified type to the StringBuilder.
     *
     * @param key    the type key which is the String form of {@link FastenURI} of the type
     * @param type   the JavaType to extract the information from.
     * @param result the StringBuilder to append the information.
     */
    private static void appendType(StringBuilder result, final String key,
                                   final JavaType type) {
        result.append(quote(key)).append(":{");
        appendKeyValue(result, "access", type.getAccess());
        result.append("\"methods\":{");
        appendMethods(result, type.getMethods());
        result.append("},\"final\":").append(type.isFinal());
        result.append(",\"superInterfaces\":[");
        appendSupers(result, type.getSuperInterfaces());
        result.append("],");
        appendKeyValue(result, "sourceFile", type.getSourceFileName());
        result.append("\"superClasses\":[");
        appendSupers(result, type.getSuperClasses());
        result.append("],");
        result.append("\"annotations\":{");
        appendAnnotations(result, type.getAnnotations());
        result.append("}},");
    }

    /**
     * Appends information of the super types to the StringBuilder.
     *
     * @param supers the list of types to extract the information.
     * @param result the StringBuilder to append the information.
     */
    public static void appendSupers(StringBuilder result, final List<?> supers) {
        for (final var fastenURI : supers) {
            result.append(quote(fastenURI.toString())).append(",");
        }
        removeLastIfNotEmpty(result, supers.size());

    }

    /**
     * Appends information of the annotations to the StringBuilder.
     *
     * @param result      the StringBuilder to append the information.
     * @param annotations annotations information
     */
    public static void appendAnnotations(StringBuilder result,
                                         final Map<String, List<Pair<String, String>>> annotations) {
        for (var annotationEntry : annotations.entrySet()) {
            result.append(quote(annotationEntry.getKey())).append(":[");
            for (var annotationValue : annotationEntry.getValue()) {
                result.append("[").append(quote(annotationValue.getLeft())).append(",")
                        .append(quote(escapeString(annotationValue.getRight()))).append("],");
            }
            removeLastIfNotEmpty(result, annotationEntry.getValue().size());
            result.append("],");
        }
        removeLastIfNotEmpty(result, annotations.entrySet().size());
    }

    private static String escapeString(String str) {
        return str.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\f", "\\f")
                .replace("'", "\\'")
                .replace("\"", "\\\"");
    }

    /**
     * Appends methods of a type to the StringBuilder.
     *
     * @param methods Map of nodes to extract the information.
     * @param result  the StringBuilder to append the information.
     */
    private static void appendMethods(StringBuilder result,
                                      final Map<Integer, JavaNode> methods) {
        for (final var entry : methods.entrySet()) {

            result.append(quote(entry.getKey().toString()));
            result.append(":{\"metadata\":{");
            appendMetadata(result, entry.getValue().getMetadata());
            result.append("},");
            appendKeyValue(result, "uri", entry.getValue().getUri().toString(), true);
            result.append("},");
        }
        removeLastIfNotEmpty(result, methods.size());

    }

    /**
     * Appends metadata of a node to the StringBuilder.
     *
     * @param map    Map of metadata to extract the information.
     * @param result the StringBuilder to append the information.
     */
    private static void appendMetadata(StringBuilder result, final Map<?, ?> map) {
        for (final var entry : map.entrySet()) {
            if (entry.getValue() instanceof String) {
                result.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue())
                        .append(
                                "\",");
            } else {
                result.append("\"").append(entry.getKey()).append("\":").append(entry.getValue())
                        .append(",");
            }
        }
        removeLastIfNotEmpty(result, map.size());
    }

    /**
     * Quotes a given String.
     *
     * @param s String to be quoted.
     * @return quoted String.
     */
    private static String quote(final String s) {
        return "\"" + s + "\"";
    }

    /**
     * Appends a key value to a given StringBuilder assuming it is not the last key value in a
     * list of key values and the value is a Number.
     *
     * @param result StringBuilder to append to the key value.
     * @param key    key String.
     * @param value  Number value.
     */
    private static void appendKeyValue(StringBuilder result, final String key,
                                       final Number value) {
        appendKeyValue(result, key, value, false);
    }

    /**
     * Appends a key value to a given StringBuilder assuming the value is a Number.
     *
     * @param result StringBuilder to append to the key value.
     * @param key    key String.
     * @param value  Number value.
     */
    private static void appendKeyValue(StringBuilder result, final String key,
                                       final Number value, final boolean lastKey) {
        result.append(quote(key)).append(":").append(value);
        if (!lastKey) {
            result.append(",");
        }
    }

    /**
     * Appends a key value to a given StringBuilder assuming it is not the last key value in a
     * list of key values and the value is a String.
     *
     * @param result StringBuilder to append to the key value.
     * @param key    String key.
     * @param value  String value.
     */
    private static void appendKeyValue(StringBuilder result, final String key,
                                       final String value) {
        appendKeyValue(result, key, value, false);
    }

    /**
     * Appends a key value to a given StringBuilder assuming the value is a String.
     *
     * @param result StringBuilder to append to the key value.
     * @param key    String key.
     * @param value  String value.
     */
    private static void appendKeyValue(StringBuilder result, final String key,
                                       final String value, final boolean lastKey) {
        result.append(quote(key)).append(":").append(quote(value));
        if (!lastKey) {
            result.append(",");
        }
    }
}
