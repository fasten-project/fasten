package eu.fasten.analyzer.lapp.call;

import java.util.Objects;

import eu.fasten.analyzer.lapp.core.Method;

public abstract class Edge {

    public final Method source;
    public final Method target;

    protected Edge(Method source, Method callee) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(callee);

        this.source = source;
        this.target = callee;
    }

    public abstract String getLabel();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge edge = (Edge) o;
        return source.equals(edge.source) &&
                target.equals(edge.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target);
    }
}
