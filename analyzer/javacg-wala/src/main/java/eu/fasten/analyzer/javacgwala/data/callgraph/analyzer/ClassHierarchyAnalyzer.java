package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Selector;
import eu.fasten.analyzer.javacgwala.data.callgraph.ExtendedRevisionCallGraph;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraph;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.analyzer.javacgwala.data.core.ResolvedMethod;
import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ClassHierarchyAnalyzer {

    enum MethodType {
        INTERFACE, ABSTRACT, IMPLEMENTATION
    }

    private final IClassHierarchy cha;

    private final AnalysisContext analysisContext;

    private final PartialCallGraph partialCallGraph;

    Map<IClass, List<IMethod>> methods = new HashMap<>();

    public ClassHierarchyAnalyzer(IClassHierarchy cha, PartialCallGraph partialCallGraph) {
        this.cha = cha;
        this.partialCallGraph = partialCallGraph;
        this.analysisContext = new AnalysisContext(cha);
    }

    public void resolveCHA() {
        IClassLoader classLoader = cha.getLoader(ClassLoaderReference.Application);

        // Iterate all classes in Application scope
        for (var it = classLoader.iterateAllClasses(); it.hasNext(); ) {
            IClass klass = it.next();
            processClass(klass);
        }
    }

    private void processClass(IClass klass) {
        Map<Selector, List<IMethod>> interfaceMethods = klass.getDirectInterfaces()
                .stream()
                .flatMap(o -> o.getDeclaredMethods().stream())
                .collect(
                        Collectors.groupingBy(IMethod::getSelector)
                );
        List<IMethod> methodList = new ArrayList<>();

        for (IMethod declaredMethod : klass.getDeclaredMethods()) {

            List<IMethod> methodInterfaces = interfaceMethods.get(declaredMethod.getSelector());
            methodList.add(declaredMethod);
            processMethod(klass, declaredMethod, methodInterfaces);
        }
        methods.put(klass, methodList);
    }

    private void processMethod(IClass klass, IMethod declaredMethod, List<IMethod> methodInterfaces) {
        if (declaredMethod.isPrivate()) {
            // Private methods cannot be overridden, so no need for them.
            return;
        }
        IClass superKlass = klass.getSuperclass();

        Method declaredMethodNode = analysisContext.findOrCreate(declaredMethod.getReference());

        if (!(declaredMethodNode instanceof ResolvedMethod)) {
            return;
        }
        ResolvedMethod resolvedMethod = (ResolvedMethod) declaredMethodNode;


        IMethod superMethod = superKlass.getMethod(declaredMethod.getSelector());
        if (superMethod != null) {
            Method superMethodNode = analysisContext.findOrCreate(superMethod.getReference());

            //TODO: CHA EDGE WAS ADDED HERE
            //graph.addChaEdge(superMethodNode, resolvedMethod, ChaEdge.ChaEdgeType.OVERRIDE);
        }


        if (methodInterfaces != null) {
            for (IMethod interfaceMethod : methodInterfaces) {
                Method interfaceMethodNode =
                        analysisContext.findOrCreate(interfaceMethod.getReference());

                //TODO: CHA EDGE WAS ADDED HERE
                //graph.addChaEdge(interfaceMethodNode, resolvedMethod,ChaEdge.ChaEdgeType.IMPLEMENTS);
            }
        }


        // An abstract class doesn't have to define abstract method for interface methods
        // So if this method doesn't have a super method or an interface method look for them in the interfaces of the abstract superclass
        if (superKlass.isAbstract() && superMethod == null && methodInterfaces == null) {

            Map<Selector, List<IMethod>> abstractSuperClassInterfacesByMethod = superKlass.getDirectInterfaces()
                    .stream()
                    .flatMap(o -> o.getDeclaredMethods().stream())
                    .collect(Collectors.groupingBy(IMethod::getSelector));

            List<IMethod> abstractSuperClassInterfaceMethods = abstractSuperClassInterfacesByMethod.get(declaredMethod.getSelector());
            if (abstractSuperClassInterfaceMethods != null && abstractSuperClassInterfaceMethods.size() > 0) {
                for (IMethod abstractSuperClassInterfaceMethod : abstractSuperClassInterfaceMethods) {
                    Method abstractSuperClassInterfaceMethodNode = analysisContext
                            .findOrCreate(abstractSuperClassInterfaceMethod.getReference());

                    //TODO: CHA EDGE WAS ADDED HERE
                    //graph.addChaEdge(abstractSuperClassInterfaceMethodNode, resolvedMethod,ChaEdge.ChaEdgeType.IMPLEMENTS);
                }
            }
        }
    }

    private MethodType getMethodType(IClass klass, IMethod declaredMethod) {
        if (declaredMethod.isAbstract()) {

            if (klass.isInterface()) {
                return MethodType.INTERFACE;
            } else {
                return MethodType.ABSTRACT;
            }

        } else {
            return MethodType.IMPLEMENTATION;
        }
    }
}
