package eu.fasten.core.vulchains;

import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.vulnerability.Vulnerability;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;


class VulnerableCallChainRepositoryTest {

    @TempDir
    public static File tempDir;

    private static VulnerableCallChainRepository vulnerableCallChainRepository;
    private static String fullVulString;
    private static HashSet<VulnerableCallChain> vulFromObject;
    private static Set<VulnerableCallChain> firstVulFromString;
    private static File packageFile;

    @Test
    public void setupThrowsExceptionWhenFolderDoesNotExist() {
        Assertions.assertThrows(FileNotFoundException.class, () -> new VulnerableCallChainRepository("a/b/c"));
    }

    @BeforeAll
    static void setup() throws IOException {

        vulnerableCallChainRepository = new VulnerableCallChainRepository(tempDir.getAbsolutePath());


        var firstVulString = "   {\n" +
            "      \"vulnerabilities\":[\n" +
            "         {\n" +
            "            \"id\":\"NIFI-4436\",\n" +
            "            \"purls\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"first_patched_purls\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"references\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"patch_links\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"exploits\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"patches\":[\n" +
            "               \n" +
            "            ]\n" +
            "         }\n" +
            "      ],\n" +
            "      \"chain\":[\n" +
            "         \"fasten://mvn!g:a$1.0.0/merge.simpleImport/Importer.sourceMethod()%2Fjava.lang%2FVoidType\",\n" +
            "         \"fasten://mvn!Imported$1/merge.simpleImport/Imported.targetMethod()%2Fjava.lang%2FVoidType\",\n" +
            "         \"fasten://mvn!Imported$1/merge.simpleImport/Imported.%3Cinit%3E()%2Fjava.lang%2FVoidType\"\n" +
            "      ]\n" +
            "   }";

        var secondVulString = "   {\n" +
            "      \"vulnerabilities\":[\n" +
            "         {\n" +
            "            \"id\":\"NIFI-4436\",\n" +
            "            \"purls\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"first_patched_purls\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"references\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"patch_links\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"exploits\":[\n" +
            "               \n" +
            "            ],\n" +
            "            \"patches\":[\n" +
            "               \n" +
            "            ]\n" +
            "         }\n" +
            "      ],\n" +
            "      \"chain\":[\n" +
            "         \"fasten://mvn!Imported$1/merge.simpleImport/Imported.targetMethod()%2Fjava.lang%2FVoidType\",\n" +
            "         \"fasten://mvn!Imported$1/merge.simpleImport/Imported.%3Cinit%3E()%2Fjava.lang%2FVoidType\"\n" +
            "      ]\n" +
            "   }\n";

        fullVulString = "[\n" + firstVulString +",\n" + secondVulString + "]";
        packageFile = new File(tempDir.getAbsoluteFile() + "/g:a:1.0.0.json");
        FileUtils.write(packageFile, fullVulString, StandardCharsets.UTF_8);
        Type setType = new TypeToken<HashSet<VulnerableCallChain>>(){}.getType();
        firstVulFromString = VulnerableCallChainJsonUtils
            .fromJson("[" + firstVulString +"]", setType);

        VulnerableCallChain vulChainObj1 = new VulnerableCallChain(
            Collections.singletonList(new Vulnerability("NIFI-4436")),
            List.of(FastenURI.create("fasten://mvn!g:a$1.0.0/merge.simpleImport" +
                    "/Importer.sourceMethod()%2Fjava.lang%2FVoidType"),
                FastenURI.create("fasten://mvn!Imported$1/merge.simpleImport" +
                    "/Imported.targetMethod()%2Fjava.lang%2FVoidType"),
                FastenURI.create("fasten://mvn!Imported$1/merge.simpleImport/Imported" +
                    ".%3Cinit%3E()%2Fjava.lang%2FVoidType")));
        VulnerableCallChain vulChainObject2 = new VulnerableCallChain(
            Collections.singletonList(new Vulnerability("NIFI-4436")),
            List.of(FastenURI.create("fasten://mvn!Imported$1/merge.simpleImport" +
                    "/Imported.targetMethod()%2Fjava.lang%2FVoidType"),
                FastenURI.create("fasten://mvn!Imported$1/merge.simpleImport/Imported" +
                    ".%3Cinit%3E()%2Fjava.lang%2FVoidType")
            ));
        vulFromObject = Sets.newHashSet(vulChainObj1, vulChainObject2);
    }

