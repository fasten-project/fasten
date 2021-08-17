package eu.fasten.analyzer.licensedetector.exceptions;

public class LicenseDetectorException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LicenseDetectorException() {
		super();
	}

	public LicenseDetectorException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public LicenseDetectorException(String message, Throwable cause) {
		super(message, cause);
	}

	public LicenseDetectorException(String message) {
		super(message);
	}

	public LicenseDetectorException(Throwable cause) {
		super(cause);
	}

}
