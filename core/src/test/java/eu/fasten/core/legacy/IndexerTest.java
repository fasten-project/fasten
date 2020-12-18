package eu.fasten.core.legacy;


import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDBException;

import eu.fasten.core.data.RevisionCallGraph;
import eu.fasten.core.legacy.Indexer;
import eu.fasten.core.legacy.KnowledgeBase;
import eu.fasten.core.legacy.KnowledgeBase.Node;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

@Disabled("No longer in use")
public class IndexerTest {

    final String[] JSON_SPECS = {
            "{\n" +
                    "    \"product\": \"org.slf4j.slf4j-api\",\n" +
                    "    \"forge\": \"mvn\",\n" +
                    "    \"generator\": \"OPAL\",\n" +
                    "    \"depset\": [],\n" +
                    "    \"version\": \"1.0\",\n" +
                    "    \"cha\": {\n" +
                    "        \"/org.slf4j.helpers/FormattingTuple\": {\n" +
                    "            \"methods\": {\n" +
                    "                \"0\": \"/org.slf4j.helpers/FormattingTuple.getThrowable()%2Fjava.lang%2FThrowable\",\n" +
                    "                \"1\": \"/org.slf4j.helpers/FormattingTuple.%3Cinit%3E()%2Fjava.lang%2FVoidType\",\n" +
                    "                \"2\": \"/org.slf4j.helpers/FormattingTuple.FormattingTuple(%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType\",\n" +
                    "                \"3\": \"/org.slf4j.helpers/FormattingTuple.FormattingTuple(%2Fjava.lang%2FString,%2Fjava.lang%2FObject%25255B%25255D,%2Fjava.lang%2FThrowable)%2Fjava.lang%2FVoidType\",\n" +
                    "            },\n" +
                    "            \"superInterfaces\": [],\n" +
                    "            \"sourceFile\": \"FormattingTuple.java\",\n" +
                    "            \"superClasses\": [\"/java.lang/Object\"]\n" +
                    "        },\n" +
                    "        \"/org.slf4j/ILoggerFactory\": {\n" +
                    "            \"methods\": {\"9\": \"/org.slf4j/ILoggerFactory.getLogger(%2Fjava.lang%2FString)Logger\"},\n" +
                    "            \"superInterfaces\": [],\n" +
                    "            \"sourceFile\": \"ILoggerFactory.java\",\n" +
                    "            \"superClasses\": [\"/java.lang/Object\"]\n" +
                    "        },\n" +
                    "        \"/org.slf4j.event/SubstituteLoggingEvent\": {\n" +
                    "            \"methods\": {\n" +
                    "                \"4\": \"/org.slf4j.event/SubstituteLoggingEvent.setMessage(%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType\",\n" +
                    "                \"5\": \"/org.slf4j.event/SubstituteLoggingEvent.getMarker()%2Forg.slf4j%2FMarker\",\n" +
                    "            },\n" +
                    "            \"superInterfaces\": [],\n" +
                    "            \"sourceFile\": \"SubstituteLoggingEvent.java\",\n" +
                    "            \"superClasses\": [\"/java.lang/Object\"]\n" +
                    "        }\n" +
                    "    },\n" +
                    "    \"graph\": {\n" +
                    "        \"internalCalls\": [\n" +
                    "            [\n" +
                    "                2,\n" +
                    "                1\n" +
                    "            ],\n" +
                    "            [\n" +
                    "                3,\n" +
                    "                5\n" +
                    "            ],\n" +
                    "            [\n" +
                    "                0,\n" +
                    "                1\n" +
                    "            ]\n" +
                    "	],\n" +
                    "	\"externalCalls\": [\n" +
                    "            [\n" +
                    "                \"2\",\n" +
                    "                \"///java.lang/StringBuilder.append(String)StringBuilder\",\n" +
                    "                {\"invokevirtual\": \"1\"}\n" +
                    "            ],\n" +
                    "            [\n" +
                    "                \"0\",\n" +
                    "                \"///java.lang/StringBuilder.append(String)StringBuilder\",\n" +
                    "                {\"invokevirtual\": \"1\"}\n" +
                    "            ]\n" +
                    "	]\n" +
                    "    },\n" +
                    "    \"timestamp\": 0\n" +
                    "}\n",
            "{\n" +
                    "    \"product\": \"org.slf4j2.slf4j-api\",\n" +
                    "    \"forge\": \"mvn\",\n" +
                    "    \"generator\": \"OPAL\",\n" +
                    "    \"depset\": [],\n" +
                    "    \"version\": \"1.0\",\n" +
                    "    \"cha\": {\n" +
                    "        \"/org.slf4j2.helpers/FormattingTuple\": {\n" +
                    "            \"methods\": {\n" +
                    "                \"0\": \"/org.slf4j2.helpers/FormattingTuple.getThrowable()%2Fjava.lang%2FThrowable\",\n" +
                    "                \"1\": \"/org.slf4j2.helpers/FormattingTuple.%3Cinit%3E()%2Fjava.lang%2FVoidType\",\n" +
                    "                \"2\": \"/org.slf4j2.helpers/FormattingTuple.FormattingTuple(%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType\",\n" +
                    "                \"3\": \"/org.slf4j2.helpers/FormattingTuple.FormattingTuple(%2Fjava.lang%2FString,%2Fjava.lang%2FObject%25255B%25255D,%2Fjava.lang%2FThrowable)%2Fjava.lang%2FVoidType\",\n" +
                    "            },\n" +
                    "            \"superInterfaces\": [],\n" +
                    "            \"sourceFile\": \"FormattingTuple.java\",\n" +
                    "            \"superClasses\": [\"/java.lang/Object\"]\n" +
                    "        },\n" +
                    "        \"/org.slf4j2/ILoggerFactory\": {\n" +
                    "            \"methods\": {\"9\": \"/org.slf4j2/ILoggerFactory.getLogger(%2Fjava.lang%2FString)Logger\"},\n" +
                    "            \"superInterfaces\": [],\n" +
                    "            \"sourceFile\": \"ILoggerFactory.java\",\n" +
                    "            \"superClasses\": [\"/java.lang/Object\"]\n" +
                    "        },\n" +
                    "        \"/org.slf4j2.event/SubstituteLoggingEvent\": {\n" +
                    "            \"methods\": {\n" +
                    "                \"4\": \"/org.slf4j2.event/SubstituteLoggingEvent.setMessage(%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType\",\n" +
                    "                \"5\": \"/org.slf4j2.event/SubstituteLoggingEvent.getMarker()%2Forg.slf4j2%2FMarker\",\n" +
                    "            },\n" +
                    "            \"superInterfaces\": [],\n" +
                    "            \"sourceFile\": \"SubstituteLoggingEvent.java\",\n" +
                    "            \"superClasses\": [\"/java.lang/Object\"]\n" +
                    "        }\n" +
                    "    },\n" +
                    "    \"graph\": {\n" +
                    "        \"internalCalls\": [\n" +
                    "            [\n" +
                    "                2,\n" +
                    "                1\n" +
                    "            ],\n" +
                    "            [\n" +
                    "                3,\n" +
                    "                5\n" +
                    "            ],\n" +
                    "            [\n" +
                    "                0,\n" +
                    "                1\n" +
                    "            ]\n" +
                    "	],\n" +
                    "	\"externalCalls\": [\n" +
                    "            [\n" +
                    "                \"2\",\n" +
                    "                \"///org.slf4j.helpers/FormattingTuple.getThrowable()%2Fjava.lang%2FThrowable\",\n" +
                    "                {\"invokevirtual\": \"1\"}\n" +
                    "            ],\n" +
                    "            [\n" +
                    "                \"0\",\n" +
                    "                \"///org.slf4j/ILoggerFactory.getLogger(%2Fjava.lang%2FString)Logger\",\n" +
                    "                {\"invokevirtual\": \"1\"}\n" +
                    "            ]\n" +
                    "	]\n" +
                    "    },\n" +
                    "    \"timestamp\": 0\n" +
                    "}\n"

    };

