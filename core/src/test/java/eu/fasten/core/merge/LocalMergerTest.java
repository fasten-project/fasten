package eu.fasten.core.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Objects;

public class LocalMergerTest {

    private static LocalMerger depSet;

    @BeforeAll
    static void setUp() throws FileNotFoundException {

        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("merge/Imported.json"))
                .getFile());
        JSONTokener tokener = new JSONTokener(new FileReader(file));
        var imported = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));

        file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("merge/Imported.json"))
                .getFile());
        tokener = new JSONTokener(new FileReader(file));
        var importer = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));

        depSet = new LocalMerger(Arrays.asList(imported, importer));
    }

    @Test
    public void mergeAllDepsTest() {
        depSet.mergeAllDeps();
        var mergedDepSetURI = depSet.getAllUris();

        assertEquals(mergedDepSetURI.get(0L),
                "fasten://mvn!Imported$1/merge.simpleImport/Imported.%3Cinit%3E()%2Fjava.lang%2FVoidType");
        assertEquals(mergedDepSetURI.get(1L),
                "fasten://mvn!Imported$1/merge.simpleImport/Imported.targetMethod()%2Fjava.lang%2FVoidType");

    }
}
