package eu.fasten.core.utils;

public class FastenUriUtils {
    
    public static String generateFullFastenUri(String packageName, String version,
                                               String namespace, String partialFastenUri) {
        var builder = new StringBuilder();
        builder.append("fasten://");
        builder.append(packageName);
        builder.append("$");
        builder.append(version);
        if (!namespace.startsWith("/")) {
            builder.append("/");
        }
        builder.append(namespace);
        if (!partialFastenUri.startsWith("/")) {
            builder.append("/");
        }
        builder.append(partialFastenUri);
        return builder.toString();
    }
}
