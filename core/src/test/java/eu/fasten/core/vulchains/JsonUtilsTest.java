package eu.fasten.core.vulchains;

import eu.fasten.core.data.FastenURI;
import java.util.LinkedList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonUtilsTest {

    @Test
    public void basicDataStructure() {
        final var input =
            FastenURI.create("fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType");

        String json = JsonUtils.toJson(input);
        FastenURI output = JsonUtils.fromJson(json, FastenURI.class);

        Assertions.assertNotSame(input, output);
        Assertions.assertEquals(input, output);
    }

    @Test
    public void emptyCase() {
        var vc = new VulnerableChain(new LinkedList<>(), new LinkedList<>());

        var actual = JsonUtils.toJson(vc);
        var expected = "{\"vulnerability\":[],\"chain\":[]}";
        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void serializationRoundtrip() {
        var input = new VulnerableChain(new LinkedList<>(), new LinkedList<>());

        var json = JsonUtils.toJson(input);
        var output = JsonUtils.fromJson(json, VulnerableChain.class);
        Assertions.assertEquals(input, output);
    }
    @Test
    public void nestingTypesWorks() {
        final var chains = new LinkedList<FastenURI>();
        chains.add(FastenURI.create("fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType"));
        var vc = new VulnerableChain(new LinkedList<>(), chains);

        var actual = JsonUtils.toJson(vc);
        var expected = "{\"vulnerability\":[],\"chain\":[\"fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType\"]}";
        Assertions.assertEquals(expected, actual);
    }
}
