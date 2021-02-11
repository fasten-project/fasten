package eu.fasten.server.plugins.kafka;

import eu.fasten.core.plugins.KafkaPlugin;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public class KafkaConsumeTimeoutTest {

    @Test
    public void testDisableTimeout() {
        DummyPlugin dummyPlugin = new DummyPlugin(false, 0);

        FastenKafkaPlugin plugin = new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", false, -1, false, false, "");

        assertFalse(plugin.isConsumeTimeoutEnabled());
        assertEquals(-1, plugin.getConsumeTimeout());
    }

    @Test
    public void testEnableTimeout() {
        DummyPlugin dummyPlugin = new DummyPlugin(false, 0);

        FastenKafkaPlugin plugin = new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", true, 10, false, false, "");

        assertTrue(plugin.isConsumeTimeoutEnabled());
        assertEquals(10, plugin.getConsumeTimeout());
    }

    @Test
    public void testTimeoutNoInterrupt() {
        long blockTime = 2;
        long timeOut = 3;
        DummyPlugin dummyPlugin = new DummyPlugin(true, blockTime);

        FastenKafkaPlugin plugin = new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", true, timeOut, false, false, "");

        long startTime  = System.currentTimeMillis();
        plugin.consumeWithTimeout("dummy", timeOut, false);
        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;

        assertTrue(duration >= blockTime);
    }

    @Test
    public void testTimeoutInterrupt() {
        long blockTime = 10;
        long timeOut = 2;
        DummyPlugin dummyPlugin = new DummyPlugin(true, blockTime);

        FastenKafkaPlugin plugin = new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", true, timeOut, false, false, "");

        long startTime  = System.currentTimeMillis();
        plugin.consumeWithTimeout("dummy", timeOut, false);
        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;


        assertTrue(duration >= timeOut);
        assertEquals(new TimeoutException().getClass(), dummyPlugin.getPluginError().getClass()); // verify if a TimeoutException
    }

    @Test
    public void testTimeoutInterruptBlockForever() {
        long blockTime = -1; //this will block forever
        long timeOut = 2;
        DummyPlugin dummyPlugin = new DummyPlugin(true, blockTime);

        FastenKafkaPlugin plugin = new FastenKafkaPlugin(false, new Properties(), new Properties(), dummyPlugin, 0, null, null, "", true, timeOut, false, false, "");

        long startTime  = System.currentTimeMillis();
        plugin.consumeWithTimeout("dummy", timeOut, false);
        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;


        assertTrue(duration >= timeOut);
        assertEquals(new TimeoutException().getClass(), dummyPlugin.getPluginError().getClass()); // verify if a TimeoutException
    }

    class DummyPlugin implements KafkaPlugin {

        private final boolean blocking;
        private final long blockTime;
        private Exception error;

        public DummyPlugin(boolean blocking, long blockTime) {
            this.blocking = blocking;
            this.blockTime = blockTime;
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
                if (blocking && blockTime != -1) { //block for blockTime seconds
                    Thread.sleep(blockTime * 1000);
                } else if (blocking) { //block forever
                    while (true) {}
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
        public Exception getPluginError() {
            return this.error;
        }

        @Override
        public void setPluginError(Exception error) {
            this.error = error;
        }

        @Override
        public void freeResource() {

        }
    }
}
