package eu.fasten.core.data.opal.exceptions;

/**
 * This exception is thrown whenever
 */
public class MissingArtifactException extends Exception {

    /**
     * Creates a MissingArtifactException.
     * @param message the exception message.
     * @param cause the exception cause.
     */
    public MissingArtifactException(String message, Throwable cause) {
        super(message, cause);
    }
}
