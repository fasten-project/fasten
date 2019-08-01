package eu.fasten.analyzer.lapp.callgraph.wala;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.IInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import eu.fasten.analyzer.lapp.call.Call;
import eu.fasten.analyzer.lapp.core.Method;

import java.util.Iterator;
import java.util.function.Predicate;

public class CallGraphInserter {


    private final CallGraph cg;
    private final IClassHierarchy cha;
    private final LappPackageBuilder lappPackageBuilder;

    public CallGraphInserter(CallGraph cg, IClassHierarchy cha, LappPackageBuilder lappPackageBuilder) {
        this.cg = cg;
        this.cha = cha;
        this.lappPackageBuilder = lappPackageBuilder;
    }


    public void insertCallGraph() {
        for (CGNode node : this.cg) {
            MethodReference nodeReference = node.getMethod().getReference();

            if (applicationClassLoaderFilter.test(node)) {
                // Ignore everything not in the application classloader
                continue;
            }
            Method methodNode = lappPackageBuilder.addMethod(nodeReference, LappPackageBuilder.MethodType.IMPLEMENTATION);


            for (Iterator<CallSiteReference> callSites = node.iterateCallSites(); callSites.hasNext(); ) {
                CallSiteReference callSite = callSites.next();

                /* If the target is unknown, is gets the Application loader by default. We would like this to be the
                   Extension loader, that way it is easy to filter them out later.
                   */
                MethodReference targetWithCorrectClassLoader = correctClassLoader(callSite.getDeclaredTarget());

                Method targetMethodNode = lappPackageBuilder.addMethod(targetWithCorrectClassLoader);
                lappPackageBuilder.addCall(methodNode, targetMethodNode, getInvocationLabel(callSite));
            }

        }
    }

    private Predicate<CGNode> applicationClassLoaderFilter = node -> {
        return !node.getMethod()
                .getDeclaringClass()
                .getClassLoader()
                .getReference()
                .equals(ClassLoaderReference.Application);
    };

    private Call.CallType getInvocationLabel(CallSiteReference callsite) {

        switch ((IInvokeInstruction.Dispatch) callsite.getInvocationCode()) {
            case INTERFACE:
                return Call.CallType.INTERFACE;
            case VIRTUAL:
                return Call.CallType.VIRTUAL;
            case SPECIAL:
                return Call.CallType.SPECIAL;
            case STATIC:
                return Call.CallType.STATIC;
        }

        return Call.CallType.UNKNOWN;
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
}
