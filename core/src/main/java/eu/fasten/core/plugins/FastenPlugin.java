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

/**
 * Base interface for all FASTEN plugins. Used mostly for discovery and loading.
 *
 * The lifecycle of a plug-in is the following:
 * <ol>
 *     <li>The FASTEN server discovers the plug-in JAR in a provided directory</li>
 *     <li>The plug-in is loaded dynamically, through reflection.</li>
 *     <li>The {@link :name()} method is invoked.</li>
 *     <li>The server examines plug-in compatibility with each of the plug-in subclasses.
 *     If the plug-in is compatible, then it performs sub-class specific initialization (if any) </li>
 *     <li>The {@link :start() method is invoked}</li>
 *     <li>The server invokes functionality on concrete plug-ins.</li>
 *     <li>The {@link :stop() method is invoked when the server is about to shutdown}</li>
 * </ol>
 *
 */
public interface FastenPlugin {

    /**
     * Returns a unique name for the plug-in
     *
     * @return The plugin's fully qualified name
     */
    public String name();

    /**
     * Returns a longer description of the plug-in functionality
     *
     * @return A string describing what the plug-in does.
     */
    public String description();

    /**
     * Lifecycle operations: Invoked when the server has allocated all resources required to run
     * this plug-in
     */
    public void start();

    /**
     * Invoked when the server is about to shutdown. This enables the plug-in to shutdown gracefully.
     */
    public void stop();
}
