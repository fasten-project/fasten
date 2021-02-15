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

package eu.fasten.server.plugins.kafka;

import com.google.common.base.Strings;
import eu.fasten.core.plugins.KafkaPlugin;
import eu.fasten.server.plugins.FastenServerPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastenKafkaPlugin implements FastenServerPlugin {
    private final Logger logger = LoggerFactory.getLogger(FastenKafkaPlugin.class.getName());

    private final KafkaPlugin plugin;

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private KafkaConsumer<String, String> connection;

    private KafkaProducer<String, String> producer;
    private final String outputTopic;

    private final int skipOffsets;

    private final String writeDirectory;
    private final String writeLink;

    // Configuration for consumer timeout.
    private final boolean consumeTimeoutEnabled;
    private final long consumeTimeout;
    private final boolean exitOnTimeout;

    // Local storage for duplicate processing.
    private final LocalStorage localStorage;

    // Executor service which creates a thread pool and re-uses threads when possible.
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * Constructs a FastenKafkaConsumer.
     *
     * @param consumerProperties properties of a consumer
     * @param plugin             Kafka plugin
     * @param skipOffsets        skip offset number
     */
    public FastenKafkaPlugin(boolean enableKafka, Properties consumerProperties, Properties producerProperties,
                             KafkaPlugin plugin, int skipOffsets, String writeDirectory, String writeLink, String outputTopic, boolean consumeTimeoutEnabled, long consumeTimeout, boolean exitOnTimeout, boolean enableLocalStorage, String localStorageDir) {
        this.plugin = plugin;

        if (enableKafka) {
            this.connection = new KafkaConsumer<>(consumerProperties);
            this.producer = new KafkaProducer<>(producerProperties);
        }

        this.skipOffsets = skipOffsets;
        if (writeDirectory != null) {
            this.writeDirectory = writeDirectory.endsWith(File.separator)
                    ? writeDirectory.substring(0, writeDirectory.length() - 1) : writeDirectory;
        } else {
            this.writeDirectory = null;
        }
        if (writeLink != null) {
            this.writeLink = writeLink.endsWith(File.separator)
                    ? writeLink.substring(0, writeLink.length() - 1) : writeLink;


        } else {
            this.writeLink = null;
        }

        // If the write link is not null, and local storage is enabled. Initialize it.
        if (enableLocalStorage) {
            this.localStorage = new LocalStorage(localStorageDir);
        } else {
            this.localStorage = null;
        }

        this.outputTopic = outputTopic;
        this.consumeTimeoutEnabled = consumeTimeoutEnabled;
        this.consumeTimeout = consumeTimeout;
        this.exitOnTimeout = exitOnTimeout;
        logger.debug("Constructed a Kafka plugin for " + plugin.getClass().getCanonicalName());
    }

    public FastenKafkaPlugin(Properties consumerProperties, Properties producerProperties,
                             KafkaPlugin plugin, int skipOffsets, String writeDirectory, String writeLink, String outputTopic, boolean consumeTimeoutEnabled, long consumeTimeout, boolean exitOnTimeout, boolean enableLocalStorage, String localStorageDir) {
        this(true, consumerProperties, producerProperties, plugin, skipOffsets, writeDirectory, writeLink, outputTopic, consumeTimeoutEnabled, consumeTimeout, exitOnTimeout, enableLocalStorage, localStorageDir);
    }


    @Override
    public void run() {
        try {
            if (plugin.consumeTopic().isPresent()) {
                connection.subscribe(plugin.consumeTopic().get());
            }
            if (this.skipOffsets == 1) {
                skipPartitionOffsets();
            }

            while (!closed.get()) {
                if (plugin.consumeTopic().isPresent()) {
                    handleConsuming();
                } else {
                    doCommitSync();
                    handleProducing(null, System.currentTimeMillis() / 1000L);
                }
            }
        } catch (Exception e) {
            logger.error("Error occurred while processing call graphs", e);
        } finally {
            connection.close();
            logger.info("Plugin {} stopped", plugin.name());
        }
    }

    /**
     * Starts the plugin.
     */
    public void start() {
        this.run();
    }

    /**
     * Sends a wake up signal to Kafka consumer and stops it.
     */
    public void stop() {
        closed.set(true);
    }

    /**
     * Consumes a message from a Kafka topics and passes it to a plugin.
     */
    public void handleConsuming() {
        ConsumerRecords<String, String> records = connection.poll(Duration.ofSeconds(1));
        Long consumeTimestamp = System.currentTimeMillis() / 1000L;

        // Keep a list of all records and offsets we processed (by default this is only 1).
        ArrayList<ImmutablePair<Long, Integer>> messagesProcessed = new ArrayList<ImmutablePair<Long, Integer>>();

        // Although we loop through all records, by default we only poll 1 record.
        for (var r : records) {
            logger.info("Read message offset " + r.offset() + " from partition " + r.partition() + ".");
            processRecord(r, consumeTimestamp);
            logger.info("Successfully processed message offset " + r.offset() + " from partition " + r.partition() + ".");

            messagesProcessed.add(new ImmutablePair<>(r.offset(), r.partition()));
        }

        // Commit only after _all_ records are processed.
        // For most plugins, this loop will only process 1 record (since max.poll.records is 1).
        doCommitSync();

        // More logging.
        String allOffsets = messagesProcessed.stream().map((x) -> x.left).map(Object::toString)
                .collect(Collectors.joining(", "));
        String allPartitions = messagesProcessed.stream().map((x) -> x.right).map(Object::toString)
                .collect(Collectors.joining(", "));

        if (!records.isEmpty()) {
            logger.info("Committed offsets [" + allOffsets + "] of partitions [" + allPartitions + "].");
        }


        // If local storage is enabled, clear the correct partitions after offsets are committed.
        if (localStorage != null) {
            localStorage.clear(messagesProcessed.stream().map((x) -> x.right).collect(Collectors.toList()));
        }
    }

    /**
     * Consumer strategy (using local storage):
     * <p>
     * 1. Poll one record (by default).
     * 2. If the record hash is in local storage:
     * a. Produce to error topic (this record is probably processed before and caused a crash or timeout).
     * b. Commit the offset, if producer confirmed sending the message.
     * c. Delete record in local storage.
     * d. Go back to 1.
     * 3. If the record hash is _not_ in local storage:
     * a. Process the record.
     * b. Produce its results (either to the error topic, or output topic).
     * c. Commit the offset if producer confirmed sending the message.
     * d. Delete record in local storage.
     * e. Go back to 1.
     * <p>
     * This strategy provides at-least-once semantics.
     */
    public void processRecord(ConsumerRecord<String, String> record, Long consumeTimestamp) {
        if (localStorage != null) { // If local storage is enabled.
            if (localStorage.exists(record.value(), record.partition())) { // This plugin already consumed this record before, we will not process it now.
                logger.info("Already processed record with hash: " + localStorage.getSHA1(record.value()) + ", skipping it now.");
                plugin.setPluginError(new ExistsInLocalStorageException("Record already exists in local storage. Most probably it has been processed before and the pod crashed."));
            } else {
                try {
                    localStorage.store(record.value(), record.partition());
                } catch (IOException e) {
                    // We couldn't store the message SHA. Will just continue processing, but log the error.
                    // This strategy might result in the deadlock/retry behavior of the same coordinate.
                    // However, if local storage is failing we can't store the CG's either and that's already a problem.
                    logger.error("Trying to store the hash of a record, but failed due to an IOException", e);
                } finally { // Event if we hit an IOException, we will execute this finally block.
                    if (consumeTimeoutEnabled) {
                        consumeWithTimeout(record.value(), consumeTimeout, exitOnTimeout);
                    } else {
                        plugin.consume(record.value());
                    }
                }
            }
        } else { // If local storage is not enabled.
            if (consumeTimeoutEnabled) {
                consumeWithTimeout(record.value(), consumeTimeout, exitOnTimeout);
            } else {
                plugin.consume(record.value());
            }
        }

        // We always produce, it does not matter if local storage is enabled or not.
        handleProducing(record.value(), consumeTimestamp);
    }

    /**
     * Writes messages to server log and stdout/stderr topics.
     *
     * @param input input message [can be null]
     */
    public void handleProducing(String input, long consumeTimestamp) {
        try {
            if (plugin.getPluginError() != null) {
                throw plugin.getPluginError();
            }

            var result = plugin.produce();
            String payload = result.orElse(null);
            if (result.isPresent() && writeDirectory != null && !writeDirectory.equals("")) {
                payload = writeToFile(payload);
            }

            emitMessage(this.producer, String.format("fasten.%s.out",
                    outputTopic),
                    getStdOutMsg(input, payload, consumeTimestamp));

        } catch (Exception e) {
            emitMessage(this.producer, String.format("fasten.%s.err",
                    outputTopic),
                    getStdErrMsg(input, e, consumeTimestamp));
        }
    }

    /**
     * Send message to Kafka topic.
     *
     * @param producer Kafka producer
     * @param topic    topic to send to
     * @param msg      message
     */
    private void emitMessage(KafkaProducer<String, String> producer, String topic, String msg) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, msg);

        producer.send(record, (recordMetadata, e) -> {
            if (recordMetadata != null) {
                logger.debug("Sent: {} to {}", msg, topic);
            } else {
                e.printStackTrace();
            }
        });

        producer.flush();
    }

    /**
     * Writes output or error message to JSON file and return JSON object containing
     * a link to to written file.
     *
     * @param result message to write
     * @return Path to a newly written JSON file
     */
    private String writeToFile(String result)
            throws IOException, NullPointerException {
        var path = plugin.getOutputPath();
        var pathWithoutFilename = path.substring(0, path.lastIndexOf(File.separator));

        File directory = new File(this.writeDirectory + pathWithoutFilename);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create parent directories");
        }

        File file = new File(this.writeDirectory + path);
        FileWriter fw = new FileWriter(file.getAbsoluteFile());
        fw.write(result);
        fw.flush();
        fw.close();

        JSONObject link = new JSONObject();
        link.put("dir", file.getAbsolutePath());

        if (this.writeLink != null && !this.writeLink.equals("")) {
            link.put("link", this.writeLink + path);
        }
        return link.toString();
    }

    /**
     * Create a message that will be send to STDOUT of a given plugin.
     *
     * @param input   consumed record
     * @param payload output of the plugin
     * @return stdout message
     */
    private String getStdOutMsg(String input, String payload, long consumeTimestamp) {
        JSONObject stdoutMsg = new JSONObject();
        stdoutMsg.put("created_at", System.currentTimeMillis() / 1000L);
        stdoutMsg.put("consumed_at", consumeTimestamp);
        stdoutMsg.put("plugin_name", plugin.getClass().getSimpleName());
        stdoutMsg.put("plugin_version", plugin.version());
        try {
            stdoutMsg.put("host", InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            stdoutMsg.put("host", "unknown");
        }
        stdoutMsg.put("input", StringUtils.isNotEmpty(input) ? new JSONObject(input) : "");
        stdoutMsg.put("payload", StringUtils.isNotEmpty(payload) ? new JSONObject(payload) : "");

        return stdoutMsg.toString();
    }

    /**
     * Create a message that will be send to STDERR of a given plugin.
     *
     * @param input consumed record
     * @return stderr message
     */
    private String getStdErrMsg(String input, Throwable pluginError, long consumeTimestamp) {
        JSONObject stderrMsg = new JSONObject();
        stderrMsg.put("created_at", System.currentTimeMillis() / 1000L);
        stderrMsg.put("plugin_name", plugin.getClass().getSimpleName());
        stderrMsg.put("consumed_at", consumeTimestamp);
        stderrMsg.put("plugin_version", plugin.version());
        try {
            stderrMsg.put("host", InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            stderrMsg.put("host", "unknown");
        }
        stderrMsg.put("input", !Strings.isNullOrEmpty(input) ? new JSONObject(input) : "");

        JSONObject error = new JSONObject();
        error.put("error", pluginError.getClass().getSimpleName());
        error.put("msg", pluginError.getMessage());
        error.put("stacktrace", pluginError.getStackTrace());

        stderrMsg.put("err", error);

        return stderrMsg.toString();
    }

    /**
     * This is a synchronous commits and will block until either the commit succeeds
     * or an unrecoverable error is encountered.
     */
    private void doCommitSync() {
        try {
            connection.commitSync();
        } catch (WakeupException e) {
            // we're shutting down, but finish the commit first and then
            // rethrow the exception so that the main loop can exit
            doCommitSync();
            throw e;
        } catch (CommitFailedException e) {
            // the commit failed with an unrecoverable error. if there is any
            // internal state which depended on the commit, you can clean it
            // up here. otherwise it's reasonable to ignore the error and go on
            logger.error("Commit failed", e);
        }
    }


    /**
     * This method adds one to the offset of all the partitions of a topic.
     * This is useful when you want to skip an offset with FATAL errors when
     * the FASTEN server is restarted.
     * Please note that this is NOT the most efficient way to restart FASTEN server
     * in the case of FATAL errors.
     */
    private void skipPartitionOffsets() {
        ArrayList<TopicPartition> topicPartitions = new ArrayList<>();
        List<String> topics = new ArrayList<>();
        this.plugin.consumeTopic().ifPresentOrElse(topics::addAll, () -> {
        });
        if (topics.isEmpty()) {
            return;
        }
        // Note that this assumes that the consumer is subscribed to one topic only
        for (PartitionInfo p : this.connection.partitionsFor(topics.get(0))) {
            topicPartitions.add(new TopicPartition(topics.get(0), p.partition()));
        }

        ConsumerRecords<String, String> records = dummyPoll(this.connection);

        if (records.count() != 0) {
            for (TopicPartition tp : topicPartitions) {
                logger.debug("Topic: {} | Current offset for partition {}: {}", topics.get(0),
                        tp, this.connection.position(tp));

                this.connection.seek(tp, this.connection.position(tp) + 1);

                logger.debug("Topic: {} | Offset for partition {} is set to {}",
                        topics.get(0),
                        tp, this.connection.position(tp));
            }
        }
    }

    /**
     * This is a dummy poll method for calling lazy methods such as seek.
     *
     * @param consumer Kafka consumer
     * @return consumed Kafka record
     */
    private ConsumerRecords<String, String> dummyPoll(KafkaConsumer<String, String> consumer) {
        ConsumerRecords<String, String> statusRecords;
        int i = 0;
        do {
            statusRecords = consumer.poll(Duration.ofMillis(100));
            i++;
        } while (i <= 5 && statusRecords.count() == 0);

        return statusRecords;
    }

    /**
     * Consumes an input with a timeout. If the timeout is exceeded the thread handling the message is killed.
     *
     * @param input   the input message to be consumed.
     * @param timeout the timeout in seconds. I.e. the maximum time a plugin can spend on processing a record.
     *                <p>
     *                Based on: https://stackoverflow.com/questions/1164301/how-do-i-call-some-blocking-method-with-a-timeout-in-java
     */
    public void consumeWithTimeout(String input, long timeout, boolean exitOnTimeout) {
        Runnable consumeTask = () -> plugin.consume(input);

        // Submit the consume task to a thread.
        var futureConsumeTask = executorService.submit(consumeTask);

        try {
            futureConsumeTask.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutException) {
            // In this situation the consumeTask took longer than the timeout.
            // We will send an error to the err topic by setting the plugin error.
            plugin.setPluginError(timeoutException);
            logger.error("A TimeoutException occurred, processing a record took more than " + timeout + " seconds.");
            if (exitOnTimeout) { // Exit if the timeout is reached.
                System.exit(0);
            }
        } catch (InterruptedException interruptedException) {
            // The consumeTask thread was interrupted.
            plugin.setPluginError(interruptedException);
            logger.error("A InterruptedException occurred", interruptedException);
        } catch (ExecutionException executionException) {
            // In this situation the consumeTask threw an exception during computation.
            // We kind of expect this not to happen, because (at least for OPAL) plugins should with exception themselves.
            plugin.setPluginError(executionException);
            logger.error("A ExecutionException occurred", executionException);
        } finally {
            // Finally we will kill the current thread if it's still running so we can continue processing the next record.
            futureConsumeTask.cancel(true);
        }
    }

    /**
     * Verify is the consumer timeout is enabled.
     *
     * @return if a consumer timeout is enabled.
     */
    public boolean isConsumeTimeoutEnabled() {
        return consumeTimeoutEnabled;
    }

    /**
     * Get the consume timeout (in seconds).
     *
     * @return consume timeout.
     */
    public long getConsumeTimeout() {
        return consumeTimeout;
    }
}
