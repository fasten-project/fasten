package eu.fasten.core.plugins;

/**
 * Base interface for all FASTEN plugins. Used mostly for discovery and loading.
 */
public interface FastenPlugin {

    /**
     * Returns
     *
     * @return The plugin's fully qualified name
     */
    public String name();

    /**
     * Returns a longer description of the plug-in functionality
     *
     * @return A string describing what the plug-in does.
     */
    public String description();
}
