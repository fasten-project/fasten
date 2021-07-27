package eu.fasten.analyzer.licensedetector;

import javax.annotation.Nullable;

import eu.fasten.analyzer.licensedetector.exceptions.LicenseDetectorException;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenses;

public interface ILicenseDetector {

	DetectedLicenses detect(String repoPath, @Nullable String repoUrl) throws LicenseDetectorException;

}
