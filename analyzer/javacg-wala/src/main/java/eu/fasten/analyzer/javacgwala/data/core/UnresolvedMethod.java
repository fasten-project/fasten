package eu.fasten.analyzer.javacgwala.data.core;

import com.ibm.wala.types.Selector;

public class UnresolvedMethod extends Method {


    /**
     * Construct a method given its reference.
     *
     * @param namespace
     * @param symbol
     */
    public UnresolvedMethod(String namespace, Selector symbol) {
        super(namespace, symbol);
    }

    @Override
    public String toID() {
        return "__::" + namespace + "." + symbol.toString();
    }
}
