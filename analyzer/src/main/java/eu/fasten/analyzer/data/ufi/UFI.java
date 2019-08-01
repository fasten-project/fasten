package eu.fasten.analyzer.data.ufi;

import eu.fasten.analyzer.data.type.JDKPackage;
import eu.fasten.analyzer.data.type.MavenCoordinate;
import eu.fasten.analyzer.data.type.Namespace;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class UFI implements Serializable {
    public final static String DELIM = "---";
    public final UniversalType pathType;
    public final String methodName;
    public final Optional<List<UniversalType>> parameters;
    public final UniversalType returnType;

    public UFI(UniversalType pathType,
               String methodName,
               Optional<List<UniversalType>> parameters,
               UniversalType returnType) {
        this.pathType = pathType;
        this.methodName = methodName;
        this.parameters = parameters;
        this.returnType = returnType;
    }

    public static String stringBuilder(UniversalType uty) {

        String buildup = "";

        if (uty.outer.isPresent()) {

            Namespace global = uty.outer.get();

            if (global instanceof MavenCoordinate) {
                buildup += "mvn";


            } else if (global instanceof JDKPackage) {
                buildup += "jdk";
            }

            buildup += DELIM;
            buildup += String.join(global.getNamespaceDelim(), global.getSegments());
            buildup += DELIM;
            buildup += String.join(uty.inner.getNamespaceDelim(), uty.inner.getSegments());


        } else {
            buildup += String.join(uty.inner.getNamespaceDelim(), uty.inner.getSegments());
        }


        if (uty instanceof ArrayType) {
            ArrayType aty = (ArrayType) uty;
            String brackets = IntStream
                    .rangeClosed(1, aty.brackets)
                    .mapToObj(i -> "[]").collect(Collectors.joining(""));

            buildup += brackets;
        }
        return buildup;
    }

    @Override
    public String toString() {

        String args = parameters.isPresent() ?
                parameters.get()
                        .stream()
                        .map(UFI::stringBuilder)
                        .collect(Collectors.joining(",")) : "";


        return stringBuilder(this.pathType) + DELIM
                + this.methodName
                + "<" + args + ">"
                + stringBuilder(this.returnType);
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
        UFI ufi = (UFI) o;
        return Objects.equals(this.toString(), ufi.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.toString());
    }
}
