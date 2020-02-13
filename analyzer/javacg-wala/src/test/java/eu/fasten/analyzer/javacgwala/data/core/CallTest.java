package eu.fasten.analyzer.javacgwala.data.core;

import com.ibm.wala.types.Selector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    void getSource() {
        assertEquals(source, resolvedCall.getSource());
        assertEquals(source, unresolvedCall.getSource());
    }

    @Test
    void getTarget() {
        assertEquals(target, resolvedCall.getTarget());
        assertEquals(unresolvedMethod, unresolvedCall.getTarget());
    }

    @Test
    void getCallType() {
        assertEquals(Call.CallType.STATIC, resolvedCall.getCallType());
    }

    @Test
    void toURICall() {
        var fastenUri = resolvedCall.toURICall();

        assertEquals("/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid",
                fastenUri[0].toString());
        assertEquals("///name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid",
                fastenUri[1].toString());
    }
}