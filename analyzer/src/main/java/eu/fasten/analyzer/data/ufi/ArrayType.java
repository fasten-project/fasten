package eu.fasten.analyzer.data.ufi;

import eu.fasten.analyzer.data.type.Namespace;

import java.util.Optional;


public final class ArrayType extends UniversalType  {

    public final int brackets;

    public ArrayType(Optional<Namespace> outer, Namespace inner, int brackets) {
        super(outer, inner);
        this.brackets = brackets;
    }
}
