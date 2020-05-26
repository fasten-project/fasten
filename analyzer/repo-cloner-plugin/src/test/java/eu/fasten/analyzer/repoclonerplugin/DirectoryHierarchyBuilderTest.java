package eu.fasten.analyzer.repoclonerplugin;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DirectoryHierarchyBuilderTest {

    @Test
    public void getDirectoryFromHierarchyTest() {
        String baseDir = "test";
        String name = "folder";
        var hierarchyBuilder = new DirectoryHierarchyBuilder(baseDir);
        var dir = hierarchyBuilder.getDirectoryFromHierarchy(name);
        Assertions.assertEquals("test/mvn/f/folder", dir.getPath());
    }
}
