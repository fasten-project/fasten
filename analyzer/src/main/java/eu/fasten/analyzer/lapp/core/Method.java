package eu.fasten.analyzer.lapp.core;

import com.ibm.wala.types.Selector;
import java.util.HashMap;
import java.util.Map;

public abstract class Method {

    public final String namespace;
    public final Selector symbol;
    public final Map<String, String> metadata;


    protected Method(String namespace, Selector symbol) {
        this.namespace = namespace;
        this.symbol = symbol;

        this.metadata = new HashMap<>();
    }

    public abstract String toID();
}
