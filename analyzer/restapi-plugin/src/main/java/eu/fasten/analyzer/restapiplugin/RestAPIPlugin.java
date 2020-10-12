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

package eu.fasten.analyzer.restapiplugin;

import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.plugins.DBConnector;
import eu.fasten.server.connectors.PostgresConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jooq.DSLContext;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class RestAPIPlugin extends Plugin {

    public RestAPIPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    /**
     * RESTeasy + Jetty REST API.
     */
    @Extension
    public static class RestAPIExtension implements DBConnector {

        private final Logger logger = LoggerFactory.getLogger(RestAPIPlugin.class.getName());

        /**
         * Application context, a.k.a. Jetty's handler tree.
         */
        protected static final String CONTEXT_ROOT = "/";

        /**
         * RESTeasy's HttpServletDispatcher at `APPLICATION_PATH`/*.
         */
        protected static final String APPLICATION_PATH = "";

        /**
         * REST server object.
         */
        protected final Server server;

        /**
         * REST server port.
         */
        protected final int SERVER_PORT;

        /**
         * KnoweldgeBase information.
         */
        protected final String KB_URL, KB_USER;

        /**
         * KnoweldgeBase DSL context.
         */
        protected static DSLContext kbDslContext;

        /**
         * KnowledgeBase data access object.
         */
        public static MetadataDao kbDao;

        private Throwable pluginError = null;

        /**
         * Default constructor, setting up the REST server.
         * This replaces the deployment descriptor file.
         *
         * @param port   REST server port.
         * @param kbUrl  KnowledgeBase URL.
         * @param kbUser KnowledgeBase username.
         */
        public RestAPIExtension(int port, String kbUrl, String kbUser) {
            logger.info("Setting up the REST server...");
            KB_URL = kbUrl;
            KB_USER = kbUser;
            SERVER_PORT = port;
            server = new Server(SERVER_PORT);
            final ServletContextHandler context = new ServletContextHandler(server, CONTEXT_ROOT);
            final ServletHolder restEasyServlet = new ServletHolder(new HttpServletDispatcher());
            restEasyServlet.setInitParameter("resteasy.servlet.mapping.prefix", APPLICATION_PATH);
            restEasyServlet.setInitParameter("javax.ws.rs.Application",
                    "eu.fasten.analyzer.restapiplugin.api.RestApplication");
            context.addServlet(restEasyServlet, APPLICATION_PATH + "/*");
            final ServletHolder defaultServlet = new ServletHolder(new DefaultServlet()); // default at context root
            context.addServlet(defaultServlet, CONTEXT_ROOT);
            logger.info("...REST server configuration done.");
        }

        @Override
        public void setDBConnection(DSLContext dslContext) {
            kbDslContext = dslContext;
        }

        @Override
        public String name() {
            return "REST API Plugin";
        }

        @Override
        public String description() {
            return "Rest API Plugin. "
                    + "Connects to the Knowledge Base and exposes canned queries.";
        }

        @Override
        public String version() {
            return "0.1.0";
        }

        /**
         * Starts the REST server and joins its thread.
         */
        @Override
        public void start() {

            logger.info("Establishing connection to the KnowledgeBase...");
            try {
                setDBConnection(PostgresConnector.getDSLContext(KB_URL, KB_USER));
                kbDao = new MetadataDao(kbDslContext);
            } catch (SQLException e) {
                logger.error("Couldn't connect to the KnowledgeBase", e);
                return;
            }
            logger.info("...KnowledgeBase connection established successfully.");

            logger.info("Starting the REST server on port " + SERVER_PORT + "...");
            try {
                server.start();
                server.join();
            } catch (InterruptedException e) {
                logger.error("REST API server thread has been interrupted", e);
                setPluginError(e);
            } catch (Exception e) {
                logger.error("Error while starting the REST API server", e);
                setPluginError(e);
            }
        }

        /**
         * Stops the REST server.
         */
        @Override
        public void stop() {
            logger.info("...shutting down the REST server.");
            try {
                server.stop();
            } catch (Exception e) {
                logger.error("Couldn't stop the REST server", e);
                setPluginError(e);
            }
        }

        @Override
        public Throwable getPluginError() {
            return this.pluginError;
        }

        public void setPluginError(Throwable throwable) {
            this.pluginError = throwable;
        }

        @Override
        public void freeResource() {
        }
    }
}