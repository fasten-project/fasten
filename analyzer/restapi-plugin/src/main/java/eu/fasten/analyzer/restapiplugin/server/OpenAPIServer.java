package eu.fasten.analyzer.restapiplugin.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
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
                Router router = routerFactory.getRouter();

                // Handle errors
                router.errorHandler(404, routingContext -> {
                    JsonObject errorObject = new JsonObject()
                            .put("code", 404)
                            .put("message",
                                    (routingContext.failure() != null) ?
                                            routingContext.failure().getMessage() :
                                            "Not Found"
                            );
                    routingContext
                            .response()
                            .setStatusCode(404)
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(errorObject.encode());
                });
                router.errorHandler(400, routingContext -> {
                    JsonObject errorObject = new JsonObject()
                            .put("code", 400)
                            .put("message",
                                    (routingContext.failure() != null) ?
                                            routingContext.failure().getMessage() :
                                            "Validation Exception"
                            );
                    routingContext
                            .response()
                            .setStatusCode(400)
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(errorObject.encode());
                });

                // Set http server
                server = vertx.createHttpServer(new HttpServerOptions().setPort(8080).setHost("localhost"));
                server.requestHandler(router).listen();

                // Complete the verticle start
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