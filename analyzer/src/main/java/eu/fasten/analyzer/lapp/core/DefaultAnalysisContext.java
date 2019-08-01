package eu.fasten.analyzer.lapp.core;

import com.ibm.wala.types.Selector;

import java.util.HashMap;
import java.util.Objects;
import java.util.jar.JarFile;

public class DefaultAnalysisContext implements AnalysisContext {

    private final HashMap<String, ResolvedMethod> resolvedDictionary = new HashMap<>();
    private final HashMap<String, UnresolvedMethod> unresolvedDictionary = new HashMap<>();

    
    @Override
    public synchronized ResolvedMethod makeResolved(String namespace, Selector symbol, JarFile artifact) {
        //Objects.requireNonNull(artifact);
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(symbol);

        String key = ResolvedMethod.toID(namespace, symbol, artifact);

        ResolvedMethod val = resolvedDictionary.get(key);
        if (val != null) {
            return val;
        }

        val = new ResolvedMethod(namespace, symbol, artifact);
        resolvedDictionary.put(key, val);
        return val;

    }

    @Override
    public synchronized UnresolvedMethod makeUnresolved(String namespace, Selector symbol) {
        Objects.requireNonNull(namespace);
        Objects.requireNonNull(symbol);

        String key = UnresolvedMethod.toID(namespace, symbol);

        UnresolvedMethod val = unresolvedDictionary.get(key);
        if (val != null) {
            return val;
        }

        val = new UnresolvedMethod(namespace, symbol);
        unresolvedDictionary.put(key, val);
        return val;

    }

}
