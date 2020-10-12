package eu.fasten.analyzer.restapiplugin.api;

import eu.fasten.analyzer.restapiplugin.api.mvn.PackageApi;

import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

//@ApplicationPath("/") // Set in the plugin class
public class RestApplication extends Application {

    private final Set<Class<?>> resources = new HashSet<>();

    public RestApplication() {
        resources.add(PackageApi.class);
    }

    @Override
    public Set<Class<?>> getClasses() {
        return resources;
    }
}
