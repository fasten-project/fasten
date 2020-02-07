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

package eu.fasten.server.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Properties;

public abstract class FastenKafkaConnection extends Thread {

    Properties connProperties;


    public FastenKafkaConnection(Properties p) {
        this.connProperties = p;
    }

    public static Properties kafkaProperties(List<String> serverAddresses, String groupId) {
        String deserializer = StringDeserializer.class.getName();
        Properties properties = new Properties();

        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", serverAddresses));
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.CLIENT_ID_CONFIG, groupId + "_client");
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, deserializer);
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "5");
        properties.setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "20000000");

        // Gives more time to the consumer for processing the records so that the broker will NOT kill the consumer.
        properties.setProperty(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "200000");
        properties.setProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "700000");

        return properties;
    }

    public static Properties producerProperties(List<String> serverAddresses, String clientId){

        String serializer = StringSerializer.class.getName();
        Properties properties = new Properties();

        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", serverAddresses));
        properties.setProperty(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, serializer);
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializer);
        properties.setProperty(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "100000000");

        return properties;
    }

}
