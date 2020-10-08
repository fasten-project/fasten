package eu.fasten.analyzer.restapiplugin.api;

import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

//@ApplicationPath("/") // Set in the plugin class
public class RestApplication extends Application {

    public RestApplication() {
    }

    @Override
    public Set<Object> getSingletons() {
        HashSet<Object> set = new HashSet<Object>();
        return set;
    }
}
