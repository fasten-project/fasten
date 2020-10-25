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

import eu.fasten.analyzer.restapiplugin.server.OpenAPIServer;
import eu.fasten.core.plugins.DBConnector;
import io.vertx.core.Vertx;
import org.jooq.DSLContext;
import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class RestAPIPlugin extends Plugin {

    public RestAPIPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Extension
    public static class RestAPIExtension implements DBConnector {

        private static DSLContext dslContext;
        private final Logger logger = LoggerFactory.getLogger(RestAPIPlugin.class.getName());
        private Throwable pluginError = null;
        private Vertx vertx;

        @Override
        public void setDBConnection(Map<String, DSLContext> dslContexts) {
            RestAPIExtension.dslContext = dslContexts.get("java");
        }

        @Override
        public String name() {
            return this.getClass().getCanonicalName();
        }

        @Override
        public String description() {
            return "Rest API Plugin. "
                    + "Connects to the metadata database and expose canned queries.";
        }

        @Override
        public String version() {
            return "0.0.1";
        }

        @Override
        public void start() {
            vertx = Vertx.vertx();
            vertx.deployVerticle(new OpenAPIServer(dslContext));
            logger.debug("Deployed Verticle: " + vertx);
        }

        @Override
        public void stop() {
            vertx.close();
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
            vertx.close();
            dslContext.close();
        }
    }
}