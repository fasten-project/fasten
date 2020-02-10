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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.pf4j.ExtensionPoint;

import java.util.List;

/**
 * Indicates a plug-in that works by consuming records from Kafka.
 *
 * @param <T> The type of data that this plug-in expects to consume. The FASTEN core
 *           only provides a de-serializer from JSON data into
 *           {@link eu.fasten.core.data.RevisionCallGraph} entries. All other types
 *           must be accompanied by custom de-serializers.
 */
public interface KafkaConsumer<T> extends FastenPlugin {

    /**
     * The topic this plug-in is interested into.
     */
    public List<String> consumerTopics();

    /**
     * Processed an incoming record. This method must return when the record
     * has been consumed, otherwise it will block further record delivery.
     *
     * @param record A record, de-serialized to the provided type {@link T}
     */
    public void consume(String topic, ConsumerRecord<String, T> record);

}
