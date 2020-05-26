package eu.fasten.analyzer.repoclonerplugin;

import java.io.File;
import java.nio.file.Paths;

public class DirectoryHierarchyBuilder {

    private final String baseDir;

    public DirectoryHierarchyBuilder(String baseDir) {
        this.baseDir = baseDir;
    }

    public File getDirectoryFromHierarchy(String name) {
        var dir = Paths.get(this.baseDir, "mvn", String.valueOf(name.charAt(0)), name);
        return new File(dir.toString());
    }
}
