package eu.fasten.analyzer.licensedetector;

public class LicenseDetectorFactory {

	public static ILicenseDetector create (LanguageType language) {
    	switch (language) {
		case JAVA:
			return new JavaMavenLicenseDetector();
		case PYTHON:
			return new PythonLicenseDetector();
		case C:
			return new CLicenseDetector();
		default:
			throw new NullPointerException("LanguageType cannot be null");
		}
	}
	
}