    public void testKnowledgeBase(final String[] jsonSpec) throws JSONException, IOException, RocksDBException, ClassNotFoundException {
        final Path kbDir = Files.createTempDirectory(Indexer.class.getSimpleName());
        final String meta = Files.createTempFile(Indexer.class.getSimpleName(), "meta").getFileName().toString();
        FileUtils.deleteDirectory(kbDir.toFile());
        FileUtils.deleteQuietly(new File(meta));

        KnowledgeBase kb = KnowledgeBase.getInstance(kbDir.toString(), meta, false);

        for (int index = 0; index < jsonSpec.length; index++)
            kb.add(new RevisionCallGraph(new JSONObject(jsonSpec[index])), index);

        for (int pass = 0; pass < 2; pass++) {
            for (final var entry : kb.callGraphs.long2ObjectEntrySet()) {
                final eu.fasten.core.legacy.KnowledgeBase.CallGraph callGraph = entry.getValue();
                for (final long gid : callGraph.callGraphData().nodes())
                    if (!(callGraph.callGraphData().isExternal(gid))) {
                        final Node node = kb.new Node(gid, entry.getLongKey());
                        final long signature = node.signature();
                        ObjectLinkedOpenHashSet<Node> reaches;
                        ObjectLinkedOpenHashSet<Node> coreaches;
                        LongSet reachesSig;
                        LongSet coreachesSig;

                        reaches = kb.reaches(node);
                        reachesSig = kb.reaches(signature);

                        for (final Node reached : reaches) {
                            coreaches = kb.coreaches(reached);
                            assertTrue(coreaches.contains(node));
                        }
                        for (final long reachedSig : reachesSig) {
                            coreachesSig = kb.coreaches(reachedSig);
                            assertTrue(coreachesSig.contains(signature));
                        }

                        coreaches = kb.coreaches(node);
                        coreachesSig = kb.coreaches(signature);
                        for (final Node reached : coreaches) {
                            reaches = kb.coreaches(reached);
                            assertTrue(coreaches.contains(node));
                        }
                        for (final long reached : coreachesSig) {
                            reachesSig = kb.coreaches(reached);
                            assertTrue(coreachesSig.contains(signature));
                        }
                    }
            }
            kb.close();
            kb = KnowledgeBase.getInstance(kbDir.toString(), meta, false);
        }
        kb.close();
        FileUtils.deleteDirectory(kbDir.toFile());
        FileUtils.deleteQuietly(new File(meta));
    }

