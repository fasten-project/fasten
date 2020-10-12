package eu.fasten.analyzer.restapiplugin.api;

import eu.fasten.analyzer.restapiplugin.api.mvn.*;

import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

//@ApplicationPath("/") // Set in the plugin class
public class RestApplication extends Application {

    private final Set<Class<?>> resources = new HashSet<>();

    public RestApplication() {
        resources.add(PackageApi.class);
        resources.add(DependencyApi.class);
        resources.add(ModuleApi.class);
        resources.add(BinaryModuleApi.class);
        resources.add(CallableApi.class);
        resources.add(EdgeApi.class);
        resources.add(FileApi.class);
    }

    @Override
    public Set<Class<?>> getClasses() {
        return resources;
    }
}
