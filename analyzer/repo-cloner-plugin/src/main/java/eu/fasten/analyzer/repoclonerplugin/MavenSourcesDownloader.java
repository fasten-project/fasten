package eu.fasten.analyzer.repoclonerplugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;

public class MavenSourcesDownloader {

    private final String baseDir;

    public MavenSourcesDownloader(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Downloads JAR file from provided URL into hierarchical directory structure.
     *
     * @param jarUrl  URL at which the JAR file is located
     * @param product Product name (whose jar is downloading)
     * @return Path to saved JAR file
     * @throws IOException if could not save the file
     */
    public String downloadJarSources(String jarUrl, String product) throws IOException {
        if (!jarUrl.endsWith(".jar")) {
            throw new IllegalArgumentException("Invalid link to download JAR");
        }
        var dirHierarchy = new DirectoryHierarchyBuilder(baseDir);
        var jarPath = Paths.get(dirHierarchy.getDirectoryFromHierarchy(product).getAbsolutePath(),
                product + ".jar");
        FileUtils.copyURLToFile(new URL(jarUrl), new File(jarPath.toString()));
        return jarPath.toString();
    }
}
