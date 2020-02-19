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
import eu.fasten.analyzer.javacgwala.data.core.UnresolvedMethod;
import eu.fasten.core.data.FastenJavaURI;
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

    Map<FastenURI, List<FastenURI>> methods = new HashMap<>();

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

        List<FastenURI> methodURIs = new ArrayList<>();

        FastenURI superClass = getClassURI(klass.getSuperclass());

        String sourceFileName = klass.getSourceFileName();

        List<FastenURI> interfaces = new ArrayList<>();

        for (IClass implementedInterface : klass.getAllImplementedInterfaces()) {
            interfaces.add(getClassURI(implementedInterface));
        }

        for (IMethod declaredMethod : klass.getDeclaredMethods()) {
            String namespace =
                    declaredMethod.getReference().getDeclaringClass().getName().toString().substring(1)
                    .replace('/', '.');
            Selector symbol = declaredMethod.getReference().getSelector();


            List<IMethod> methodInterfaces = interfaceMethods.get(declaredMethod.getSelector());
            var method = new UnresolvedMethod(namespace, symbol);
            methodURIs.add(method.toCanonicalSchemalessURI());
            //processMethod(klass, declaredMethod, methodInterfaces);
        }
        methods.put(getClassURI(klass), methodURIs);

//        System.out.println();
//        System.out.println("--------------- Class: " + getClassURI(klass) + " ---------------");
//        System.out.println("METHODS: " + methodURIs);
//        System.out.println("==================================");
//        System.out.println("INTERFACES: " + interfaces);
//        System.out.println("==================================");
//        System.out.println("SUPER CLASS: " + superClass);
//        System.out.println("==================================");
//        System.out.println("SOURCE FILE: " + sourceFileName);
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

    private FastenURI getClassURI(IClass klass) {
        String namespace = klass.getName().toString().substring(1).replace('/', '.');
        return FastenURI.create("/" + getPackageName(namespace) + "/" + getClassName(namespace));
    }

    /**
     * Get name of the package.
     *
     * @return Package name
     */
    private String getPackageName(String namespace) {
        return namespace.substring(0, namespace.lastIndexOf("."));
    }

    /**
     * Get name of the class.
     *
     * @return Class name
     */
    private String getClassName(String namespace) {
        return namespace.substring(namespace.lastIndexOf(".") + 1);
    }

}
