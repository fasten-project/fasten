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

package eu.fasten.core.plugins;

import eu.fasten.core.data.graphdb.RocksDao;

/**
 * A plug-in that needs to access graph database (RocksDB) in a read-only mode
 */
public interface GraphDBReader extends FastenPlugin {

    /**
     * Sets read-only RocksDB connection.
     *
     * @param rocksDao RocksDB Database Access Object
     */
    void setRocksDao(RocksDao rocksDao);
}
