package eu.fasten.core.data.opal.exceptions;

/**
 * Exception that is thrown whenever any type of an exception occurs
 * inside OPAL library code.
 */
public class OPALException extends Exception {
    /**
     * Creates an OPALException with a message.
     *
     * @param message error message
     */
    public OPALException(String message) {
        super(message);
    }
}
