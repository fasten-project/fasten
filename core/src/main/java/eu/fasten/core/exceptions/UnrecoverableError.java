package eu.fasten.core.exceptions;

/**
 * This exception is defined for rare circumstances where we need to deliberately crash a plug-in and
 * cause Kubernetes to restart the pod. Otherwise, RuntimeExceptions are normally catched and handled by the FASTEN server.
 */
public class UnrecoverableError extends Error {
    public UnrecoverableError(String errorMsg, Throwable err) {
        super(errorMsg, err);
    }
}
