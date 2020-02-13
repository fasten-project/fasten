package eu.fasten.analyzer.javacgwala.data.core;

import com.ibm.wala.types.Selector;
import java.util.jar.JarFile;

public class ResolvedMethod extends Method {

    private final JarFile artifact;

    /**
     * Construct a method given its reference.
     *
     * @param namespace Namespace
     * @param symbol    Selector
     */
    public ResolvedMethod(String namespace, Selector symbol, JarFile artifact) {
        super(namespace, symbol);
        this.artifact = artifact;
    }

    @Override
    public String toID() {
        return artifact == null ? "Unknown"
                : artifact.getName() + "::" + namespace + "." + symbol.toString();
    }
}
