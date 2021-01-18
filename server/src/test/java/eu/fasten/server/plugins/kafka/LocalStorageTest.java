package eu.fasten.server.plugins.kafka;

import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Paths;
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
        File toRemove = new File("src/test/resources/test_pod/");

        String[] entries = toRemove.list();

        for(String s: entries){
            File currentFile = new File(toRemove.getPath(), s);
            currentFile.delete();
        }

        toRemove.delete();
    }

    @Test
    public void test_create_positive() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!"));
        Assertions.assertTrue(localStorage.store("Second message!"));
    }

    @Test
    public void test_exists() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!"));
        Assertions.assertTrue(localStorage.store("Extra message"));
        Assertions.assertTrue(localStorage.exists("A very nice message!"));
        Assertions.assertFalse(localStorage.exists("Doesn't exist"));
    }

    @Test
    public void test_create_negative() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!"));
        Assertions.assertFalse(localStorage.store("A very nice message!"));
        Assertions.assertTrue(localStorage.store("Second message!"));
        Assertions.assertFalse(localStorage.store("Second message!"));
    }

    @Test
    public void test_remove() throws IOException {
        Assertions.assertTrue(localStorage.store("A very nice message!"));
        Assertions.assertTrue(localStorage.exists("A very nice message!"));
        Assertions.assertTrue(localStorage.delete("A very nice message!"));
        Assertions.assertFalse(localStorage.exists("A very nice message!"));
    }

    @Test
    public void test_remove_negative() throws IOException {
        Assertions.assertFalse(localStorage.delete("Non existent message"));
    }

    @Test
    public void test_sha() throws IOException {
        Assertions.assertEquals(localStorage.getSHA1("Same message"),localStorage.getSHA1("Same message"));
        Assertions.assertNotEquals(localStorage.getSHA1("Same message"),localStorage.getSHA1("Different message"));
    }

    @Test
    public void test_clear() throws IOException {
        localStorage.store("Number 1");
        localStorage.store("Number 2");
        Assertions.assertTrue(localStorage.exists("Number 1"));
        Assertions.assertTrue(localStorage.exists("Number 2"));
        localStorage.clear();
        Assertions.assertFalse(localStorage.exists("Number 1"));
        Assertions.assertFalse(localStorage.exists("Number 2"));
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
