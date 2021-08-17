package eu.fasten.analyzer.licensedetector;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;

import eu.fasten.analyzer.licensedetector.exceptions.LicenseDetectorException;
import eu.fasten.analyzer.licensedetector.license.DetectedLicense;
import eu.fasten.analyzer.licensedetector.license.DetectedLicenses;

public class PythonLicenseDetector extends AbstractLicenseDetector {

	private static final List<String> LICENSE_FILES = Arrays.asList("setup.py", "LICENSE", "LICENSE.md", "LICENSE.txt",
			"Readme.md", "Readme.txt");
	
	@Override
	public DetectedLicenses detect(String repoPath, String repoUrl) throws LicenseDetectorException {
		DetectedLicenses result = new DetectedLicenses();
		
		JSONArray files = detectFileLicenses(repoPath);
		result.setFiles(files);
		
		Set<DetectedLicense> outbound = detectOutboundLicenses(repoPath, LICENSE_FILES, files);

		// TODO: check if is necessary for Python projects
		if ((outbound == null || outbound.isEmpty()) && StringUtils.isNotBlank(repoUrl)) {
			outbound = detectOutboundLicensesFromGitHub(repoUrl);
		}
		
		result.setOutbound(outbound);
		
		return result;
	}

}
