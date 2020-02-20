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

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PostgresConnector {

    private static final String dbUrl = "jdbc:postgresql:postgres";
    private static final String dbUser = "postgres";
    private static final String dbPass = "pass123";

    public static DSLContext getDSLContext() throws SQLException {
        Connection connection = getLocalConnection();
        return DSL.using(connection, SQLDialect.POSTGRES);
    }

    public static Connection getLocalConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPass);
    }
}
