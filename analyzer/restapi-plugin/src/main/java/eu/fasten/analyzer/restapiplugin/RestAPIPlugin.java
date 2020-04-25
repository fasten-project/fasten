package eu.fasten.analyzer.restapiplugin;

import eu.fasten.analyzer.restapiplugin.server.OpenAPIServer;
import eu.fasten.core.plugins.DBConnector;
import io.vertx.core.Vertx;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestAPIPlugin extends Plugin {

    public RestAPIPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class RestAPIExtension implements DBConnector {
        private static DSLContext dslContext;
        private String pluginError = "";
        private final Logger logger = LoggerFactory.getLogger(RestAPIExtension.class.getName());

        @Override
        public void setDBConnection(DSLContext dslContext) {
            RestAPIExtension.dslContext = dslContext;
        }

        @Override
        public String name() {
            return "Rest API Plugin";
        }

        @Override
        public String description() {
            // TODO: Update the description
            return "Rest API Plugin. ";
        }

        @Override
        public void start() {
            // TODO: check if this is the right place to deploy the verticle
            Vertx vertx = Vertx.vertx();
            vertx.deployVerticle(new OpenAPIServer());
            logger.info("Deployed Verticle: " + vertx);
        }

        @Override
        public void stop() {

        }

        @Override
        public void setPluginError(Throwable throwable) {
            this.pluginError =
                    new JSONObject().put("plugin", this.getClass().getSimpleName()).put("msg",
                            throwable.getMessage()).put("trace", throwable.getStackTrace())
                            .put("type", throwable.getClass().getSimpleName()).toString();
        }

        @Override
        public String getPluginError() {
            return this.pluginError;
        }

        @Override
        public void freeResource() {

        }


    }

}