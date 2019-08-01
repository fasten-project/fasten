package eu.fasten.analyzer.data.ufi;


import eu.fasten.analyzer.data.type.Namespace;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

public class UniversalType implements Serializable {
    public final Optional<Namespace> outer;
    public final Namespace inner;

    public UniversalType(Optional<Namespace> outer, Namespace inner) {
        this.outer = outer;
        this.inner = inner;
    }

    @Override
    public boolean equals(Object o) {
        // self check
        if (this == o)
            return true;
        // null check
        if (o == null)
            return false;
        // type check and cast
        if (getClass() != o.getClass())
            return false;
        UniversalType ty = (UniversalType) o;
        return Objects.equals(this.outer, ty.outer) &&
                Objects.equals(this.inner, ty.inner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.outer, this.inner);
    }
}
