package eu.fasten.core.plugins;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

public interface KafkaPlugin<I, O> extends FastenPlugin, Callable<Optional<O>> {
    Optional<List<String>> consumeTopics();

    void setTopic(String topicName);

    void consume(String record);

    Optional<O> call();

    boolean recordProcessSuccessful();
}
