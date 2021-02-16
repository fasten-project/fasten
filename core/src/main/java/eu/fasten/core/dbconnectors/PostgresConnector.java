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

package eu.fasten.core.dbconnectors;

import java.sql.DriverManager;
import java.sql.SQLException;
import eu.fasten.core.data.Constants;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.postgresql.Driver;

public class PostgresConnector {

    /**
     * Establishes database connection.
     *
     * @param dbUrl URL of the database to connect
     * @param user  Database user name
     * @return DSLContext for jOOQ to query the database
     * @throws SQLException             if failed to set up connection
     * @throws IllegalArgumentException if database URL has incorrect format and cannot be parsed
     */
    public static DSLContext getDSLContext(String dbUrl, String user, boolean autocommit)
            throws SQLException, IllegalArgumentException {
        if (!new Driver().acceptsURL(dbUrl)) {
            throw new IllegalArgumentException("Could not parse database URI: " + dbUrl);
        }
        var pass = System.getenv(Constants.fastenDbPassEnvVariable) != null
                ?  System.getenv(Constants.fastenDbPassEnvVariable)
                : System.getenv(Constants.pgPasswordEnvVariable);

        if (pass == null) {
            throw new IllegalArgumentException("No password for DB is provided");
        }
        var connection = DriverManager.getConnection(dbUrl, user, pass);
        connection.setAutoCommit(autocommit);
        return DSL.using(connection, SQLDialect.POSTGRES);
    }
}
