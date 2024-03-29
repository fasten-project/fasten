package eu.fasten.server.plugins.kafka;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

public class LocalStorageTest {

    private LocalStorage localStorage;

    @BeforeEach
    public void setupStorage() throws URISyntaxException {
        setEnv("POD_INSTANCE_ID", "test_pod");
       localStorage = new LocalStorage(new File("src/test/resources").getAbsolutePath());
    }

    @AfterEach
    public void clearStorage() {
        localStorage = null;
        File toRemove = new File("src/test/resources/test_pod/dummy_topic");

        String[] entries = toRemove.list();

        if (entries != null) {
            for (String s : entries) {
                File currentFile = new File(toRemove.getPath(), s);
                if (currentFile.isDirectory()) {
                    for (String child : currentFile.list()) {
                        new File(currentFile.getPath(), child).delete();
                    }
                }

                currentFile.delete();
            }
        }

        toRemove.delete();
    }

    @Test
    public void testCreatePositive() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.store("Second message!", 1, "dummy_topic"));
    }

    @Test
    public void testExists() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.store("Extra message", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.exists("A very nice message!", 1, "dummy_topic"));
        Assertions.assertFalse(localStorage.exists("Doesn't exist", 1, "dummy_topic"));
    }

    @Test
    public void testCreateNegative() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!", 1, "dummy_topic"));
        Assertions.assertFalse(localStorage.store("A very nice message!", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.store("Second message!", 1, "dummy_topic"));
        Assertions.assertFalse(localStorage.store("Second message!", 1, "dummy_topic"));
    }

    @Test
    public void testAcrossPartitions() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.store("A very nice message!", 2, "dummy_topic"));
        Assertions.assertFalse(localStorage.store("A very nice message!", 1, "dummy_topic"));
        Assertions.assertFalse(localStorage.store("A very nice message!", 2, "dummy_topic"));
        Assertions.assertTrue(localStorage.store("Second message!", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.store("Second message!", 2, "dummy_topic"));
    }

    @Test
    public void testClearPartitions() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.store("A very nice message!", 2, "dummy_topic"));
        Assertions.assertTrue(localStorage.store("A very nice message!", 3, "dummy_topic"));
        Assertions.assertTrue(localStorage.store("A very nice message!", 4, "dummy_topic"));

        localStorage.clear(List.of(1, 2, 3), "dummy_topic");

        Assertions.assertFalse(localStorage.exists("A very nice message!", 1, "dummy_topic"));
        Assertions.assertFalse(localStorage.exists("A very nice message!", 2, "dummy_topic"));
        Assertions.assertFalse(localStorage.exists("A very nice message!", 3, "dummy_topic"));
        Assertions.assertTrue(localStorage.exists("A very nice message!", 4, "dummy_topic"));
    }


    @Test
    public void testRemove() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.exists("A very nice message!", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.delete("A very nice message!", 1, "dummy_topic"));
        Assertions.assertFalse(localStorage.exists("A very nice message!", 1, "dummy_topic"));
    }

    @Test
    public void testRemoveNegative() throws IOException {
        Assertions.assertFalse(localStorage.delete("Non existent message", 1, "dummy_topic"));
    }

    @Test
    public void testSHA() throws IOException {
        Assertions.assertEquals(localStorage.getSHA1("Same message"),localStorage.getSHA1("Same message"));
        Assertions.assertNotEquals(localStorage.getSHA1("Same message"),localStorage.getSHA1("Different message"));
    }

    @Test
    public void testClear() throws IOException {
        localStorage.store("Number 1", 1, "dummy_topic");
        localStorage.store("Number 2", 1, "dummy_topic");
        Assertions.assertTrue(localStorage.exists("Number 1", 1, "dummy_topic"));
        Assertions.assertTrue(localStorage.exists("Number 2", 1, "dummy_topic"));
        localStorage.clear(List.of(1), "dummy_topic");
        Assertions.assertFalse(localStorage.exists("Number 1", 1, "dummy_topic"));
        Assertions.assertFalse(localStorage.exists("Number 2", 1, "dummy_topic"));
    }


    //From: https://stackoverflow.com/questions/19600527/java-program-setting-an-environment-variable
    public static void setEnv(String key, String value) {
        try {
            Map<String, String> env = System.getenv();
            Class<?> cl = env.getClass();
            Field field = cl.getDeclaredField("m");
            field.setAccessible(true);
            Map<String, String> writableEnv = (Map<String, String>) field.get(env);
            writableEnv.put(key, value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set environment variable", e);
        }
    }
}
