package eu.fasten.core.index;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import eu.fasten.core.data.JSONCallGraph;
import eu.fasten.core.index.InMemoryIndexer.CallGraph;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;

public class InMemoryIndexerTest {

	final String[] jsonSpecs = {
			"{\n" + "\"forge\": \"f\",\n" + "\"product\": \"graph-0\",\n" + "\"version\": \"1.0\",\n" + "\"timestamp\": \"0\",\n" + "\"depset\": [],\n" + "\"graph\": [\n" + "          [ \"/p0/A.f0()v\", \"//-\" ],\n" + "          [ \"/p0/A.f1()v\", \"//-\" ],\n" + "          [ \"/p0/A.f2()v\", \"//-\" ],\n" + "          [ \"/p0/A.f3()v\", \"//-\" ],\n" + "          [ \"/p0/A.f4()v\", \"//-\" ],\n" + "          [ \"/p0/A.f5()v\", \"//-\" ],\n" + "          [ \"/p0/A.f6()v\", \"//-\" ],\n" + "          [ \"/p0/A.f7()v\", \"//-\" ],\n" + "          [ \"/p0/A.f8()v\", \"//-\" ],\n" + "          [ \"/p0/A.f9()v\", \"//-\" ],\n" + "          [ \"/p0/A.f10()v\", \"/p0/A.f8()v\" ],\n" + "          [ \"/p0/A.f10()v\", \"/p0/A.f9()v\" ],\n" + "          [ \"/p0/A.f11()v\", \"/p0/A.f1()v\" ],\n" + "          ]" + "}",
			"{\n" + "\"forge\": \"f\",\n" + "\"product\": \"graph-1\",\n" + "\"version\": \"1.0\",\n" + "\"timestamp\": \"0\",\n" + "\"depset\": [],\n" + "\"graph\": [\n" + "          [ \"/p1/A.f0()v\", \"//-\" ],\n" + "          [ \"/p1/A.f1()v\", \"//-\" ],\n" + "          [ \"/p1/A.f2()v\", \"//-\" ],\n" + "          [ \"/p1/A.f3()v\", \"//-\" ],\n" + "          [ \"/p1/A.f4()v\", \"//-\" ],\n" + "          [ \"/p1/A.f5()v\", \"//-\" ],\n" + "          [ \"/p1/A.f6()v\", \"//-\" ],\n" + "          [ \"/p1/A.f7()v\", \"//-\" ],\n" + "          [ \"/p1/A.f8()v\", \"//-\" ],\n" + "          [ \"/p1/A.f9()v\", \"//-\" ],\n" + "          [ \"/p1/A.f10()v\", \"/p1/A.f0()v\" ],\n" + "          [ \"/p1/A.f10()v\", \"/p1/A.f3()v\" ],\n" + "          [ \"/p1/A.f10()v\", \"/p1/A.f5()v\" ],\n" + "          [ \"/p1/A.f11()v\", \"/p1/A.f1()v\" ],\n" + "          ]\n" + "}",
			"{\n" + "\"forge\": \"f\",\n" + "\"product\": \"graph-2\",\n" + "\"version\": \"1.0\",\n" + "\"timestamp\": \"0\",\n" + "\"depset\": [{ \"forge\": \"f\", \"product\": \"graph-0\", \"constraints\": [\"[1.0]\"] }, { \"forge\": \"f\", \"product\": \"graph-1\", \"constraints\": [\"[1.0]\"] }],\n" + "\"graph\": [\n" + "          [ \"/p2/A.f1()v\", \"//graph-0/p0/A.f3()v\" ],\n" + "          [ \"/p2/A.f7()v\", \"//graph-0/p0/A.f6()v\" ],\n" + "          [ \"/p2/A.f2()v\", \"//graph-0/p0/A.f0()v\" ],\n" + "          [ \"/p2/A.f0()v\", \"//graph-1/p1/A.f2()v\" ],\n" + "          [ \"/p2/A.f5()v\", \"//graph-1/p1/A.f7()v\" ],\n" + "          [ \"/p2/A.f10()v\", \"//graph-1/p1/A.f8()v\" ],\n" + "          [ \"/p2/A.f3()v\", \"//-\" ],\n" + "          [ \"/p2/A.f4()v\", \"//-\" ],\n" + "          [ \"/p2/A.f6()v\", \"//-\" ],\n" + "          [ \"/p2/A.f8()v\", \"//-\" ],\n" + "          [ \"/p2/A.f9()v\", \"//-\" ],\n" + "          [ \"/p2/A.f10()v\", \"/p2/A.f1()v\" ],\n" + "          [ \"/p2/A.f10()v\", \"/p2/A.f2()v\" ],\n" + "          [ \"/p2/A.f10()v\", \"/p2/A.f7()v\" ],\n" + "          [ \"/p2/A.f10()v\", \"/p2/A.f8()v\" ],\n" + "          [ \"/p2/A.f11()v\", \"/p2/A.f0()v\" ],\n" + "          [ \"/p2/A.f11()v\", \"/p2/A.f4()v\" ],\n" + "          [ \"/p2/A.f11()v\", \"/p2/A.f9()v\" ],\n" + "          ]\n" + "}",
			"{\n" + "\"forge\": \"f\",\n" + "\"product\": \"graph-3\",\n" + "\"version\": \"1.0\",\n" + "\"timestamp\": \"0\",\n" + "\"depset\": [{ \"forge\": \"f\", \"product\": \"graph-0\", \"constraints\": [\"[1.0]\"] }, { \"forge\": \"f\", \"product\": \"graph-2\", \"constraints\": [\"[1.0]\"] }, { \"forge\": \"f\", \"product\": \"graph-1\", \"constraints\": [\"[1.0]\"] }],\n" + "\"graph\": [\n" + "          [ \"/p3/A.f8()v\", \"//graph-1/p1/A.f7()v\" ],\n" + "          [ \"/p3/A.f5()v\", \"//graph-0/p0/A.f5()v\" ],\n" + "          [ \"/p3/A.f10()v\", \"//graph-0/p0/A.f7()v\" ],\n" + "          [ \"/p3/A.f10()v\", \"//graph-0/p0/A.f8()v\" ],\n" + "          [ \"/p3/A.f8()v\", \"//graph-0/p0/A.f6()v\" ],\n" + "          [ \"/p3/A.f5()v\", \"//graph-2/p2/A.f3()v\" ],\n" + "          [ \"/p3/A.f11()v\", \"//graph-2/p2/A.f6()v\" ],\n" + "          [ \"/p3/A.f0()v\", \"//-\" ],\n" + "          [ \"/p3/A.f1()v\", \"//-\" ],\n" + "          [ \"/p3/A.f2()v\", \"//-\" ],\n" + "          [ \"/p3/A.f3()v\", \"//-\" ],\n" + "          [ \"/p3/A.f4()v\", \"//-\" ],\n" + "          [ \"/p3/A.f6()v\", \"//-\" ],\n" + "          [ \"/p3/A.f7()v\", \"//-\" ],\n" + "          [ \"/p3/A.f9()v\", \"//-\" ],\n" + "          [ \"/p3/A.f10()v\", \"/p3/A.f4()v\" ],\n" + "          [ \"/p3/A.f10()v\", \"/p3/A.f8()v\" ],\n" + "          [ \"/p3/A.f11()v\", \"/p3/A.f1()v\" ],\n" + "          [ \"/p3/A.f11()v\", \"/p3/A.f4()v\" ],\n" + "          [ \"/p3/A.f11()v\", \"/p3/A.f5()v\" ],\n" + "          ]\n" + "}" };

