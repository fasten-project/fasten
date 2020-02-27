package eu.fasten.analyzer.javacgwala.data.core;

public enum CallType {
    UNKNOWN("unknown"),
    INTERFACE("invokeinterface"),
    VIRTUAL("invokevirtual"),
    SPECIAL("invokespecial"),
    STATIC("invokestatic");

    public final String label;

    CallType(String label) {
        this.label = label;
    }
}
