package eu.fasten.analyzer.restapiplugin.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

public class OpenAPIServer extends AbstractVerticle {

    HttpServer server;
    Logger logger = LoggerFactory.getLogger("OpenAPI3RouterFactory");

    @Override
    public void start(Future<Void> future) {
        OpenAPI3RouterFactory.create(this.vertx, "openapispec.json", ar -> {
            if (ar.succeeded()) {
                OpenAPI3RouterFactory routerFactory = ar.result();

                // Add routes handlers

                // Generate the router

                // Handle errors

                future.complete();
            } else {
                future.fail(ar.cause());
            }
        });
    }

    @Override
    public void stop() {
        this.server.close();
    }
}