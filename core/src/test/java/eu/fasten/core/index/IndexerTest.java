package eu.fasten.core.index;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.rocksdb.RocksDBException;

import eu.fasten.core.data.KnowledgeBase;
import eu.fasten.core.data.KnowledgeBase.Node;
import eu.fasten.core.data.RevisionCallGraph;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class IndexerTest {

	final String[] JSON_SPECS = {
			"{\n" + "\"forge\": \"f\",\n" + "\"product\": \"graph-0\",\n" + "\"version\": \"1.0\",\n" + "\"timestamp\": \"0\",\n" + "\"depset\": [],\n" + "\"graph\": [\n" + "          [ \"/p0/A.f0()v\", \"//-\" ],\n" + "          [ \"/p0/A.f1()v\", \"//-\" ],\n" + "          [ \"/p0/A.f2()v\", \"//-\" ],\n" + "          [ \"/p0/A.f3()v\", \"//-\" ],\n" + "          [ \"/p0/A.f4()v\", \"//-\" ],\n" + "          [ \"/p0/A.f5()v\", \"//-\" ],\n" + "          [ \"/p0/A.f6()v\", \"//-\" ],\n" + "          [ \"/p0/A.f7()v\", \"//-\" ],\n" + "          [ \"/p0/A.f8()v\", \"//-\" ],\n" + "          [ \"/p0/A.f9()v\", \"//-\" ],\n" + "          [ \"/p0/A.f10()v\", \"/p0/A.f8()v\" ],\n" + "          [ \"/p0/A.f10()v\", \"/p0/A.f9()v\" ],\n" + "          [ \"/p0/A.f11()v\", \"/p0/A.f1()v\" ],\n" + "          ]" + "}",
			"{\n" + "\"forge\": \"f\",\n" + "\"product\": \"graph-1\",\n" + "\"version\": \"1.0\",\n" + "\"timestamp\": \"0\",\n" + "\"depset\": [],\n" + "\"graph\": [\n" + "          [ \"/p1/A.f0()v\", \"//-\" ],\n" + "          [ \"/p1/A.f1()v\", \"//-\" ],\n" + "          [ \"/p1/A.f2()v\", \"//-\" ],\n" + "          [ \"/p1/A.f3()v\", \"//-\" ],\n" + "          [ \"/p1/A.f4()v\", \"//-\" ],\n" + "          [ \"/p1/A.f5()v\", \"//-\" ],\n" + "          [ \"/p1/A.f6()v\", \"//-\" ],\n" + "          [ \"/p1/A.f7()v\", \"//-\" ],\n" + "          [ \"/p1/A.f8()v\", \"//-\" ],\n" + "          [ \"/p1/A.f9()v\", \"//-\" ],\n" + "          [ \"/p1/A.f10()v\", \"/p1/A.f0()v\" ],\n" + "          [ \"/p1/A.f10()v\", \"/p1/A.f3()v\" ],\n" + "          [ \"/p1/A.f10()v\", \"/p1/A.f5()v\" ],\n" + "          [ \"/p1/A.f11()v\", \"/p1/A.f1()v\" ],\n" + "          ]\n" + "}",
			"{\n" + "\"forge\": \"f\",\n" + "\"product\": \"graph-2\",\n" + "\"version\": \"1.0\",\n" + "\"timestamp\": \"0\",\n" + "\"depset\": [[{ \"forge\": \"f\", \"product\": \"graph-0\", \"constraints\": [\"[1.0]\"] }], [{ \"forge\": \"f\", \"product\": \"graph-1\", \"constraints\": [\"[1.0]\"] }]],\n" + "\"graph\": [\n" + "          [ \"/p2/A.f1()v\", \"//graph-0/p0/A.f3()v\" ],\n" + "          [ \"/p2/A.f7()v\", \"//graph-0/p0/A.f6()v\" ],\n" + "          [ \"/p2/A.f2()v\", \"//graph-0/p0/A.f0()v\" ],\n" + "          [ \"/p2/A.f0()v\", \"//graph-1/p1/A.f2()v\" ],\n" + "          [ \"/p2/A.f5()v\", \"//graph-1/p1/A.f7()v\" ],\n" + "          [ \"/p2/A.f10()v\", \"//graph-1/p1/A.f8()v\" ],\n" + "          [ \"/p2/A.f3()v\", \"//-\" ],\n" + "          [ \"/p2/A.f4()v\", \"//-\" ],\n" + "          [ \"/p2/A.f6()v\", \"//-\" ],\n" + "          [ \"/p2/A.f8()v\", \"//-\" ],\n" + "          [ \"/p2/A.f9()v\", \"//-\" ],\n" + "          [ \"/p2/A.f10()v\", \"/p2/A.f1()v\" ],\n" + "          [ \"/p2/A.f10()v\", \"/p2/A.f2()v\" ],\n" + "          [ \"/p2/A.f10()v\", \"/p2/A.f7()v\" ],\n" + "          [ \"/p2/A.f10()v\", \"/p2/A.f8()v\" ],\n" + "          [ \"/p2/A.f11()v\", \"/p2/A.f0()v\" ],\n" + "          [ \"/p2/A.f11()v\", \"/p2/A.f4()v\" ],\n" + "          [ \"/p2/A.f11()v\", \"/p2/A.f9()v\" ],\n" + "          ]\n" + "}",
			"{\n" + "\"forge\": \"f\",\n" + "\"product\": \"graph-3\",\n" + "\"version\": \"1.0\",\n" + "\"timestamp\": \"0\",\n" + "\"depset\": [[{ \"forge\": \"f\", \"product\": \"graph-0\", \"constraints\": [\"[1.0]\"] }], [{ \"forge\": \"f\", \"product\": \"graph-2\", \"constraints\": [\"[1.0]\"] }], [{ \"forge\": \"f\", \"product\": \"graph-1\", \"constraints\": [\"[1.0]\"] }]],\n" + "\"graph\": [\n" + "          [ \"/p3/A.f8()v\", \"//graph-1/p1/A.f7()v\" ],\n" + "          [ \"/p3/A.f5()v\", \"//graph-0/p0/A.f5()v\" ],\n" + "          [ \"/p3/A.f10()v\", \"//graph-0/p0/A.f7()v\" ],\n" + "          [ \"/p3/A.f10()v\", \"//graph-0/p0/A.f8()v\" ],\n" + "          [ \"/p3/A.f8()v\", \"//graph-0/p0/A.f6()v\" ],\n" + "          [ \"/p3/A.f5()v\", \"//graph-2/p2/A.f3()v\" ],\n" + "          [ \"/p3/A.f11()v\", \"//graph-2/p2/A.f6()v\" ],\n" + "          [ \"/p3/A.f0()v\", \"//-\" ],\n" + "          [ \"/p3/A.f1()v\", \"//-\" ],\n" + "          [ \"/p3/A.f2()v\", \"//-\" ],\n" + "          [ \"/p3/A.f3()v\", \"//-\" ],\n" + "          [ \"/p3/A.f4()v\", \"//-\" ],\n" + "          [ \"/p3/A.f6()v\", \"//-\" ],\n" + "          [ \"/p3/A.f7()v\", \"//-\" ],\n" + "          [ \"/p3/A.f9()v\", \"//-\" ],\n" + "          [ \"/p3/A.f10()v\", \"/p3/A.f4()v\" ],\n" + "          [ \"/p3/A.f10()v\", \"/p3/A.f8()v\" ],\n" + "          [ \"/p3/A.f11()v\", \"/p3/A.f1()v\" ],\n" + "          [ \"/p3/A.f11()v\", \"/p3/A.f4()v\" ],\n" + "          [ \"/p3/A.f11()v\", \"/p3/A.f5()v\" ],\n" + "          ]\n" + "}" };

	public void testKnowledgeBase(final String[] jsonSpec) throws JSONException, IOException, RocksDBException, URISyntaxException, ClassNotFoundException {
		final Path kbDir = Files.createTempDirectory(Indexer.class.getSimpleName());
		KnowledgeBase kb = KnowledgeBase.getInstance(kbDir.toString());

		for (int index = 0; index < jsonSpec.length; index++)
			kb.add(new RevisionCallGraph(new JSONObject(jsonSpec[index]), false), index);

		for(int pass = 0; pass < 2; pass++) {
			for(final var entry : kb.callGraphs.long2ObjectEntrySet()) {
				final eu.fasten.core.data.KnowledgeBase.CallGraph callGraph = entry.getValue();
				for(int i = 0; i < callGraph.nInternal; i++) {
					final Node node = kb.new Node(callGraph.LID2GID[i], entry.getLongKey());
					ObjectOpenHashSet<Node> reaches, coreaches;

					reaches = kb.reaches(node);
					for(final Node reached: reaches) {
						coreaches = kb.coreaches(reached);
						assertTrue(coreaches.contains(node));
					}

					coreaches = kb.coreaches(node);
					for(final Node reached: coreaches) {
						reaches = kb.coreaches(reached);
						assertTrue(coreaches.contains(node));
					}
				}
			}
			kb.close();
			kb = KnowledgeBase.getInstance(kbDir.toString());
		}


		FileUtils.deleteDirectory(kbDir.toFile());
	}

	@Test
	public void testSmallIndex() throws JSONException, IOException, RocksDBException, URISyntaxException, ClassNotFoundException {
		testKnowledgeBase(JSON_SPECS);
	}

	@Test
	public void testMediumIndex() throws JSONException, IOException, RocksDBException, URISyntaxException, ClassNotFoundException {
		final ObjectArrayList<String> jsonSpecs = new ObjectArrayList<>();
		jsonSpecs.addAll(Arrays.asList(JSON_SPECS));
		for(final String s : JSON_SPECS) jsonSpecs.add(s.replaceAll("1\\.0", "2.0"));
		for(final String s : JSON_SPECS) jsonSpecs.add(s.replaceAll("1\\.0", "3.0"));
		for(final String s : JSON_SPECS) jsonSpecs.add(s.replaceAll("1\\.0", "4.0"));
		testKnowledgeBase(jsonSpecs.toArray(new String[0]));
	}


	@Test
	public void testLargeIndex() throws JSONException, IOException, RocksDBException, URISyntaxException, ClassNotFoundException {
		final ObjectArrayList<String> jsonSpecs = new ObjectArrayList<>();
		jsonSpecs.addAll(Arrays.asList(JSON_SPECS));
		for(int i = 2; i < 10; i++)
			for(final String s : JSON_SPECS) jsonSpecs.add(s.replaceAll("1\\.0", i + ".0"));

		testKnowledgeBase(jsonSpecs.toArray(new String[0]));
	}
}
