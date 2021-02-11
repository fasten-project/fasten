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

package eu.fasten.server.connectors;

import java.util.List;
import java.util.Properties;

import eu.fasten.core.plugins.KafkaPlugin;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConnector {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConnector.class);

    /**
     * Returns Kafka properties.
     *
     * @param serverAddresses broker address
     * @param groupId         group id
     * @param sessionTimeout a value for `session.timeout.ms`.
     * @param maxPollInterval a value for `max.poll.interval.ms`.
     * @param staticMemberShip if static membership should be enabled.
     * @return Kafka Properties
     */
    public static Properties kafkaConsumerProperties(List<String> serverAddresses, String groupId, long sessionTimeout, long maxPollInterval, boolean staticMemberShip) {
        String deserializer = StringDeserializer.class.getName();
        Properties properties = new Properties();

        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                String.join(",", serverAddresses));
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, groupId + "_client");
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, deserializer);
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1");
        properties.setProperty(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, "50000000"); //Set max read size to 50 MB.
        properties.setProperty(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "5000"); // 5 seconds

        // Default consumption configuration
        properties.setProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(sessionTimeout));
        properties.setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, String.valueOf(maxPollInterval));

        if (staticMemberShip) {
            // Assign a static ID to the consumer based pods' unique name in K8s env.
            if (System.getenv("POD_INSTANCE_ID") != null) {
                logger.info(String.format("Static Membership ID: %s", System.getenv("POD_INSTANCE_ID")));
                properties.setProperty(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, System.getenv("POD_INSTANCE_ID"));
            } else {
                logger.warn("Static Membership was enabled but POD_INSTANCE_ID was not defined. Plugin will proceed without Static Membership.");
            }

        }

        return properties;
    }

    /**
     * Sets up a connection for producing messages to Kafka.
     *
     * @param serverAddresses address of server
     * @param groupID         group id
     * @return properties for producer
     */
    public static Properties kafkaProducerProperties(List<String> serverAddresses, String groupID) {
        Properties p = new Properties();
        p.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                String.join(",", serverAddresses));
        p.setProperty(ProducerConfig.CLIENT_ID_CONFIG, groupID + "_producer");
        p.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        p.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());
        p.setProperty(ProducerConfig.MAX_REQUEST_SIZE_CONFIG,
                "50000000"); //Set produce size to 50MB.
        return p;
    }
}
