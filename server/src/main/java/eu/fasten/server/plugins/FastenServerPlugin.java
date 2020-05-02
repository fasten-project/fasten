package eu.fasten.server.plugins;

public interface FastenServerPlugin extends Runnable {

    /**
     * Starts the fasten plugin in a separate thread.
     */
    void start();

    /**
     * Stops the fasten plugin.
     */
    void stop();

    /**
     * Returns the current thread the fasten plugin is running on.
     *
     * @return current plugin thread
     */
    Thread thread();
}
