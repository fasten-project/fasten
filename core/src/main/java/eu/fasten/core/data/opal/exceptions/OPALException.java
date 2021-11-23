package eu.fasten.core.data.opal.exceptions;

/**
 * Exception wraps exceptions thrown by OPAL library.
 */
public class OPALException extends RuntimeException {

	private static final long serialVersionUID = 3553121464323074257L;

	public OPALException(String message) {
		super(message);
	}

	public OPALException(Throwable t) {
		super(t);
	}

	public OPALException(String message, Throwable t) {
		super(message, t);
	}
}
