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

package eu.fasten.analyzer.metadataplugin.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public class PostgresConnector {

    public static DSLContext getDSLContext() throws SQLException, IOException {
        var connection = getConnection();
        return DSL.using(connection, SQLDialect.POSTGRES);
    }

    /**
     * Create a connection to the database specified in 'postgres.properties'.
     *
     * @return SQL Connection object
     * @throws SQLException if failed to set up connection
     * @throws IOException  if failed to read the 'postgres.properties' file
     */
    public static Connection getConnection() throws SQLException, IOException {
        var dbProps = getPostgresProperties();
        return DriverManager.getConnection(
                dbProps.getProperty("dbUrl"), dbProps.getProperty("dbUser"),
                dbProps.getProperty("dbPass"));
    }

    private static Properties getPostgresProperties() throws IOException {
        try (var resource = PostgresConnector.class.getClassLoader()
                .getResourceAsStream("postgres.properties")) {
            var connectionsProps = new Properties();
            if (resource != null) {
                connectionsProps.load(resource);
                return connectionsProps;
            } else {
                throw new IOException("Cannot find 'postgres.properties' file");
            }
        }
    }
}
