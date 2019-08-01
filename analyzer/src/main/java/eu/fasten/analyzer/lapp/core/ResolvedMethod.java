package eu.fasten.analyzer.lapp.core;

import com.ibm.wala.types.Selector;

import java.util.jar.JarFile;

public class ResolvedMethod extends Method {

    public static final AnalysisContext DEFAULT_CONTEXT = new DefaultAnalysisContext();

    public final JarFile artifact;

    ResolvedMethod(String namespace, Selector symbol, JarFile artifact) {
        super(namespace, symbol);

        this.artifact = artifact;
    }

    public String toID() {
        return toID(namespace, symbol, artifact);
    }

    public static String toID(String namespace, Selector symbol, JarFile artifact) {
        return artifact == null ? "Unknown" : artifact.getName() + "::" + namespace + "." + symbol.toString();
    }

    public static ResolvedMethod findOrCreate(String namespace, Selector symbol, JarFile artifact) {
        return DEFAULT_CONTEXT.makeResolved(namespace, symbol, artifact);
    }
}
