package eu.fasten.analyzer.lapp.core;

import com.ibm.wala.types.Selector;

public class UnresolvedMethod extends Method {

    public static final AnalysisContext DEFAULT_CONTEXT = new DefaultAnalysisContext();

    UnresolvedMethod(String namespace, Selector symbol) {
        super(namespace, symbol);
    }

    public String toID() {
        return toID(namespace, symbol);
    }

    public static String toID(String namespace, Selector symbol) {
        return "__::" + namespace + "." + symbol.toString();
    }

    public static synchronized UnresolvedMethod findOrCreate(String namespace, Selector symbol) {
        return DEFAULT_CONTEXT.makeUnresolved(namespace, symbol);
    }
}
