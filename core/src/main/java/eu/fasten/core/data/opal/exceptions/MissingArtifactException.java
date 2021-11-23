package eu.fasten.core.data.opal.exceptions;

/**
 * Exception used to indicate when an artifact cannot be found.
 */
public class MissingArtifactException extends RuntimeException {

	private static final long serialVersionUID = -4154873419230733231L;

	public MissingArtifactException(String message) {
		super(message);
	}

	public MissingArtifactException(Throwable t) {
		super(t);
	}

	public MissingArtifactException(String message, Throwable t) {
		super(message, t);
	}
}
