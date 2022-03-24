package eu.fasten.analyzer.repoclonerplugin.exceptions;

public class RepoUrlNotFoundException extends Exception {
    public RepoUrlNotFoundException(String errMsg) {
        super(errMsg);
    }
}
