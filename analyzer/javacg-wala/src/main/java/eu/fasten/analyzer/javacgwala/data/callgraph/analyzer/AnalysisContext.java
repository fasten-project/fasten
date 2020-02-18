package eu.fasten.analyzer.javacgwala.data.callgraph.analyzer;

import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import eu.fasten.analyzer.javacgwala.data.ArtifactResolver;
import eu.fasten.analyzer.javacgwala.data.core.Method;
import eu.fasten.analyzer.javacgwala.data.core.ResolvedMethod;
import eu.fasten.analyzer.javacgwala.data.core.UnresolvedMethod;
import java.util.HashMap;
import java.util.jar.JarFile;

public class AnalysisContext {

    private final ArtifactResolver artifactResolver;

    private final HashMap<String, ResolvedMethod> resolvedDictionary;
    private final HashMap<String, UnresolvedMethod> unresolvedDictionary;

    public AnalysisContext(IClassHierarchy cha) {
        this.resolvedDictionary = new HashMap<>();
        this.unresolvedDictionary = new HashMap<>();
        this.artifactResolver = new ArtifactResolver(cha);
    }

    /**
     * Check if given method was already added to the list of calls. If call was already added,
     * return this call.
     *
     * @param reference Method reference
     * @return Duplicate or newly created method
     */
    public Method findOrCreate(MethodReference reference) {
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

    /**
     * Check if given method "belongs" to application call.
     *
     * @param reference Method reference
     * @return true if method "belongs" to application scope, false otherwise
     */
    private boolean inApplicationScope(MethodReference reference) {
        return reference.getDeclaringClass().getClassLoader()
                .equals(ClassLoaderReference.Application);
    }
}
