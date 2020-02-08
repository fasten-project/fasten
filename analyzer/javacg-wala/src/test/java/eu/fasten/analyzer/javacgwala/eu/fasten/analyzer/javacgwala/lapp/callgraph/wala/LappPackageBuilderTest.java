package eu.fasten.analyzer.javacgwala.lapp.callgraph.wala;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import eu.fasten.analyzer.javacgwala.generator.WalaCallgraphConstructor;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.ClassToArtifactResolver;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.folderlayout.ArtifactFolderLayout;
import eu.fasten.analyzer.javacgwala.lapp.callgraph.folderlayout.DollarSeparatedLayout;
import eu.fasten.analyzer.javacgwala.lapp.core.LappPackage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

class LappPackageBuilderTest {

    private static LappPackageBuilder builder;
    private static CallGraph graph;

    @BeforeAll
    static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        var path = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();

        graph = WalaCallgraphConstructor.buildCallGraph(path);

        ArtifactFolderLayout layout = new DollarSeparatedLayout();
        IClassHierarchy cha = graph.getClassHierarchy();

        builder = new LappPackageBuilder(new ClassToArtifactResolver(cha, layout), layout);
    }

    @Test
    void build() {
        LappPackage lappPackage = builder
                .setPackages(graph.getClassHierarchy().getScope().getModules(ClassLoaderReference.Application))
                .insertCha(graph.getClassHierarchy())
                .insertCallGraph(graph)
                .build();

        assertEquals(1, lappPackage.resolvedCalls.size());

        var resolvedCall = lappPackage.resolvedCalls.iterator().next();
        assertEquals("name.space.SingleSourceToTarget", resolvedCall.source.namespace);
        assertEquals("sourceMethod()V", resolvedCall.source.symbol.toString());
        assertEquals("name.space.SingleSourceToTarget", resolvedCall.target.namespace);
        assertEquals("targetMethod()V", resolvedCall.target.symbol.toString());


        assertEquals(1, lappPackage.unresolvedCalls.size());

        var unresolvedCall = lappPackage.unresolvedCalls.iterator().next();
        assertEquals("name.space.SingleSourceToTarget", unresolvedCall.source.namespace);
        assertEquals("<init>()V", unresolvedCall.source.symbol.toString());
        assertEquals("java.lang.Object", unresolvedCall.target.namespace);
        assertEquals("<init>()V", unresolvedCall.target.symbol.toString());

        assertEquals(0, lappPackage.cha.size());

        assertEquals(1, lappPackage.artifacts.size());
    }
}