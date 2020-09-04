/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.restapiplugin.server;

import eu.fasten.core.data.metadatadb.MetadataDao;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.api.RequestParameters;
import io.vertx.ext.web.api.contract.RouterFactoryOptions;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.sql.Timestamp;

public class OpenAPIServer extends AbstractVerticle {

    private static DSLContext dslContext;
    final Logger logger = LoggerFactory.getLogger("OpenAPI3RouterFactory");
    HttpServer server;

    public OpenAPIServer(DSLContext dsl) {
        dslContext = dsl;
    }

    @Override
    public void start(Future<Void> future) {
        OpenAPI3RouterFactory.create(this.vertx, "openapispec.json", asyncResult -> {
            if (asyncResult.succeeded()) {

                var metadataDao = new MetadataDao(dslContext);
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

                /**
                 * "/api/{forge}/{pkg_name}/deps/{timestamp}"
                 *
                 * Given a product (package name) and a timestamp, reconstruct its dependency network
                 *
                 * Return: A set of revisions, along with an adjacency matrix
                 *
                 * REST examples:
                 *  GET /api/mvn/org.slf4j:slf4j-api/deps/1233323123
                 *  GET /api/pypi/numpy/deps/1233323123?transitive=true
                 *
                 */
                routerFactory.addHandlerByOperationId
                        ("rebuild_dependency_net_api__forge___pkg_name__deps__timestamp__get",
                                routingContext -> {
                                    RequestParameters params = routingContext.get("parsedParameters");
                                    String forge = params.pathParameter("forge").getString();
                                    String pkgName = params.pathParameter("pkg_name").getString();
                                    int timestamp = params.pathParameter("timestamp").getInteger();
                                    boolean transitive = params.queryParameter("transitive").getBoolean();
                                    // TODO: check the transitive value and define it as false by default.
                                    logger.debug("DEBUG: Parsed parameters converted to string (" + forge + ", "
                                            + pkgName + ", " + timestamp + ").");

                                    dslContext.transaction(transaction -> {
                                        metadataDao.setContext(DSL.using(transaction));
                                        try {
                                            logger.debug("DEBUG: Got the DSL context to metadataDao" + metadataDao);

                                            String queryResult = metadataDao.rebuildDependencyNet(forge, pkgName, timestamp, transitive);

                                            logger.debug("DEBUG: Got the reply: " + queryResult.substring(0, 300) + "...");

                                            if (!queryResult.isEmpty())
                                                routingContext.response().setStatusCode(200)
                                                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                                        .end(queryResult);
                                            else
                                                routingContext.fail(404, new Exception("Query not found!"));
                                            // TODO: nice to have more specific exceptions ex: pkg not found, pkgName not found etc.
                                        } catch (RuntimeException e) {
                                            routingContext.fail(404, new Exception("Error querying the database!"));
                                            logger.error("Error querying the database: ", e);
                                            throw e;
                                        }
                                    });
                                });

                /**
                 * "/api/{forge}/{pkg_name}/cg/{timestamp}"
                 *
                 * Given a package name and a timestamp, retrieve its call graph
                 * Use case: A user wants to run a custom analysis locally.
                 *
                 * Return: A JSON-serialized RevisionCallGraph
                 *
                 * REST examples:
                 *  GET /api/mvn/org.slf4j:slf4j-api/cg/1233323123
                 *  GET /api/pypi/numpy/cg/1233323123?transitive=true
                 */
                routerFactory.addHandlerByOperationId
                        ("get_call_graph_api__forge___pkg_name__cg__timestamp__get",
                                routingContext -> {
                                    RequestParameters params = routingContext.get("parsedParameters");
                                    String forge = params.pathParameter("forge").getString();
                                    String pkgName = params.pathParameter("pkg_name").getString();
                                    int timestamp = params.pathParameter("timestamp").getInteger();
                                    boolean transitive = params.queryParameter("transitive").getBoolean();
                                    // TODO: check the transitive value and define it as false by default.
                                    logger.debug("DEBUG: Parsed parameters converted to string (" + forge + ", "
                                            + pkgName + ", " + timestamp + ").");

                                    dslContext.transaction(transaction -> {
                                        metadataDao.setContext(DSL.using(transaction));
                                        try {
                                            logger.debug("DEBUG: Got the DSL context to metadataDao" + metadataDao);

                                            String queryResult = metadataDao.getCallGraph(forge, pkgName, timestamp, transitive);

                                            logger.debug("DEBUG: Got the reply: " + queryResult.substring(0, 300) + "...");

                                            if (!queryResult.isEmpty())
                                                routingContext.response().setStatusCode(200)
                                                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                                        .end(queryResult);
                                            else
                                                routingContext.fail(404, new Exception("Query not found!"));
                                            // TODO: nice to have more specific exceptions ex: pkg not found, pkgName not found etc.
                                        } catch (RuntimeException e) {
                                            routingContext.fail(404, new Exception("Error querying the database!"));
                                            logger.error("Error querying the database: ", e);
                                            throw e;
                                        }
                                    });
                                });

                /**
                 * "/api/{forge}/{pkg_name}/{version}"
                 *
                 * Given a package name and a version, retrieve all known metadata
                 * Return: All known metadata for a revision
                 *
                 * REST examples:
                 *  GET /api/mvn/org.slf4j:slf4j-api/1.7.29
                 *  GET /api/pypi/numpy/1.15.2
                 */
                routerFactory.addHandlerByOperationId("get_metadata_api__forge___pkg_name___version__get",
                        routingContext -> {
                            RequestParameters params = routingContext.get("parsedParameters");
                            String forge = params.pathParameter("forge").getString();
                            String pkgName = params.pathParameter("pkg_name").getString();
                            String version = params.pathParameter("version").getString();

                            logger.debug("DEBUG: Parsed parameters converted to string (" + forge + ", "
                                    + pkgName + ", " + version + ").");

                            dslContext.transaction(transaction -> {
                                metadataDao.setContext(DSL.using(transaction));
                                try {
                                    logger.debug("DEBUG: Got the DSL context");

                                    String allMetadata = metadataDao.getAllMetadataForPkg(forge, pkgName, version);

                                    logger.debug("DEBUG: Got the reply: "+allMetadata.substring(0,300)+"...");

                                    if (!allMetadata.isEmpty())
                                        routingContext.response().setStatusCode(200)
                                            .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                            .end(allMetadata);
                                    else
                                        routingContext.fail(404, new Exception("Query not found!"));
                                        // TODO: nice to have more specific exceptions ex: pkg not found, pkgName not found etc.
                                } catch (RuntimeException e) {
                                    routingContext.fail(404, new Exception("Error querying the database!"));
                                    logger.error("Error querying the database: ", e);
                                    throw e;
                                }
                            });
                        });

                /**
                 * "/api/{forge}/{pkg_name}/{version}/vulnerabilities"
                 *
                 * Vulnerabilities in the transitive closure of a package version
                 * Expected result, in order of detail
                 * - Paths of revisions,
                 * - Paths of files / compilation units,
                 * - Paths of functions
                 *
                 * REST examples:
                 * GET /api/mvn/org.slf4j:slf4j-api/1.7.29/vulnerabilities
                 * GET /api/pypi/numpy/1.15.2/vulnerabilities
                 */
                routerFactory.addHandlerByOperationId
                        ("get_vulnerabilities_api__forge___pkg_name___version__vulnerabilities_get",
                                routingContext -> {
                                    RequestParameters params = routingContext.get("parsedParameters");
                                    String forge = params.pathParameter("forge").getString();
                                    String pkgName = params.pathParameter("pkg_name").getString();
                                    String version = params.pathParameter("version").getString();

                                    logger.debug("DEBUG: Parsed parameters converted to string (" + forge + ", "
                                            + pkgName + ", " + version + ").");

                                    dslContext.transaction(transaction -> {
                                        metadataDao.setContext(DSL.using(transaction));
                                        try {
                                            logger.debug("DEBUG: Got the DSL context to metadataDao" + metadataDao);

                                            // FIXME: update function ref
                                            String queryResult = metadataDao.getVulnerabilities(forge, pkgName, version);

                                            logger.debug("DEBUG: Got the reply: " + queryResult.substring(0, 300) + "...");

                                            if (!queryResult.isEmpty())
                                                routingContext.response().setStatusCode(200)
                                                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                                        .end(queryResult);
                                            else
                                                routingContext.fail(404, new Exception("Query not found!"));
                                            // TODO: nice to have more specific exceptions ex: pkg not found, pkgName not found etc.
                                        } catch (RuntimeException e) {
                                            routingContext.fail(404, new Exception("Error querying the database!"));
                                            logger.error("Error querying the database: ", e);
                                            throw e;
                                        }
                                    });
                                });

                /**
                 * "/api/{forge}/{pkg_name}/{version}/impact"
                 *
                 * Impact analysis
                 *
                 * Use case: the user asks the KB to compute the impact of a semantic change to a function
                 *
                 * Expected result: The full set of functions reachable from the provided function
                 *
                 * REST examples:
                 *  POST /api/mvn/org.slf4j:slf4j-api/1.7.29/impact
                 *  POST /api/mvn/org.slf4j:slf4j-api/1.7.29/impact?transitive=true
                 *
                 * The post body contains a FASTEN URI
                 */
                routerFactory.addHandlerByOperationId
                        ("update_impact_api__forge___pkg_name___version__impact_post",
                                routingContext -> {
                                    RequestParameters params = routingContext.get("parsedParameters");
                                    String forge = params.pathParameter("forge").getString();
                                    String pkgName = params.pathParameter("pkg_name").getString();
                                    String version = params.pathParameter("version").getString();
                                    boolean transitive = params.queryParameter("transitive").getBoolean();
                                    // TODO: check the transitive value and define it as false by default.
                                    logger.debug("DEBUG: Parsed parameters converted to string (" + forge + ", "
                                            + pkgName + ", " + version + ").");

                                    dslContext.transaction(transaction -> {
                                        metadataDao.setContext(DSL.using(transaction));
                                        try {
                                            logger.debug("DEBUG: Got the DSL context to metadataDao" + metadataDao);

                                            String queryResult = metadataDao.updateImpact(forge, pkgName, version, transitive);

                                            logger.debug("DEBUG: Got the reply: " + queryResult.substring(0, 300) + "...");

                                            if (!queryResult.isEmpty())
                                                routingContext.response().setStatusCode(200)
                                                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                                        .end(queryResult);
                                            else
                                                routingContext.fail(404, new Exception("Query not found!"));
                                            // TODO: nice to have more specific exceptions ex: pkg not found, pkgName not found etc.
                                        } catch (RuntimeException e) {
                                            routingContext.fail(404, new Exception("Error querying the database!"));
                                            logger.error("Error querying the database: ", e);
                                            throw e;
                                        }
                                    });
                                });

                /**
                 * "/api/{forge}/{pkg_name}/{version}/cg"
                 *
                 * Update the static CG of a package version with new edges
                 *
                 * Use case: A user runs an instrumented test suite locally and decides to update the central call
                 * graph with edges that do not exist due to shortcomings in static analysis.
                 *
                 * Expected result: A list of edges that where added.
                 *
                 * REST examples:
                 *  POST /api/mvn/org.slf4j:slf4j-api/1.7.29/cg
                 *  POST /api/pypi/numpy/1.15.2/cg"
                 * */
                routerFactory.addHandlerByOperationId
                        ("update_cg_api__forge___pkg_name___version__cg_post",
                                routingContext -> {
                                    RequestParameters params = routingContext.get("parsedParameters");
                                    String forge = params.pathParameter("forge").getString();
                                    String pkgName = params.pathParameter("pkg_name").getString();
                                    String version = params.pathParameter("version").getString();

                                    logger.debug("DEBUG: Parsed parameters converted to string (" + forge + ", "
                                            + pkgName + ", " + version + ").");

                                    dslContext.transaction(transaction -> {
                                        metadataDao.setContext(DSL.using(transaction));
                                        try {
                                            logger.debug("DEBUG: Got the DSL context to metadataDao" + metadataDao);

                                            String queryResult = metadataDao.updateCg(forge, pkgName, version);

                                            logger.debug("DEBUG: Got the reply: " + queryResult.substring(0, 300) + "...");

                                            if (!queryResult.isEmpty())
                                                routingContext.response().setStatusCode(200)
                                                        .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                                                        .end(queryResult);
                                            else
                                                routingContext.fail(404, new Exception("Query not found!"));
                                            // TODO: nice to have more specific exceptions ex: pkg not found, pkgName not found etc.
                                        } catch (RuntimeException e) {
                                            routingContext.fail(404, new Exception("Error querying the database!"));
                                            logger.error("Error querying the database: ", e);
                                            throw e;
                                        }
                                    });
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

    @Override
    public void stop(Future future) {
        logger.info("\n Closing server");
        this.server.close();
    }

}