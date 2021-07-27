package eu.fasten.analyzer.licensedetector;

import org.apache.commons.lang3.NotImplementedException;

import eu.fasten.analyzer.licensedetector.exceptions.LicenseDetectorException;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenses;

//TODO: to be implemented
public class JavaMavenLicenseDetector extends AbstractLicenseDetector {
	
	@Override
	public DetectedLicenses detect(String repoPath, String repoUrl) throws LicenseDetectorException {
		throw new LicenseDetectorException(new NotImplementedException());
	}

}
