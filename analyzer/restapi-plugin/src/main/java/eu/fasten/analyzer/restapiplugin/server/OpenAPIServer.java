package eu.fasten.analyzer.restapiplugin.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.contract.RouterFactoryOptions;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OpenAPIServer extends AbstractVerticle {

    HttpServer server;
    Logger logger = LoggerFactory.getLogger("OpenAPI3RouterFactory");

    @Override
    public void start(Future<Void> future) {
        OpenAPI3RouterFactory.create(this.vertx, "openapispec.json", asyncResult -> {
            if (asyncResult.succeeded()) {
                OpenAPI3RouterFactory routerFactory = asyncResult.result();

                // Enable automatic response when ValidationException is thrown
                routerFactory.setOptions(new RouterFactoryOptions().setMountResponseContentTypeHandler(true));

                // Add routes handlers

                // "/"
                routerFactory.addHandlerByOperationId("root_api__get",
                        routingContext -> routingContext.response().setStatusCode(200)
                                .putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end("Welcome to FASTEN api")
                // TODO: Do we need a html page for this endpoint? Or just some answer!?
                );

                // "/api/{pkg_manager}/{product}/{version}"
                // "api/pypi/numpy/1.12.3"
                routerFactory.addHandlerByOperationId("get_metadata_api__pkg_manager___product___version__get",
                        routingContext -> {
                            RequestParameters params = routingContext.get("parsedParameters");
                            String pkg_manager = params.pathParameter("pkg_manager").getString();
                            String product = params.pathParameter("product").getString();
                            String version = params.pathParameter("version").getString();
                            System.out.println("DEBUG: Parsed parameters converted to string (" + pkg_manager + ", "
                                    + product + ", " + version + ")."); // update this to logger.debug
                            // TODO: call a function to do the DB query
                            JsonObject reply = dummyFunction(pkg_manager, product, version);
                            if (!reply.isEmpty())
                                routingContext.response().setStatusCode(200)
                                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(reply.encode());
                            else
                                routingContext.fail(404, new Exception("Query not found!"));
                            // TODO: nice to have more specifc exceptions ex: pkg not found, product not
                            // found etc.
                        });

                // Generate the router
                Router router = routerFactory.getRouter();

                // Handle errors
                router.errorHandler(404, routingContext -> {
                    JsonObject errorObject = new JsonObject()
                            .put("code", 404)
                            .put("message",
                                    (routingContext.failure() != null) ?
                                            routingContext.failure().getMessage() :
                                            "Not Found");
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
                                            routingContext.failure().getMessage():
                                            "Validation Exception");
                    routingContext
                            .response()
                            .setStatusCode(400)
                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                            .end(errorObject.encode());
                });

                // Set http server
                server = vertx.createHttpServer(new HttpServerOptions()
                        .setPort(8080)
                        .setHost("localhost"));
                server.requestHandler(router).listen(serverAsyncResult -> {
                    if (serverAsyncResult.succeeded()) {
                        logger.info("Server started on port "
                                + serverAsyncResult.result().actualPort());
                    } else {
                        logger.info(serverAsyncResult.cause());
                    }
                });
                // Complete the verticle start
                future.complete();
                logger.info("Verticle start completed");
            } else {
                // Something went wrong during router factory initialization
                future.fail(asyncResult.cause());
                logger.info("Verticle failed");
            }
        });
    }

    private JsonObject dummyFunction(String pkg_manager, String product, String version) {
        String msg = "Package manager: "+pkg_manager+", Product: "+product+", Version: "+version+".";

        JsonObject result = new JsonObject().put("message", msg);

        logger.info(result);

        return result;
    }

    @Override
    public void stop(Future future) {
        logger.info("\n Closing server");
        this.server.close();
    }

}