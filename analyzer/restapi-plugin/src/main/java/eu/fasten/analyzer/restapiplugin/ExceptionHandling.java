/*
 * Copyright 2022 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.restapiplugin;

import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.inject.Inject;

@ControllerAdvice
public class ExceptionHandling extends ResponseEntityExceptionHandler {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final int PSQL_EXCEPTION_ERR_CODE = 2;

    @Inject
    ShutdownManager shutdownManager;

    @ExceptionHandler(PSQLException.class)
    public void handlePSQLException(PSQLException exception) {
        logger.error("Could not access the Postgres server! The REST API should be stopped and restarted now to create a new DB connection.");
        exception.printStackTrace();
        shutdownManager.initiateShutdown(PSQL_EXCEPTION_ERR_CODE);
    }
}
