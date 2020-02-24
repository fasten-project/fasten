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

import org.pf4j.ExtensionPoint;

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
public interface FastenPlugin extends ExtensionPoint {

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

    /**
     * These two methods should be implemented so that the FASTEN server can retrieve the plug-in's error and its exception
     * type. This will help to reprocess certain types of failed records.
     */
    public void setPluginError(Throwable throwable);

    /**
     * This method should return a JSON string with the following fields:
     * - plugin: the name of the plugin
     * - msg: the message of the exception
     * - trace: the stack trace of the exception
     * - type: the type of the exception, e.g. FileNotFoundException
     *
     * @return
     */

    public String getPluginError();

    /**
     * The purpose of this method is to release all the resources of a plug-in. For example, closing a stream or setting
     * a big object to null.
     */
    public void freeResource();

}
