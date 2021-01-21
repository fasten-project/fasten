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

import java.util.List;
import java.util.Optional;
import java.util.Properties;

public interface KafkaPlugin extends FastenPlugin {
    /**
     * Returns an optional singleton list with a Kafka topic from which messages
     * need to be consumed. If Optional.empty() is returned, the plugin
     * will work in producer mode only.
     *
     * @return a list with a Kafka topic
     */
    Optional<List<String>> consumeTopic();

    /**
     * Overrides a consume topic of a plug-in.
     *
     * @param topicName new consume topic
     */
    void setTopic(String topicName);

    /**
     * Process an incoming record. This method return only when a record has been
     * processed.
     *
     * @param record a record to process
     */
    void consume(String record);

    /**
     * Return an optional results of the computation. The result is appended to
     * the payload of standard output message. If Optional.empty() is returned,
     * a standard output message with an empty payload is written.
     *
     * @return optional result of the computation
     */
    Optional<String> produce();

    /**
     * Returns a relative path to a file, the result of processing
     * a record should be written to. THe path has the following hierarchy:
     * /forge/first-letter-of-artifactId/artifactId/artifactId_groupId_Version.json
     *
     * @return relative path to the output file
     */
    String getOutputPath();


    /**
     * Corresponds to `max.poll.interval.ms` in the Kafka Consumer config.
     * Default is set to 10 minutes.
     *
     * Overriding this method will be reflected in the Kafka Consumer config.
     * @return the maximum time (in ms) a plugin can spend on a single record before timeout.
     */
    default long getMaxConsumeTimeout() {
        return 600000;
    }

    /**
     * Corresponds to `session.timeout.ms` in the Kafka Consumer config.
     * Default is set to 1 minute for non static membership, otherwise 10 minutes.
     *
     * Overriding this method will be reflected in the Kafka Consumer config.
     * @return the maximum time (in ms) a plugin can be unresponsive in the heartbeat thread, before it's considered 'dead'.
     */
    default long getSessionTimeout() {
        if (!isStaticMembership()) {
            return 600000;
        } else {
            return 60000;
        }
    }

    /**
     * Reflects if the Kafka consumer should enable 'Static Membership'.
     * Default is enabled if the env POD_INSTANCE_ID is used.
     *
     * Overriding this method will be reflected in the Kafka Consumer config.
     * If enabled, the plugin should set `POD_INSTANCE_ID` as environment variable. Each plugin/deployment should have an unique and static id.
     * This `POD_INSTANCE_ID` will be set in Kafka Consumer config for the key `group.instance.id`.
     * @return if the plugin should consume using 'Static Membership'.
     */
    default boolean isStaticMembership() {
        return System.getenv("POD_INSTANCE_ID") != null;
    }
}
