package eu.fasten.core.utils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
     * Specification: `fasten://{forge}!{package_name}${version}/{partial_fasten_uri}`
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
        var versionAndPartialUri = Arrays.stream(fullFastenUri.split("\\$")).skip(1).collect(Collectors.joining("$"));
        var version = versionAndPartialUri.split("/")[0];
        var partialUri = Arrays.stream(versionAndPartialUri.split("/")).skip(1).collect(Collectors.joining("/"));
        if (!partialUri.startsWith("/")) {
            partialUri = "/" + partialUri;
        }
        return List.of(forge, packageName, version, partialUri);
    }

    /**
     * Given a partial FASTEN URI, produces a list consisting of namespace, class, method name and its signature.
     * Specification: `/{namespace}/{class}.{method}({signature.args})/{signature.returnType}`
     *
     * @param partialFastenUri a partial FASTEN URI
     * @return List containing namespace, class name, method name, method's args, method's return type.
     */
    public static List<String> parsePartialFastenUri(String partialFastenUri) {

        // Exception messages
        var fullUriException = "Invalid partial FASTEN URI. You may want to use parser for full FASTEN URI instead.";
        var partialUriFormatException = "Invalid partial FASTEN URI. The format is corrupted.\nMust be: `/{namespace}/{class}.{method}({signature.args})/{signature.returnType}`";

        // Ensure that this is not a Full FASTEN URI
        if (partialFastenUri.startsWith("fasten://") && partialFastenUri.contains("!") && partialFastenUri.contains("$") && partialFastenUri.contains("/")) {
            throw new IllegalArgumentException(fullUriException);
        }

        // URI is usually stored encoded, so decode for parsing.
        // E.g., `/com.sun.istack.localization/Localizer.%3Cinit%3E(%2Fjava.util%2FLocale)%2Fjava.lang%2FVoidType`
        partialFastenUri = java.net.URLDecoder.decode(partialFastenUri, StandardCharsets.UTF_8);

        // Namespace: `/{namespace}/`
        Pattern namespacePattern = Pattern.compile("(?<=/)(.+?)(?=/)");
        Matcher namespaceMatcher = namespacePattern.matcher(partialFastenUri);
        if (!namespaceMatcher.find() || namespaceMatcher.group(0).isEmpty())
            throw new IllegalArgumentException(partialUriFormatException + "; failed to parse namespace.");

        // Class: `/{class}.*(`
        Pattern classPattern = Pattern.compile("(?<=/)([^,;./]+?)(?=(\\$|\\.)([^./]+)\\()");
        Matcher classMatcher = classPattern.matcher(partialFastenUri);
        if (!classMatcher.find() || classMatcher.group(0).isEmpty())
            throw new IllegalArgumentException(partialUriFormatException + "; failed to parse class name.");


        // Method: `.{method}(`
        Pattern methodNamePattern = Pattern.compile("(?<=\\.(\\$?))([^,;./]+?)(?=\\()");
        Matcher methodNameMatcher = methodNamePattern.matcher(partialFastenUri);
        if (!methodNameMatcher.find() || methodNameMatcher.group(0).isEmpty())
            throw new IllegalArgumentException(partialUriFormatException + "; failed to parse method name.");


        // Method Args: `({args})`
        Pattern methodArgsPattern = Pattern.compile("(?<=" + Pattern.quote(methodNameMatcher.group(0)) + "\\()(.*?)(?=\\))");
        Matcher methodArgsMatcher = methodArgsPattern.matcher(partialFastenUri);
        if (!methodArgsMatcher.find())
            throw new IllegalArgumentException(partialUriFormatException + "; failed to parse method args.");

        // Method Return Type: `)/{type}`
        Pattern methodReturnPattern = Pattern.compile(
                "(?<=" + Pattern.quote(methodNameMatcher.group(0)) + "\\(" + Pattern.quote(methodArgsMatcher.group(0)) + "\\))(.*)");
        Matcher methodReturnMatcher = methodReturnPattern.matcher(partialFastenUri);
        if (!methodReturnMatcher.find() || methodReturnMatcher.group(0).isEmpty())
            throw new IllegalArgumentException(partialUriFormatException + "; failed to parse return type.");


        var namespace = namespaceMatcher.group(0);
        var className = classMatcher.group(0);
        var methodName = methodNameMatcher.group(0);
        var methodArgs = methodArgsMatcher.group(0);
        var methodReturnType = methodReturnMatcher.group(0);

        return List.of(namespace, className, methodName, methodArgs, methodReturnType);
    }

}
