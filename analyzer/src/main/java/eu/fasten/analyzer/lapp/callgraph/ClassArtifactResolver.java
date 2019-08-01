package eu.fasten.analyzer.lapp.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.types.MethodReference;

public interface ClassArtifactResolver {
    ArtifactRecord artifactRecordFromMethodReference(MethodReference n);

    ArtifactRecord artifactRecordFromClass(IClass klass);
}
