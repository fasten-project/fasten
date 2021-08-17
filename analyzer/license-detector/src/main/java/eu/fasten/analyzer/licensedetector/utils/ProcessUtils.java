package eu.fasten.analyzer.licensedetector.utils;

import java.io.IOException;
import java.util.List;

public class ProcessUtils {

	public static int exec(List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = null;
        int exitCode = Integer.MIN_VALUE;
        try {
            p = pb.start(); // start executing command
            exitCode = p.waitFor();// synchronous call
        } catch (IOException e) {
            throw new IOException("Couldn't start the external process: " + e.getMessage(), e.getCause());
        } catch (InterruptedException e) {
            if (p != null) {
                p.destroy();
            }
            throw new InterruptedException("Couldn't wait for external process to complete: " + e.getMessage());
        }
        return exitCode;
	}
}
