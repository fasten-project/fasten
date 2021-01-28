package eu.fasten.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FastenUriUtilsTest {

    @Test
    void testGenerateFullFastenUriSuccess() {

        var forge = "forge";
        var pkg = "name";
        var version = "1.0";
        var partial = "/partial";

        var expectedFullUri = "fasten://forge!name$1.0/partial";

        var actual = FastenUriUtils.generateFullFastenUri(forge, pkg, version, partial);

        assertEquals(expectedFullUri, actual);
    }

    @Test
    void testParseFullFastenUriSuccess() {

        var fullUri = "fasten://forge!name$1.0/partial";

        var expectedForge = "forge";
        var expectedPackage = "name";
        var expectedVersion = "1.0";
        var expectedPartial = "/partial";

        var actual = FastenUriUtils.parseFullFastenUri(fullUri);

        assertEquals(expectedForge, actual.get(0));
        assertEquals(expectedPackage, actual.get(1));
        assertEquals(expectedVersion, actual.get(2));
        assertEquals(expectedPartial, actual.get(3));
    }

    @Test
    void testParsePartialFastenUriSuccess() {

        var partialUri = "/junit.awtui/AboutDialog.<init>(/java.awt/Frame)/java.lang/VoidType";

        var expectedNamespace = "junit.awtui";
        var expectedClass = "AboutDialog";
        var expectedMethod = "init";
        var expectedArgs = "/java.awt/Frame";
        var expectedReturnType = "/java.lang/VoidType";

        var actual = FastenUriUtils.parsePartialFastenUri(partialUri);

        assertEquals(expectedNamespace, actual.get(0));
        assertEquals(expectedClass, actual.get(1));
        assertEquals(expectedMethod, actual.get(2));
        assertEquals(expectedArgs, actual.get(3));
        assertEquals(expectedReturnType, actual.get(4));
    }

    @Test
    void testParsePartialFastenUriFailedModule() {
        var partialUri = "/junit.awtui/AboutDialog<init>(/java.awt/Frame)/java.lang/VoidType";  // missing leading `.` after class name.
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            FastenUriUtils.parsePartialFastenUri(partialUri);
        });

        String expectedMessage = "Invalid partial FASTEN URI: module was not found.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    void testParsePartialFastenUriFailedMethod() {
        var partialUri = "/junit.awtui/AboutDialog.<init(/java.awt/Frame)/java.lang/VoidType"; // missing trailing `>`.
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            FastenUriUtils.parsePartialFastenUri(partialUri);
        });

        String expectedMessage = "Invalid partial FASTEN URI: method name was not found.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    void testParsePartialFastenUriFailedMethodArgs() {
        var partialUri = "/junit.awtui/AboutDialog.<init>/java.awt/Frame)/java.lang/VoidType"; // missing leading `(`.
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            FastenUriUtils.parsePartialFastenUri(partialUri);
        });

        String expectedMessage = "Invalid partial FASTEN URI: method's arguments were not found.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

    @Test
    void testParsePartialFastenUriFailedMethodReturnT() {
        var partialUri = "/junit.awtui/AboutDialog.<init>(/java.awt/Frame)"; // missing return type.
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            FastenUriUtils.parsePartialFastenUri(partialUri);
        });

        String expectedMessage = "Invalid partial FASTEN URI: method's return type was not found.";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }

}
