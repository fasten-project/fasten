package eu.fasten.analyzer.dummyplugin;

public class Main implements Runnable {
    @Override
    public void run() {
        DummyPlugin.DummyPluginExtension dummyPlugin = new DummyPlugin.DummyPluginExtension();
    }
}
