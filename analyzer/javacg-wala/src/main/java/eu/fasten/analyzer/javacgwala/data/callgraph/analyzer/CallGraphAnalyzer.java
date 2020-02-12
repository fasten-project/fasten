package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import eu.fasten.analyzer.javacgwala.data.ArtifactResolver;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import eu.fasten.analyzer.javacgwala.data.core.Call;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.analyzer.javacgwala.data.core.ResolvedMethod;
import eu.fasten.analyzer.javacgwala.data.core.UnresolvedMethod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.jar.JarFile;

public class CallGraphAnalyzer {

    private final CallGraph rawCallGraph;

    private final IClassHierarchy cha;

    private final ArtifactResolver artifactResolver;

    private final PartialCallGraph partialCallGraph;

    private final HashMap<String, ResolvedMethod> resolvedDictionary = new HashMap<>();
    private final HashMap<String, UnresolvedMethod> unresolvedDictionary = new HashMap<>();

    public CallGraphAnalyzer(CallGraph rawCallGraph, IClassHierarchy cha,
                             PartialCallGraph partialCallGraph) {
        this.rawCallGraph = rawCallGraph;
        this.cha = cha;
        this.artifactResolver = new ArtifactResolver(cha);
        this.partialCallGraph = partialCallGraph;
    }

    public void resolveCalls() {
        for (CGNode node : this.rawCallGraph) {
            MethodReference nodeReference = node.getMethod().getReference();

            if (applicationClassLoaderFilter.test(node)) {
                continue;
            }

            Method methodNode = findOrCreate(nodeReference);

            for (Iterator<CallSiteReference> callSites = node.iterateCallSites();
                 callSites.hasNext(); ) {
                CallSiteReference callSite = callSites.next();

                MethodReference targetWithCorrectClassLoader = correctClassLoader(callSite
                        .getDeclaredTarget());

                Method targetMethodNode = findOrCreate(targetWithCorrectClassLoader);

                addCall(methodNode, targetMethodNode, getInvocationLabel(callSite));
            }

        }
    }

    private void addCall(Method source, Method target, Call.CallType callType) {
        Call call = new Call(source, target, callType);

        if (source instanceof ResolvedMethod && target instanceof ResolvedMethod) {
            partialCallGraph.addResolvedCall(call);
        } else {
            partialCallGraph.addUnresolvedCall(call);
        }
    }

    private Method findOrCreate(MethodReference reference) {
        String namespace = reference.getDeclaringClass().getName().toString().substring(1)
                .replace('/', '.');
        Selector symbol = reference.getSelector();

        if (inApplicationScope(reference)) {

            JarFile jarfile = artifactResolver.findJarFileUsingMethod(reference);
            ResolvedMethod method = new ResolvedMethod(namespace, symbol, jarfile);
            String key = method.toID();

            ResolvedMethod val = resolvedDictionary.get(key);
            if (val != null) {
                return val;
            }

            resolvedDictionary.put(key, method);
            return method;
        } else {
            UnresolvedMethod method = new UnresolvedMethod(namespace, symbol);
            String key = method.toID();

            UnresolvedMethod val = unresolvedDictionary.get(key);
            if (val != null) {
                return val;
            }

            unresolvedDictionary.put(key, method);
            return method;
        }
    }

    private Predicate<CGNode> applicationClassLoaderFilter = node -> !node.getMethod()
            .getDeclaringClass()
            .getClassLoader()
            .getReference()
            .equals(ClassLoaderReference.Application);

    private boolean inApplicationScope(MethodReference reference) {
        return reference.getDeclaringClass().getClassLoader()
                .equals(ClassLoaderReference.Application);
    }

    private MethodReference correctClassLoader(MethodReference reference) {
        IClass klass = cha.lookupClass(reference.getDeclaringClass());

        if (klass == null) {
            return MethodReference.findOrCreate(ClassLoaderReference.Extension,
                    reference.getDeclaringClass().getName().toString(),
                    reference.getName().toString(),
                    reference.getDescriptor().toString());
        }

        return MethodReference.findOrCreate(klass.getReference(), reference.getSelector());

    }

    private Call.CallType getInvocationLabel(CallSiteReference callSite) {

        switch ((IInvokeInstruction.Dispatch) callSite.getInvocationCode()) {
            case INTERFACE:
                return Call.CallType.INTERFACE;
            case VIRTUAL:
                return Call.CallType.VIRTUAL;
            case SPECIAL:
                return Call.CallType.SPECIAL;
            case STATIC:
                return Call.CallType.STATIC;
            default:
                return Call.CallType.UNKNOWN;
        }
    }
}
