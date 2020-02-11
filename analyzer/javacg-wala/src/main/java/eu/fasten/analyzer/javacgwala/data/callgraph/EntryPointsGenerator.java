package eu.fasten.analyzer.javacgwala.data.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class EntryPointsGenerator {

    private IClassHierarchy cha;

    public EntryPointsGenerator(IClassHierarchy cha) {
        this.cha = cha;
    }

    /**
     * Create entry points for call graph creation
     * (stuff taken from  woutrrr/lapp).
     *
     * @return List of entry points
     */
    public ArrayList<Entrypoint> getEntryPoints() {
        return StreamSupport.stream(cha.spliterator(), false)
                .filter(EntryPointsGenerator::isPublicClass)
                .flatMap(klass -> klass.getAllMethods().parallelStream())
                .filter(EntryPointsGenerator::isPublicMethod)
                .map(m -> new DefaultEntrypoint(m, cha))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Check if given class is public.
     *
     * @param klass Class to check
     * @return true if class is public, false otherwise
     */
    private static boolean isPublicClass(IClass klass) {
        return isApplication(klass)
                && !klass.isInterface()
                && klass.isPublic();
    }

    /**
     * Check if given method is public.
     *
     * @param method Method to check
     * @return true if method is public, false otherwise
     */
    private static boolean isPublicMethod(IMethod method) {
        return isApplication(method.getDeclaringClass())
                && method.isPublic()
                && !method.isAbstract();
    }

    /**
     * Check if given class "belongs" to the application loader.
     *
     * @param klass Class to check
     * @return true if class "belongs", false otherwise
     */
    private static Boolean isApplication(IClass klass) {
        return klass.getClassLoader().getReference().equals(ClassLoaderReference.Application);
    }
}
