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
 * Indicates a plug-in that produces records to Kafka. As per FASTEN conventions,
 * the plug-ins are expected to produce text output in the JSON format.
 */
public interface KafkaProducer extends ExtensionPoint {

    /**
     * A unique name for the producer topic to write to. If multiple plug-ins specify the same topic,
     * the FASTEN server discards all plug-ins except the first to declare the topic.
     */
    public String producerTopic();

    /**
     * The server provides the plug-in with an already initialized Kafka producer. The plug-in
     * implementation can use it to write a set of mechanisms to Kafka, in a blocking fashion.
     */
    public void setKafkaProducer(org.apache.kafka.clients.producer.KafkaProducer<Object, String> producer);
}
