package eu.fasten.core.utils;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JavaNode;
import java.util.Map;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FastenUriUtils {

    /**
     * Given a package name, package version and partial FASTEN URI, produces full FASTEN URI.
     * Specification: fasten://{package_name}${version}/{partial_fasten_uri}
     *
     * @param packageName      Name of the package
     * @param version          Version of the package
     * @param partialFastenUri Partial FASTEN URI (including namespace)
     * @return Full-qualified FASTEN URI
     */
    public static String generateFullFastenUri(String packageName, String version, String partialFastenUri) {
        var builder = new StringBuilder();
        builder.append("fasten://");
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
     * Given a full FASTEN URI, produces a triple consisting of package name, package version and partial FASTEN URI.
     * Specification: fasten://{package_name}${version}/{partial_fasten_uri}
     *
     * @param fullFastenUri Fully-qualified FASTEN URI
     * @return Triple (package name, package version, partial FASTEN URI)
     */
    public static Triple<String, String, String> parseFullFastenUri(String fullFastenUri) {
        if (!fullFastenUri.startsWith("fasten://") && !fullFastenUri.contains("$") && !fullFastenUri.contains("/")) {
            throw new IllegalArgumentException("Invalid full FASTEN URI");
        }
        fullFastenUri = fullFastenUri.replaceFirst("fasten://", "");
        var packageName = fullFastenUri.split("\\$")[0];
        var versionAndPartialUri = fullFastenUri.split("\\$")[1];
        var version = versionAndPartialUri.split("/")[0];
        var partialUri = Arrays.stream(versionAndPartialUri.split("/")).skip(1).collect(Collectors.joining("/"));
        if (!partialUri.startsWith("/")) {
            partialUri = "/" + partialUri;
        }
        return new ImmutableTriple<>(packageName, version, partialUri);
    }
}
