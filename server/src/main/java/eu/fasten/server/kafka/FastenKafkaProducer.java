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

import org.apache.kafka.clients.producer.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class FastenKafkaProducer extends FastenKafkaConnection {

    private final Logger logger = LoggerFactory.getLogger(FastenKafkaProducer.class.getName());
    KafkaProducer<Object, String> connection;
    eu.fasten.core.plugins.KafkaProducer producer;
    private CountDownLatch mLatch;

    public FastenKafkaProducer(Properties p, eu.fasten.core.plugins.KafkaProducer kp) {
        super(p);
        this.producer = kp;
        this.mLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.debug("Caught shutdown hook");
            try {
                mLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            logger.debug("{} has exited", producer.getClass().getCanonicalName());
        }));

    }

    @Override
    public void run(){
        logger.info("Starts....");
        if(this.connection == null){
            this.connection = new KafkaProducer<Object, String>(this.connProperties);
        }
        this.producer.setKafkaProducer(this.connection);
        logger.debug("Sets a Kafka producer.....");
    }

}
