package eu.fasten.server.plugins.kafka;

import eu.fasten.core.plugins.KafkaPlugin;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class KafkaConsumeTimeoutTest {

    @Test
    public void testDisableTimeout() {
        DummyPlugin dummyPlugin = new DummyPlugin(false, 0);

        FastenKafkaPlugin plugin = new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", false, -1);

        assertFalse(plugin.isConsumeTimeoutEnabled());
        assertEquals(-1, plugin.getConsumeTimeout());
    }

    @Test
    public void testEnableTimeout() {
        DummyPlugin dummyPlugin = new DummyPlugin(false, 0);

        FastenKafkaPlugin plugin = new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", true, 10);

        assertTrue(plugin.isConsumeTimeoutEnabled());
        assertEquals(10, plugin.getConsumeTimeout());
    }

    class DummyPlugin implements KafkaPlugin {

        private final boolean blocking;
        private final int blockTime;

        public DummyPlugin(boolean blocking, int blockTime) {
            this.blocking = blocking;
            this.blockTime = 0;
        }

        @Override
        public Optional<List<String>> consumeTopic() {
            return Optional.empty();
        }

        @Override
        public void setTopic(String topicName) {

        }

        @Override
        public void consume(String record) {
            try {
                if (blocking) {
                    Thread.sleep(blockTime * 1000);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public Optional<String> produce() {
            return Optional.empty();
        }

        @Override
        public String getOutputPath() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public String description() {
            return null;
        }

        @Override
        public String version() {
            return null;
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public Throwable getPluginError() {
            return null;
        }

        @Override
        public void freeResource() {

        }
    }
}
