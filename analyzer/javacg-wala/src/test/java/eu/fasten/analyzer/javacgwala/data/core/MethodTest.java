package eu.fasten.analyzer.javacgwala.lapp.core;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.WalaResultAnalysis;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

public class MethodTest {

    private static CallGraph ssttgraph, cigraph, lambdagraph, arraygraph;

    @BeforeAll
    public static void setUp() throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        /**
         * SingleSourceToTarget:
         *
         * package name.space;
         *
         * public class SingleSourceToTarget{
         *
         *     public static void sourceMethod() { targetMethod(); }
         *
         *     public static void targetMethod() {}
         * }
         */
        var ssttpath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();

        ssttgraph = CallGraphConstructor.generateCallGraph(ssttpath);

        /**
         * ClassInit:
         *
         * package name.space;
         *
         * public class ClassInit{
         *
         * public static void targetMethod(){}
         *
         *     static{
         *         targetMethod();
         *     }
         * }
         *
         */
        var cipath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("ClassInit.jar")
                .getFile()).getAbsolutePath();

        cigraph = CallGraphConstructor.generateCallGraph(cipath);

        /**
         * LambdaExample:
         *
         * package name.space;
         *
         * import java.util.function.Function;
         *
         * public class LambdaExample {
         *     Function f = (it) -> "Hello";
         * }
         */
        var lambdapath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("LambdaExample.jar")
                .getFile()).getAbsolutePath();

        lambdagraph = CallGraphConstructor.generateCallGraph(lambdapath);

        /**
         * ArrayExample:
         *
         *package name.space;
         *
         * public class ArrayExample{
         *     public static void sourceMethod() {
         *         Object[] object = new Object[1];
         *         targetMethod(object);
         *     }
         *
         *     public static Object[] targetMethod(Object[] obj) {
         *         return obj;
         *     }
         * }
         */
        var arraypath = new File(Thread.currentThread().getContextClassLoader()
                .getResource("ArrayExample.jar")
                .getFile()).getAbsolutePath();

        arraygraph = CallGraphConstructor.generateCallGraph(arraypath);
    }

    @Test
    public void toCanonicalJSONSingleSourceToTargetTest() {

        var wrapped = WalaResultAnalysis.wrap(ssttgraph, null);

        assertEquals(1, wrapped.getResolvedCalls().size());

        var resolvedCall = wrapped.getResolvedCalls().get(0);
        var unresolvedCall = wrapped.getUnresolvedCalls().get(0);

        // Actual URIs
        var actualSourceURI = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid";
        var actualSourceUnresolvedURI =
                "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid";
        var actualTargetUnresolvedURI = "/java.lang/Object.Object()%2Fjava.lang%2FVoid";


        assertEquals(actualSourceURI,
                resolvedCall.getSource().toCanonicalSchemalessURI().toString());

        assertEquals(actualTargetURI,
                resolvedCall.getTarget().toCanonicalSchemalessURI().toString());

        assertEquals(actualSourceUnresolvedURI,
                unresolvedCall.getSource().toCanonicalSchemalessURI().toString());

        assertEquals(actualTargetUnresolvedURI,
                unresolvedCall.getTarget().toCanonicalSchemalessURI().toString());
    }

    @Test
    public void toCanonicalJSONClassInitTest() {

        var wrapped = WalaResultAnalysis.wrap(cigraph, null);

        assertEquals(1, wrapped.getResolvedCalls().size());

        var resolvedCall = wrapped.getResolvedCalls().get(0);

        // Actual URIs
        var actualSourceURI = "/name.space/ClassInit.%3Cinit%3E()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/name.space/ClassInit.targetMethod()%2Fjava.lang%2FVoid";

        assertEquals(actualSourceURI,
                resolvedCall.getSource().toCanonicalSchemalessURI().toString());

        assertEquals(actualTargetURI,
                resolvedCall.getTarget().toCanonicalSchemalessURI().toString());
    }

    @Test
    public void toCanonicalJSONLambdaTest() {

        var wrapped = WalaResultAnalysis.wrap(lambdagraph, null);

        assertEquals(2, wrapped.getUnresolvedCalls().size());

        var unresolvedCall = wrapped.getUnresolvedCalls().get(1);

        // Actual URIs
        var actualSourceURI = "/name.space/LambdaExample.LambdaExample()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/java.lang.invoke/LambdaMetafactory.apply()%2Fjava"
                + ".util.function%2FFunction";

        assertEquals(actualSourceURI,
                unresolvedCall.getSource().toCanonicalSchemalessURI().toString());

        assertEquals(actualTargetURI,
                unresolvedCall.getTarget().toCanonicalSchemalessURI().toString());
    }

    @Test
    public void toCanonicalJSONArrayTest() {

        var wrapped = WalaResultAnalysis.wrap(arraygraph, null);

        assertEquals(1, wrapped.getResolvedCalls().size());

        var resolvedCall = wrapped.getResolvedCalls().get(0);

        // Actual URIs
        var actualSourceURI = "/name.space/ArrayExample.sourceMethod()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/name.space/ArrayExample.targetMethod(%2Fjava"
                + ".lang%2FObject%25255B%25255D)%2Fjava.lang%2FObject%25255B%25255D";

        assertEquals(actualSourceURI,
                resolvedCall.getSource().toCanonicalSchemalessURI().toString());

        assertEquals(actualTargetURI,
                resolvedCall.getTarget().toCanonicalSchemalessURI().toString());
    }

    @Test
    public void toID() {
        var path = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();

        var wrapped = WalaResultAnalysis.wrap(ssttgraph, null);

        var resolvedCall = wrapped.getResolvedCalls().get(0);
        var unresolvedCall = wrapped.getUnresolvedCalls().get(0);

        assertEquals(path + "::name.space.SingleSourceToTarget.sourceMethod()V",
                resolvedCall.getSource().toID());
        assertEquals(path + "::name.space.SingleSourceToTarget.<init>()V",
                unresolvedCall.getSource().toID());
    }
}