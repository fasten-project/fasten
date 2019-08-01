package eu.fasten.analyzer.lapp.callgraph.FolderLayout;

import eu.fasten.analyzer.lapp.callgraph.ArtifactRecord;

import java.util.jar.JarFile;

public interface ArtifactFolderLayout {

    ArtifactRecord artifactRecordFromJarFile(JarFile path);
}
