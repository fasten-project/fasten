package eu.fasten.analyzer.licensedetector.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Scancode {

    private final static Logger logger = LoggerFactory.getLogger(Scancode.class.getName());
    private static final String SCANCODE_CMD = StringUtils.firstNonBlank(System.getenv("SCANCODE_CMD"), "scancode");

    /**
     * Scans a repository looking for license text in files with scancode.
     *
     * @param repoPath the repository path whose pom.xml file must be retrieved.
     * @return the path of the file containing the result.
     * @throws IOException          in case scancode couldn't start.
     * @throws InterruptedException in case this function couldn't wait for scancode to complete.
     * @throws RuntimeException     in case scancode returns with an error code != 0.
     */
    public static String scanProject(String repoPath) throws IOException, InterruptedException, RuntimeException {

        // Where is the result stored
        String resultPath = repoPath + "/scancode.json";

        // `scancode` command to be executed
        List<String> cmd = Arrays.asList(
                "/bin/bash",
                "-c",
                SCANCODE_CMD + " " +
                // Scan for licenses
                "--license " +
                // Report full, absolute paths
                "--full-root " +
                // Scan using n parallel processes
                "--processes " + "$(nproc) " +
                // Write scan output as a compact JSON file
                "--json " + resultPath + " " +
                // SPDX RDF file
                // "--spdx-rdf " + repoPath + "/scancode.spdx.rdf" + " " +
                // SPDX tag/value file
                // "--spdx-tv " + repoPath + "/scancode.spdx.tv " + " " +
                /*  Only return files or directories with findings for the requested scans.
                    Files and directories without findings are omitted
                    (file information is not treated as findings). */
                "--only-findings " +
                // TODO Scancode timeout?
                // "--timeout " + "600.0 " +
                // Repository directory
                repoPath
        );

        // Start scanning
		logger.info("Scanning project in " + repoPath + "...");
		int exitCode = ProcessUtils.exec(cmd);
		if (exitCode != 0) {
		    throw new RuntimeException("Scancode returned with exit code " + exitCode + ".");
		}
		logger.info("...project in " + repoPath + " scanned successfully.");
		
		return resultPath;
    }

    /**
     * Parses the scan result file and returns file licenses.
     *
     * @param scanResultPath the path of the file containing the scan results.
     * @return the list of licenses that have been detected by scanning files.
     * @throws IOException   in case the JSON scan result couldn't be read.
     * @throws JSONException in case the root object of the JSON scan result couldn't have been retrieved.
     */
    public static JSONArray parseScanResult(String scanResultPath) throws IOException, JSONException {

        try {
            // Retrieving the root element of the scan result file
            JSONObject root = new JSONObject(Files.readString(Paths.get(scanResultPath)));
            if (root.isEmpty()) {
                throw new JSONException("Couldn't retrieve the root object of the JSON scan result file " +
                        "at " + scanResultPath + ".");
            }

            // Returning file licenses
            if (root.has("files") && !root.isNull("files")) {
                return root.getJSONArray("files");
            }
        } catch (IOException e) {
            throw new IOException("Couldn't read the JSON scan result file at " + scanResultPath +
                    ": " + e.getMessage(), e.getCause());
        }

        // In case nothing could have been found
        return null;
    }    
}