    @Test
    public void store() {
        packageFile.delete();
        vulnerableCallChainRepository.store("g:a", "1.0.0", vulFromObject);
        String actual;
        try {
            actual = Files.readString(Paths.get(tempDir.getAbsolutePath()+"/g:a:1.0.0.json"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        JSONAssert.assertEquals(fullVulString.replace("\n", "").replace(" ", ""), actual, JSONCompareMode.LENIENT);

    }

    @Test
    public void getChainsForPackage() {
        final var actual = vulnerableCallChainRepository.getChainsForPackage("g:a", "1.0.0");
        Assertions.assertEquals(vulFromObject, actual);
    }

    @Test
    void getChainsForModule() {
        var module = FastenURI.create("fasten://mvn!g:a$1.0.0/merge.simpleImport/Importer");
        var actual = vulnerableCallChainRepository.getChainsForModule(module);
        Assertions.assertEquals(firstVulFromString, actual);
    }

    @Test
    void getChainsForCallable() {
        var callable = FastenURI.create("fasten://mvn!g:a$1.0.0/merge.simpleImport/Importer" +
            ".sourceMethod()%2Fjava.lang%2FVoidType");
        var actual = vulnerableCallChainRepository.getChainsForCallable(callable);
        Assertions.assertEquals(firstVulFromString, actual);
    }

    @Test
    void readAndWriteAnArtificialVulDir() throws FileNotFoundException {
        var source = FastenURI.create("fasten://mvn!org.apache.sling:org.apache.sling.xss$2.0.6/org" +
            ".apache.commons.beanutils.converters/ArrayConverter.convertToType(%2Fjava.lang%2FClass,%2Fjava" +
            ".lang%2FObject)%2Fjava.lang%2FObject");

        var target1 = FastenURI.create("fasten://mvn!com.google.guava:guava$15.0/com.google" +
            ".common.collect/AbstractMapBasedMultimap$Itr.next()%2Fjava.lang%2FObject");
        var target2 = FastenURI.create("fasten://mvn!xom:xom$1.2.5/nu.xom.jaxen" +
            ".util/FollowingSiblingAxisIterator.next()%2Fjava.lang%2FObject");
        var target3 = FastenURI.create("fasten://mvn!org.apache.jackrabbit.vault:org.apache.jackrabbit" +
            ".vault$3.1.18/org.apache.jackrabbit.spi.commons" +
            ".batch/ConsolidatingChangeLog$OperationsBackwardWithSentinel.next()%2Fjava.lang%2FObject");

        var vulChain1 = new VulnerableCallChain(List.of(new Vulnerability("CVE-2016-10006")),
            List.of(source, target1));
        var vulChain2 = new VulnerableCallChain(List.of(new Vulnerability("CVE-2016-10006")),
            List.of(source, target2));
        var vulChain3 = new VulnerableCallChain(List.of(new Vulnerability("CVE-2016-10006")),
            List.of(source, target3));
        var vulchains = Set.of(vulChain1, vulChain2, vulChain3);

        VulnerableCallChainRepository vulRepo = new VulnerableCallChainRepository("src/test/resources/vulpath");
        vulRepo.store("org.apache.sling:org.apache.sling.xss","2.0.6", vulchains);

        var pckgVul = vulRepo.getChainsForPackage("org.apache.sling:org.apache.sling.xss","2.0.6");
        var moduleVul = vulRepo.getChainsForModule(FastenURI.create("fasten://mvn!org.apache" +
            ".sling:org.apache.sling.xss$2.0.6/org.apache.commons.beanutils.converters/ArrayConverter"));
        var callableVul = vulRepo.getChainsForCallable(FastenURI.create("fasten://mvn!org.apache" +
            ".sling:org.apache.sling.xss$2.0.6/org.apache.commons.beanutils.converters/" +
            "ArrayConverter.convertToType(%2Fjava.lang%2FClass,%2Fjava" +
            ".lang%2FObject)%2Fjava.lang%2FObject"));

        Assertions.assertEquals(pckgVul, vulchains);
        Assertions.assertEquals(moduleVul, vulchains);
        Assertions.assertEquals(callableVul, vulchains);

    }


}