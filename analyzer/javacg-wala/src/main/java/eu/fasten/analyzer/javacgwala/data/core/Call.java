package eu.fasten.analyzer.javacgwala.data.core;

import eu.fasten.core.data.FastenURI;
import java.util.Objects;

public class Call {

    public enum CallType {
        UNKNOWN("unknown"),
        INTERFACE("invoke_interface"),
        VIRTUAL("invoke_virtual"),
        SPECIAL("invoke_special"),
        STATIC("invoke_static");

        public final String label;

        CallType(String label) {
            this.label = label;
        }

    }

    private Method source;
    private Method target;
    private final CallType callType;

    public Call(Method source, Method target, CallType callType) {
        this.source = source;
        this.target = target;
        this.callType = callType;
    }

    public Method getSource() {
        return source;
    }

    public Method getTarget() {
        return target;
    }

    public CallType getCallType() {
        return callType;
    }

    /**
     * Convert a call to FastenURI array in which 0th element represents caller URI
     * and 1st represents callee URI.
     *
     * @return FastenURI array
     */
    public FastenURI[] toURICall() {

        FastenURI[] fastenURI = new FastenURI[2];

        var sourceURI = source.toCanonicalSchemalessURI();
        var targetURI = target.toCanonicalSchemalessURI();

        fastenURI[0] = sourceURI;
        fastenURI[1] = FastenURI.create("//" + targetURI.toString());

        return fastenURI;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Call call = (Call) o;
        return Objects.equals(source, call.source) &&
                Objects.equals(target, call.target) &&
                callType == call.callType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, callType);
    }
}
