package eu.fasten.core.data;

import java.util.Map;

public class JSONUtils {
    public static String toJSONString(final ExtendedRevisionJavaCallGraph erjcg){
        final var result = new StringBuilder("{");
        appendKeyValue(result, "forge", erjcg.forge);
        appendKeyValue(result,"product", erjcg.product);
        appendKeyValue(result,"version", erjcg.version);
        appendKeyValue(result,"generator", erjcg.cgGenerator);
        if (erjcg.timestamp >= 0) {
            appendKeyValue(result,"timestamp", String.valueOf(erjcg.timestamp));
        }
        appendKeyValue(result,"cha", classHierarchyToJSON(erjcg.classHierarchy));
        appendKeyValue(result,"graph", toJsonGraph(erjcg.graph));
        appendKeyValue(result,"nodes", String.valueOf(erjcg.nodeCount), true);

        return result.append("}").toString();
    }

    private static void appendKeyValue(final StringBuilder result, final String key,
                                       final String value){
        appendKeyValue(result, key, value, false);
    }
    private static void appendKeyValue(final StringBuilder result, final String key,
                                       final String value, final boolean lastKey) {
         result.append(qoute(key)).append(":").append(qoute(value));
         if(!lastKey){
             result.append(",");
         }
    }

    public static String qoute(final String s){
        return "\"" + s + "\"";
    }

    private static String toJsonGraph(final Graph graph) {
        return "";
    }

    private static String classHierarchyToJSON(final Map<JavaScope, Map<FastenURI, JavaType>> classHierarchy) {
        return "";
    }
}
