package eu.fasten.core.utils;

import eu.fasten.core.data.opal.MavenCoordinate;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.apache.commons.lang3.tuple.Pair;

public class TypeToJarMapper {

    public static final String CLASS_EXT = ".class";
    public static final String JAR_EXT = ".jar";
    public static final String SLASH = "/";
    public static final String DOT = ".";
    public static final String EMPTY = "";

    public static Map<String, String> createTypeUriToCoordMap(final List<Pair<MavenCoordinate, File>> jars) {
        Map<String, String> result = new Object2ObjectOpenHashMap<>();
        for (final var dep : jars) {
            final var depJarFile = jarOrThrow(dep.getRight());
            final var coord = dep.getLeft().getCoordinate();
            depJarFile.stream().forEach(jarEntry -> {
                if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(CLASS_EXT)) {
                    final var uriWithDots = jarEntry.getName().replace(CLASS_EXT, EMPTY).replace(SLASH, DOT);
                    final var dotIndex = uriWithDots.lastIndexOf(DOT);
                    final var slash = new StringBuilder(SLASH);
                    if (dotIndex != -1) {
                        final var typeUri = slash.append(uriWithDots, 0, dotIndex).append(
                            uriWithDots.substring(dotIndex).replace(DOT, SLASH)).toString();
                        result.put(typeUri, coord);
                    }
                    result.put(slash.append(uriWithDots).toString(), coord);
                }

            });
        }
        return result;
    }

    private static JarFile jarOrThrow(final File depJar) {
        try {
            return new JarFile(depJar);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
