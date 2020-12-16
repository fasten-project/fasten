package eu.fasten.core.utils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FastenUriUtils {

    /**
     * Given a package name, package version and partial FASTEN URI, produces full FASTEN URI.
     * Specification: fasten://{forge}!{package_name}${version}/{partial_fasten_uri}
     *
     * @param packageName      Name of the package
     * @param version          Version of the package
     * @param partialFastenUri Partial FASTEN URI (including namespace)
     * @return Full-qualified FASTEN URI
     */
    public static String generateFullFastenUri(String forge, String packageName, String version, String partialFastenUri) {
        var builder = new StringBuilder();
        builder.append("fasten://");
        builder.append(forge);
        builder.append("!");
        builder.append(packageName);
        builder.append("$");
        builder.append(version);
        if (!partialFastenUri.startsWith("/")) {
            builder.append("/");
        }
        builder.append(partialFastenUri);
        return builder.toString();
    }

    /**
     * Given a full FASTEN URI, produces a list consisting of package name, package version and partial FASTEN URI.
     * Specification: fasten://{forge}!{package_name}${version}/{partial_fasten_uri}
     *
     * @param fullFastenUri Fully-qualified FASTEN URI
     * @return List containing first forge, then package name, then package version, and lastly partial FASTEN URI
     */
    public static List<String> parseFullFastenUri(String fullFastenUri) {
        if (!fullFastenUri.startsWith("fasten://") || !fullFastenUri.contains("!") || !fullFastenUri.contains("$") || !fullFastenUri.contains("/")) {
            throw new IllegalArgumentException("Invalid full FASTEN URI");
        }
        fullFastenUri = fullFastenUri.replaceFirst("fasten://", "");
        var forge = fullFastenUri.split("!")[0];
        fullFastenUri = Arrays.stream(fullFastenUri.split("!")).skip(1).collect(Collectors.joining("!"));
        var packageName = fullFastenUri.split("\\$")[0];
        var versionAndPartialUri = fullFastenUri.split("\\$")[1];
        var version = versionAndPartialUri.split("/")[0];
        var partialUri = Arrays.stream(versionAndPartialUri.split("/")).skip(1).collect(Collectors.joining("/"));
        if (!partialUri.startsWith("/")) {
            partialUri = "/" + partialUri;
        }
        return List.of(forge, packageName, version, partialUri);
    }
}
