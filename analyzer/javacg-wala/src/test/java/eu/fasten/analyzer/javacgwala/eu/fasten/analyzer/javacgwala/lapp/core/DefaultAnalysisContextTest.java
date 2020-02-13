package eu.fasten.analyzer.javacgwala.lapp.core;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.types.Selector;
import eu.fasten.analyzer.javacgwala.data.callgraph.WalaCallGraph;
import eu.fasten.analyzer.javacgwala.generator.WalaCallgraphConstructor;
import eu.fasten.analyzer.javacgwala.generator.WalaUFIAdapter;
import eu.fasten.analyzer.javacgwala.lapp.call.Call;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultAnalysisContextTest {

    private static DefaultAnalysisContext defaultAnalysisContext;
    private static JarFile artifact;
    private static Call resolvedCall;
    private static Call unresolvedCall;
    private static ResolvedMethod sourceResolved;
    private static UnresolvedMethod sourceUnresolved;
    private static ResolvedMethod targetResolved;
    private static UnresolvedMethod targetUnresolved;

    @BeforeAll
    public static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        File file = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile());

        artifact = new JarFile(file);

        sourceResolved = new ResolvedMethod("name.space", Selector.make("<init>()V"), null);
        targetResolved = new ResolvedMethod("name.space", Selector.make("<clinit>()V"), null);
        sourceUnresolved = new UnresolvedMethod("name.space", Selector.make("<init>()V"));
        targetUnresolved = new UnresolvedMethod("name.space", Selector.make("<clinit>()V"));

        resolvedCall = new Call(sourceResolved, targetResolved, Call.CallType.STATIC);
        unresolvedCall = new Call(sourceUnresolved, targetUnresolved, Call.CallType.STATIC);

        defaultAnalysisContext = new DefaultAnalysisContext();
    }

    @Test
    void makeResolvedNewEntry() {
        var symbol = resolvedCall.source.symbol;
        var namespace = "test.namespace";

        var resolved = defaultAnalysisContext.makeResolved(namespace, symbol, artifact);

        assertEquals(artifact, resolved.artifact);
        assertEquals(namespace, resolved.namespace);
        assertEquals(symbol, resolved.symbol);
    }

    @Test
    void makeResolvedExisting() {
        var symbol = resolvedCall.source.symbol;
        var namespace = "test.namespace";

        var resolved = defaultAnalysisContext.makeResolved(namespace, symbol, artifact);

        assertEquals(artifact, resolved.artifact);
        assertEquals(namespace, resolved.namespace);
        assertEquals(symbol, resolved.symbol);

        var sameResolved = defaultAnalysisContext.makeResolved(namespace, symbol, artifact);

        assertEquals(resolved, sameResolved);
    }

    @Test
    void makeResolvedNullJar() {
        var symbol = resolvedCall.source.symbol;
        var namespace = resolvedCall.source.namespace;

        var resolved = defaultAnalysisContext.makeResolved(namespace, symbol, null);

        assertEquals("Unknown", resolved.toID());
    }

    @Test
    void makeUnresolvedNewEntry() {
        var symbol = unresolvedCall.source.symbol;
        var namespace = "test.namespace";

        var unresolved = defaultAnalysisContext.makeUnresolved(namespace, symbol);

        assertEquals(namespace, unresolved.namespace);
        assertEquals(symbol, unresolved.symbol);
    }

    @Test
    void makeUnresolvedExisting() {
        var symbol = unresolvedCall.source.symbol;
        var namespace = "test.namespace";

        var unresolved = defaultAnalysisContext.makeUnresolved(namespace, symbol);

        assertEquals(namespace, unresolved.namespace);
        assertEquals(symbol, unresolved.symbol);

        var sameUnresolved = defaultAnalysisContext.makeUnresolved(namespace, symbol);

        assertEquals(unresolved, sameUnresolved);
    }
}