package eu.fasten.core.plugins;

import java.util.List;
import java.util.Optional;

public interface KafkaPlugin<I, O> extends FastenPlugin {
    Optional<List<String>> consumeTopics();

    void setTopic(String topicName);

    void consume(I record);

    Optional<O> produce();

    boolean recordProcessSuccessful();
}