    @Test
    public void testSmallIndex() throws JSONException, IOException, RocksDBException, URISyntaxException, ClassNotFoundException {
        testKnowledgeBase(JSON_SPECS);
    }

    @Test
    public void testMediumIndex() throws JSONException, IOException, RocksDBException, URISyntaxException, ClassNotFoundException {
        final ObjectArrayList<String> jsonSpecs = new ObjectArrayList<>();
        jsonSpecs.addAll(Arrays.asList(JSON_SPECS));
        for (final String s : JSON_SPECS) jsonSpecs.add(s.replaceAll("1\\.0", "2.0"));
        for (final String s : JSON_SPECS) jsonSpecs.add(s.replaceAll("1\\.0", "3.0"));
        for (final String s : JSON_SPECS) jsonSpecs.add(s.replaceAll("1\\.0", "4.0"));
        testKnowledgeBase(jsonSpecs.toArray(new String[0]));
    }

    @Test
    public void testLargeIndex() throws JSONException, IOException, RocksDBException, URISyntaxException, ClassNotFoundException {
        final ObjectArrayList<String> jsonSpecs = new ObjectArrayList<>();
        jsonSpecs.addAll(Arrays.asList(JSON_SPECS));
        for (int i = 2; i < 10; i++)
            for (final String s : JSON_SPECS) jsonSpecs.add(s.replaceAll("1\\.0", i + ".0"));

        testKnowledgeBase(jsonSpecs.toArray(new String[0]));
    }
}
