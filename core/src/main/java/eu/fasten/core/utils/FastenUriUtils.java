package eu.fasten.core.utils;

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

    /**
     * Given a partial FASTEN URI, produces a list consisting of namespace, class, method name and its signature.
     * Specification: /{namespace}/{class}.<{method}>({signature.args})/{signature.returnType}
     *
     * @param partialFastenUri a partial FASTEN URI
     * @return List containing namespace, class name, method name and its signature.
     */
    public static List<String> parsePartialFastenUri(String partialFastenUri) {
        if (partialFastenUri.startsWith("fasten://") && partialFastenUri.contains("!") && partialFastenUri.contains("$") && partialFastenUri.contains("/")) {
            throw new IllegalArgumentException("Invalid partial FASTEN URI. You may want to use parser for full FASTEN URI instead.");
        }

        // Use regex to find the part before `.<`, which is start of the method.
        // This part is module, including namespace and class.
        Pattern modulePattern = Pattern.compile(".+?(?=\\.<)");
        Matcher moduleMatcher = modulePattern.matcher(partialFastenUri);
        if (!moduleMatcher.find())
            throw new IllegalArgumentException("Invalid partial FASTEN URI: module was not found.");

        var splitModule = moduleMatcher.group(0).split("/");
        var namespace = splitModule[1];
        var className = splitModule[2];

        // Use regex to find the part of `<{method}>`.
        Pattern methodNamePattern = Pattern.compile("(?<=<)(.+?)(?=>)");
        Matcher methodNameMatcher = methodNamePattern.matcher(partialFastenUri);
        if (!methodNameMatcher.find() || methodNameMatcher.group(0).isEmpty())
            throw new IllegalArgumentException("Invalid partial FASTEN URI: method name was not found.");

        var methodName = methodNameMatcher.group(0);

        // Use regex to find method's arguments of `({arguments})`.
        Pattern methodArgsPattern = Pattern.compile("(?<=\\()(.+?)(?=\\))");
        Matcher methodArgsMatcher = methodArgsPattern.matcher(partialFastenUri);
        if (!methodArgsMatcher.find() || methodArgsMatcher.group(0).isEmpty())
            throw new IllegalArgumentException("Invalid partial FASTEN URI: method's arguments were not found.");

        var methodArgs = methodArgsMatcher.group(0);

        // Use regex to find method's return type after closing `){return}`.
        Pattern methodReturnPattern = Pattern.compile("(?<=\\))(.*)");
        Matcher methodReturnMatcher = methodReturnPattern.matcher(partialFastenUri);
        if (!methodReturnMatcher.find() || methodReturnMatcher.group(0).isEmpty())
            throw new IllegalArgumentException("Invalid partial FASTEN URI: method's return type was not found.");

        var methodReturnType = methodReturnMatcher.group(0);


        return List.of(namespace, className, methodName, methodArgs, methodReturnType);
    }
}
