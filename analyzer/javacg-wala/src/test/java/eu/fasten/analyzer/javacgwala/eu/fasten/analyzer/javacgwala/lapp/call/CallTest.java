package eu.fasten.analyzer.javacgwala.lapp.call;

import com.ibm.wala.types.Selector;
import eu.fasten.analyzer.javacgwala.data.fastenjson.CanonicalJSON;
import eu.fasten.analyzer.javacgwala.lapp.core.ResolvedMethod;
import eu.fasten.analyzer.javacgwala.lapp.core.UnresolvedMethod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class CallTest {

    private static Call resolvedCall;
    private static Call unresolvedCall;
    private static ResolvedMethod source;
    private static ResolvedMethod target;
    private static UnresolvedMethod unresolvedMethod;

    @BeforeAll
    public static void setUp() {
        source = new ResolvedMethod("name.space.SingleSourceToTarget", Selector.make("sourceMethod()V"), null);
        target = new ResolvedMethod("name.space.SingleSourceToTarget", Selector.make("targetMethod()V"), null);
        unresolvedMethod = new UnresolvedMethod("name.space.SingleSourceToTarget", Selector.make("<init>()V"));
        resolvedCall = new Call(source, target, Call.CallType.STATIC);
        unresolvedCall = new Call(source, unresolvedMethod, Call.CallType.STATIC);
    }

    @Test
    void toURICall() {
        var sourceURI = CanonicalJSON.convertToFastenURI(source);
        var targetURI = CanonicalJSON.convertToFastenURI(target);

        var fastenUri = resolvedCall.toURICall(sourceURI, targetURI);

        assertEquals("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid",
                fastenUri[0].toString());
        assertEquals("///name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid",
                fastenUri[1].toString());
    }

    @Test
    void getLabel() {
        assertEquals("invoke_static" ,resolvedCall.getLabel());
    }

    @Test
    void isResolved() {
        assertTrue(resolvedCall.isResolved());
        assertFalse(unresolvedCall.isResolved());
    }
}