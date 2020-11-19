package eu.fasten.core.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSONUtils {

    public static String toJSONString(final ExtendedRevisionJavaCallGraph erjcg) {
        var result = new StringBuilder();
        appendArtifactInformation(erjcg, result);
        appendCha(result, erjcg.classHierarchy);
        appendGraph(result, erjcg.graph);
        if (erjcg.timestamp >= 0) {
            appendKeyValue(result, "timestamp", erjcg.timestamp, true);
        }
        curlyBracket(result);
        return result.toString();
    }

    private static void appendArtifactInformation(ExtendedRevisionJavaCallGraph erjcg,
                                                  final StringBuilder result) {
        appendKeyValue(result, "product", erjcg.product);
        appendKeyValue(result, "nodes", erjcg.nodeCount);
        appendKeyValue(result, "forge", erjcg.forge);
        appendKeyValue(result, "generator", erjcg.cgGenerator);
        appendKeyValue(result, "version", erjcg.version);
    }

    private static void appendGraph(StringBuilder result, final Graph graph) {
        result.append("\"graph\":{\"internalCalls\":[");
        for (final var entry : graph.getInternalCalls().entrySet()) {
            appendCalls(result, entry);
        }
        removeLastIfNotEmpty(result, graph.getInternalCalls().size());
        result.append("],\"externalCalls\":[");
        for (final var entry : graph.getExternalCalls().entrySet()) {
            appendCalls(result, entry);
        }
        removeLastIfNotEmpty(result, graph.getExternalCalls().size());
        result.append("],\"resolvedCalls\":[");
        for (final var entry : graph.getResolvedCalls().entrySet()) {
            appendCalls(result, entry);
        }
        removeLastIfNotEmpty(result, graph.getResolvedCalls().size());
        result.append("]},");

    }

    private static void removeLastIfNotEmpty(StringBuilder result,
                                             int size) {
        if (size != 0) {
            result.setLength(result.length() - 1);
        }
    }

    private static void appendCalls(StringBuilder result,
                                    final Map.Entry<List<Integer>, Map<Object, Object>> entry) {
        result.append("[").append(qoute(entry.getKey().get(0).toString())).append(",");
        result.append(qoute(entry.getKey().get(1).toString())).append(",{");
        toCallableMetadataJson(result, entry.getValue());
        result.append("}],");
    }

    private static void toCallableMetadataJson(StringBuilder result,
                                               final Map<Object, Object> metadata) {
        for (final var entry : metadata.entrySet()) {
            final var callSite = (HashMap<String, Object>) entry.getValue();
            result.append(qoute(entry.getKey().toString())).append(":{");
            appendMetadata(result, callSite);
            result.append("},");
        }
        removeLastIfNotEmpty(result, metadata.size());
    }

    private static void appendCha(StringBuilder result, final Map<JavaScope,
        Map<FastenURI, JavaType>> cha) {
        result.append("\"cha\":{\"externalTypes\":{");
        for (final var entry : cha.get(JavaScope.externalTypes).entrySet()) {
            appendType(result, entry.getKey().toString(), entry.getValue());
        }
        removeLastIfNotEmpty(result, cha.get(JavaScope.externalTypes).size());

        result.append("},\"internalTypes\":{");
        for (final var entry : cha.get(JavaScope.internalTypes).entrySet()) {
            appendType(result, entry.getKey().toString(), entry.getValue());
        }
        removeLastIfNotEmpty(result, cha.get(JavaScope.internalTypes).size());

        result.append("},\"resolvedTypes\":{");
        for (final var entry : cha.get(JavaScope.resolvedTypes).entrySet()) {
            appendType(result, entry.getKey().toString(), entry.getValue());
        }
        removeLastIfNotEmpty(result, cha.get(JavaScope.resolvedTypes).size());

        result.append("}},");

    }

    private static void appendType(StringBuilder result, final String key,
                                   final JavaType type) {
        result.append(qoute(key)).append(":{");
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
        result.append("]},");

    }

    public static void appendSupers(StringBuilder result, final List<?> list) {
        for (final var fastenURI : list) {
            result.append(qoute(fastenURI.toString())).append(",");
        }
        removeLastIfNotEmpty(result, list.size());

    }

    private static void appendMethods(StringBuilder result,
                                      final Map<Integer, JavaNode> methods) {
        for (final var entry : methods.entrySet()) {

            result.append(qoute(entry.getKey().toString()));
            result.append(":{\"metadata\":{");
            appendMetadata(result, entry.getValue().getMetadata());
            result.append("},");
            appendKeyValue(result, "uri", entry.getValue().getUri().toString(), true);
            result.append("},");
        }
        removeLastIfNotEmpty(result, methods.size());

    }

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

    private static String qoute(final String s) {
        return "\"" + s + "\"";
    }

    private static void curlyBracket(StringBuilder result) {
        result.insert(0, "{").append("}");
    }

    private static void appendKeyValue(StringBuilder result, final String key,
                                       final Number value) {
        appendKeyValue(result, key, value, false);
    }

    private static void appendKeyValue(StringBuilder result, final String key,
                                       final Number value, final boolean lastKey) {
        result.append(qoute(key)).append(":").append(value);
        if (!lastKey) {
            result.append(",");
        }
    }

    private static void appendKeyValue(StringBuilder result, final String key,
                                       final String value) {
        appendKeyValue(result, key, value, false);
    }

    private static void appendKeyValue(StringBuilder result, final String key,
                                       final String value, final boolean lastKey) {
        result.append(qoute(key)).append(":").append(qoute(value));
        if (!lastKey) {
            result.append(",");
        }
    }
}
