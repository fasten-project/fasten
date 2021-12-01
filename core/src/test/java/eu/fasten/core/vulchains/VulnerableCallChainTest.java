package eu.fasten.core.vulchains;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.vulnerability.Vulnerability;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class VulnerableCallChainTest {

    private static VulnerableCallChain sut1;
    private static VulnerableCallChain sut;
    private static VulnerableCallChain sut1Prime;

    @BeforeAll
    public static void setUp() {
        sut = new VulnerableCallChain(new LinkedList<>(), new LinkedList<>());
        sut1 = new VulnerableCallChain(List.of(new Vulnerability("1234")),
            List.of(FastenURI.create("fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType")));
        sut1Prime = new VulnerableCallChain(List.of(new Vulnerability("1234")),
            List.of(FastenURI.create("fasten://mvn!g:a$1.0.0/x/C.m()%2Fjava.lang%2FVoidType")));
    }

    @Test
    void testEquals() {
        Assertions.assertNotEquals(sut, sut1);
        Assertions.assertNotSame(sut1, sut1Prime);
        Assertions.assertEquals(sut1, sut1Prime);
    }

    @Test
    void testHashCode() {
        Assertions.assertEquals(sut1.hashCode(), sut1Prime.hashCode());
        Assertions.assertNotEquals(sut.hashCode(), sut1.hashCode());
    }

    @Test
    void testToString() {
        Assertions.assertTrue(sut.toString().contains("chain=[]\n" +
            "  vulnerability=[]\n" +
            "]"));
        Assertions.assertTrue(sut1.toString().contains("chain={fasten://mvn!g:a$1.0.0/x/C.m()" +
            "%2Fjava.lang%2FVoidType}\n" +
            "  vulnerability={{\"id\":\"1234\",\"purls\":[],\"first_patched_purls\":[],\"references\":[],\"patch_links\":[],\"exploits\":[],\"patches\":[]}}\n" +
            "]"));

    }
}
