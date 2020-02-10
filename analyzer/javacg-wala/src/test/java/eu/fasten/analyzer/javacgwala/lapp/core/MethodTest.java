package eu.fasten.analyzer.javacgwala.lapp.core;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import eu.fasten.analyzer.javacgwala.data.callgraph.WalaCallGraph;
import eu.fasten.analyzer.javacgwala.data.fastenjson.CanonicalJSON;
import eu.fasten.analyzer.javacgwala.generator.WalaCallgraphConstructor;
import eu.fasten.analyzer.javacgwala.generator.WalaUFIAdapter;
import eu.fasten.analyzer.javacgwala.lapp.call.Call;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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

        ssttgraph = WalaCallgraphConstructor.buildCallGraph(ssttpath);

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

        cigraph = WalaCallgraphConstructor.buildCallGraph(cipath);

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

        lambdagraph = WalaCallgraphConstructor.buildCallGraph(lambdapath);

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

        arraygraph = WalaCallgraphConstructor.buildCallGraph(arraypath);
    }

    @Test
    public void toCanonicalJSONSingleSourceToTargetTest() {

        var wrapped = WalaUFIAdapter.wrap(new WalaCallGraph(ssttgraph, new ArrayList<>()));

        assertEquals(1, wrapped.lappPackage.resolvedCalls.size());

        var resolvedCall = wrapped.lappPackage.resolvedCalls.iterator().next();
        var unresolvedCall = wrapped.lappPackage.unresolvedCalls.iterator().next();

        // Actual URIs
        var actualSourceURI = "/name.space/SingleSourceToTarget.sourceMethod()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/name.space/SingleSourceToTarget.targetMethod()%2Fjava.lang%2FVoid";
        var actualSourceUnresolvedURI =
                "/name.space/SingleSourceToTarget.SingleSourceToTarget()%2Fjava.lang%2FVoid";
        var actualTargetUnresolvedURI = "/java.lang/Object.Object()Void";

        var fastenJavaUriSource = CanonicalJSON.convertToFastenURI(resolvedCall.source);
        var fastenJavaUriTarget = CanonicalJSON.convertToFastenURI(resolvedCall.target);
        var unresolvedUriSource = CanonicalJSON.convertToFastenURI(unresolvedCall.source);
        var unresolvedUriTarget = CanonicalJSON.convertToFastenURI(unresolvedCall.target);

        assertEquals(actualSourceURI,
                Method.toCanonicalSchemalessURI(fastenJavaUriSource).toString());

        assertEquals(actualTargetURI,
                Method.toCanonicalSchemalessURI(fastenJavaUriTarget).toString());

        assertEquals(actualSourceUnresolvedURI,
                Method.toCanonicalSchemalessURI(unresolvedUriSource).toString());

        assertEquals(actualTargetUnresolvedURI,
                Method.toCanonicalSchemalessURI(unresolvedUriTarget).toString());
    }

    @Test
    public void toCanonicalJSONClassInitTest() {

        var wrapped = WalaUFIAdapter.wrap(new WalaCallGraph(cigraph, new ArrayList<>()));

        assertEquals(1, wrapped.lappPackage.resolvedCalls.size());

        var resolvedCall = wrapped.lappPackage.resolvedCalls.iterator().next();

        // Actual URIs
        var actualSourceURI = "/name.space/ClassInit.%3Cinit%3E()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/name.space/ClassInit.targetMethod()%2Fjava.lang%2FVoid";

        var fastenJavaUriSource = CanonicalJSON.convertToFastenURI(resolvedCall.source);
        var fastenJavaUriTarget = CanonicalJSON.convertToFastenURI(resolvedCall.target);


        assertEquals(actualSourceURI,
                Method.toCanonicalSchemalessURI(fastenJavaUriSource).toString());

        assertEquals(actualTargetURI,
                Method.toCanonicalSchemalessURI(fastenJavaUriTarget).toString());
    }

    @Test
    public void toCanonicalJSONLambdaTest() {

        var wrapped = WalaUFIAdapter.wrap(new WalaCallGraph(lambdagraph, new ArrayList<>()));

        assertEquals(2, wrapped.lappPackage.unresolvedCalls.size());

        Call unresolvedCall = null;

        for (var call : wrapped.lappPackage.unresolvedCalls) {
            if (call.callType == Call.CallType.STATIC) {
                unresolvedCall = call;
            }
        }

        assertNotNull(unresolvedCall);

        // Actual URIs
        var actualSourceURI = "/name.space/LambdaExample.LambdaExample()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/java.lang.invoke/LambdaMetafactory.apply()%2Fjava"
                + ".util.function%2FFunction";

        var fastenJavaUriSource = CanonicalJSON.convertToFastenURI(unresolvedCall.source);
        var fastenJavaUriTarget = CanonicalJSON.convertToFastenURI(unresolvedCall.target);

        assertEquals(actualSourceURI,
                Method.toCanonicalSchemalessURI(fastenJavaUriSource).toString());

        assertEquals(actualTargetURI,
                Method.toCanonicalSchemalessURI(fastenJavaUriTarget).toString());
    }

    @Test
    public void toCanonicalJSONArrayTest() {

        var wrapped = WalaUFIAdapter.wrap(new WalaCallGraph(arraygraph, new ArrayList<>()));

        assertEquals(1, wrapped.lappPackage.resolvedCalls.size());

        var resolvedCall = wrapped.lappPackage.resolvedCalls.iterator().next();

        // Actual URIs
        var actualSourceURI = "/name.space/ArrayExample.sourceMethod()%2Fjava.lang%2FVoid";
        var actualTargetURI = "/name.space/ArrayExample.targetMethod(%2Fjava"
                + ".lang%2FObject%25255B%25255D)%2Fjava.lang%2FObject%25255B%25255D";

        var fastenJavaUriSource = CanonicalJSON.convertToFastenURI(resolvedCall.source);
        var fastenJavaUriTarget = CanonicalJSON.convertToFastenURI(resolvedCall.target);

        assertEquals(actualSourceURI,
                Method.toCanonicalSchemalessURI(fastenJavaUriSource).toString());

        assertEquals(actualTargetURI,
                Method.toCanonicalSchemalessURI(fastenJavaUriTarget).toString());
    }

    @Test
    public void toID() {
        var path = new File(Thread.currentThread().getContextClassLoader()
                .getResource("SingleSourceToTarget.jar")
                .getFile()).getAbsolutePath();

        var wrapped = WalaUFIAdapter.wrap(new WalaCallGraph(ssttgraph, new ArrayList<>()));

        var resolvedCall = wrapped.lappPackage.resolvedCalls.iterator().next();
        var unresolvedCall = wrapped.lappPackage.unresolvedCalls.iterator().next();

        assertEquals(path + "::name.space.SingleSourceToTarget.sourceMethod()V",
                resolvedCall.source.toID());
        assertEquals(path + "::name.space.SingleSourceToTarget.<init>()V",
                unresolvedCall.source.toID());
    }
}