	@Test
	public void testIndex() throws JSONException, IOException, RocksDBException, URISyntaxException {
		RocksDB.loadLibrary();
		final Options options = new Options();
		options.setCreateIfMissing(true);
		final Path rocksDb = Files.createTempDirectory(InMemoryIndexer.class.getSimpleName());
		final RocksDB db = RocksDB.open(options, rocksDb.toString());

		final InMemoryIndexer inMemoryIndexer = new InMemoryIndexer(db);
		for (int index = 0; index < 4; index++)
			inMemoryIndexer.add(new JSONCallGraph(new JSONObject(jsonSpecs[index]), false), index);

		for(final var entry : inMemoryIndexer.callGraphs.long2ObjectEntrySet()) {
			System.err.println("================ Testing graph " + entry.getLongKey() + "=========================");
			final CallGraph callGraph = entry.getValue();
			System.err.println(callGraph.graphs()[0]);
			System.err.println(callGraph.graphs()[1]);
			for(int i = 0; i < callGraph.nInternal; i++) {
				final long[] node = new long[] { callGraph.LID2GID[i], entry.getLongKey() };
				System.err.println("************* Testing node " + inMemoryIndexer.toLIDString(node));
				ObjectLinkedOpenCustomHashSet<long[]> reaches, coreaches;

				reaches = inMemoryIndexer.reaches(node);
				System.err.print("Reaches : " + inMemoryIndexer.toLIDString(reaches));

				for(final long[] reached: reaches) {
					System.err.println("Testing coreachability from " + inMemoryIndexer.toLIDString(reached));
					coreaches = inMemoryIndexer.coreaches(reached);
					System.err.println("Coreaches :" + inMemoryIndexer.toLIDString(coreaches) );

					assertTrue(coreaches.contains(node));
				}

				coreaches = inMemoryIndexer.coreaches(node);
				for(final long[] reached: reaches) {
					reaches = inMemoryIndexer.coreaches(reached);
					assertTrue(coreaches.contains(node));
				}
			}
		}
		db.close();
		FileUtils.deleteDirectory(rocksDb.toFile());
	}

}
