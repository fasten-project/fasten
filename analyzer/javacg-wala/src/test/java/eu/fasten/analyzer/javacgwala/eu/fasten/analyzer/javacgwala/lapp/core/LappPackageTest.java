package eu.fasten.analyzer.javacgwala.lapp.core;

import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.types.Selector;
import eu.fasten.analyzer.javacgwala.lapp.call.Call;
import eu.fasten.analyzer.javacgwala.lapp.call.ChaEdge;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;


class LappPackageTest {

    private static LappPackage lappPackage;

    private static ResolvedMethod sourceResolved;
    private static UnresolvedMethod sourceUnresolved;
    private static ResolvedMethod targetResolved;
    private static UnresolvedMethod targetUnresolved;

    @BeforeAll
    public static void setUp() {
        sourceResolved = new ResolvedMethod("name.space", Selector.make("<init>()V"), null);
        targetResolved = new ResolvedMethod("name.space", Selector.make("<clinit>()V"), null);
        sourceUnresolved = new UnresolvedMethod("name.space", Selector.make("<init>()V"));
        targetUnresolved = new UnresolvedMethod("name.space", Selector.make("<clinit>()V"));

        lappPackage = new LappPackage();
    }

    @Test
    void addResolvedMethod() {
        assertEquals(0, lappPackage.methods.size());

        lappPackage.addResolvedMethod(sourceResolved);

        assertEquals(1, lappPackage.methods.size());
        assertEquals(sourceResolved, lappPackage.methods.iterator().next());
    }

    @Test
    void addCallResolved() {
        assertEquals(0, lappPackage.resolvedCalls.size());

        lappPackage.addCall(sourceResolved, targetResolved, Call.CallType.STATIC);
        assertEquals(1, lappPackage.resolvedCalls.size());


        lappPackage.addCall(sourceResolved, targetResolved, Call.CallType.STATIC);
        assertEquals(1, lappPackage.resolvedCalls.size());

        lappPackage.addCall(targetResolved, sourceResolved, Call.CallType.STATIC);
        assertEquals(2, lappPackage.resolvedCalls.size());
    }

    @Test
    void addCallUnresolved() {
        assertEquals(0, lappPackage.resolvedCalls.size());

        lappPackage.addCall(sourceUnresolved, targetUnresolved, Call.CallType.UNKNOWN);
        assertEquals(1, lappPackage.unresolvedCalls.size());


        lappPackage.addCall(sourceUnresolved, targetUnresolved, Call.CallType.UNKNOWN);
        assertEquals(1, lappPackage.unresolvedCalls.size());

        lappPackage.addCall(targetUnresolved, sourceUnresolved, Call.CallType.UNKNOWN);
        assertEquals(2, lappPackage.unresolvedCalls.size());
    }

    @Test
    void addChaEdge() {
        assertEquals(0, lappPackage.cha.size());

        lappPackage.addChaEdge(sourceResolved, targetResolved, ChaEdge.ChaEdgeType.UNKNOWN);
        assertEquals(1, lappPackage.cha.size());


        lappPackage.addChaEdge(sourceResolved, targetResolved, ChaEdge.ChaEdgeType.UNKNOWN);
        assertEquals(1, lappPackage.cha.size());

        lappPackage.addChaEdge(targetResolved, sourceResolved, ChaEdge.ChaEdgeType.OVERRIDE);
        assertEquals(2, lappPackage.cha.size());
    }
}