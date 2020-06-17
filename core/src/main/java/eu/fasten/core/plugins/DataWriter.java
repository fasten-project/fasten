package eu.fasten.core.plugins;

/**
 * A plug-in that needs to write to disk some data should implement this interface.
 */
public interface DataWriter extends FastenPlugin {

    /**
     * Sets base directory into which the data will be written.
     *
     * @param baseDir Path to base directory
     */
    void setBaseDir(String baseDir);
}
