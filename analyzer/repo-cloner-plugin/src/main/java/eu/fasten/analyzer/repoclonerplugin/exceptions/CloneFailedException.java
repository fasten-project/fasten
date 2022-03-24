package eu.fasten.analyzer.repoclonerplugin.exceptions;

public class CloneFailedException extends Exception {
    public CloneFailedException(String errorMsg) {
        super(errorMsg);
    }
}
