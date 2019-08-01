package eu.fasten.analyzer.lapp.callgraph.wala;

import com.ibm.wala.types.ClassLoaderReference;
import eu.fasten.analyzer.lapp.callgraph.ClassToArtifactResolver;
import eu.fasten.analyzer.lapp.callgraph.FolderLayout.ArtifactFolderLayout;
import eu.fasten.analyzer.lapp.core.LappPackage;

public class WalaAnalysisTransformer {

    public static LappPackage toPackage(WalaAnalysisResult analysisResult, ArtifactFolderLayout layout) {
        ClassToArtifactResolver artifactResolver = new ClassToArtifactResolver(analysisResult.extendedCha, layout);

        LappPackageBuilder builder = new LappPackageBuilder(artifactResolver, layout);
        return builder.setPackages(analysisResult.extendedCha.getScope().getModules(ClassLoaderReference.Application))
                .insertCha(analysisResult.extendedCha)
                .insertCallGraph(analysisResult.cg)
                .build();

    }
}
