package eu.fasten.core.legacy;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.primitives.Longs;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

import eu.fasten.core.index.BVGraphSerializer;
import eu.fasten.core.index.LayeredLabelPropagation;
import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.NullInputStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.Transform;


public class PermuteKnowledgeBase {

	private static final Logger LOGGER = LoggerFactory.getLogger(PermuteKnowledgeBase.class);

	@SuppressWarnings("resource")
	public static void main(final String[] args) throws JSAPException, ClassNotFoundException, RocksDBException, IOException {
		final SimpleJSAP jsap = new SimpleJSAP(PermuteKnowledgeBase.class.getName(),
				"Permutes a knowledge base using LLP.",
				new Parameter[] {
						new UnflaggedOption("kb", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The directory of the RocksDB instance containing the knowledge base." ),
						new UnflaggedOption("kbmeta", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The file containing the knowledge base metadata." ),
		});

		final JSAPResult jsapResult = jsap.parse(args);
		if ( jsap.messagePrinted() ) return;

		final String kbDir = jsapResult.getString("kb");
		if (!new File(kbDir).exists()) throw new IllegalArgumentException("No such directory: " + kbDir);

		RocksDB.loadLibrary();
		final ColumnFamilyOptions cfOptions = new ColumnFamilyOptions().setCompressionType(CompressionType.LZ4_COMPRESSION);
		final DBOptions dbOptions = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
		final List<ColumnFamilyDescriptor> cfDescriptors = Arrays.asList(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions), new ColumnFamilyDescriptor(KnowledgeBase.GID2URI, cfOptions), new ColumnFamilyDescriptor(KnowledgeBase.URI2GID, cfOptions));

		final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
		final RocksDB db = RocksDB.open(dbOptions, kbDir, cfDescriptors, columnFamilyHandles);
		final String kbMetadataFilename = jsapResult.getString("kbmeta");
		if (!new File(kbMetadataFilename).exists()) throw new IllegalArgumentException("No such file: " + kbMetadataFilename);
		final KnowledgeBase kb = (KnowledgeBase) BinIO.loadObject(kbMetadataFilename);

		final Kryo kryo = new Kryo();
		kryo.register(BVGraph.class, new BVGraphSerializer(kryo));
		kryo.register(byte[].class);
		kryo.register(InputBitStream.class);
		kryo.register(NullInputStream.class);
		kryo.register(EliasFanoMonotoneLongBigList.class, new JavaSerializer());
		kryo.register(MutableString.class, new FieldSerializer<>(kryo, MutableString.class));
		kryo.register(Properties.class);
		kryo.register(long[].class);
		kryo.register(Long2IntOpenHashMap.class);

		final ProgressLogger pl = new ProgressLogger(LOGGER);

		pl.itemsName = "graphs";
		pl.count = kb.size();
		pl.start("Permuting graphs...");
		final String f = File.createTempFile(PermuteKnowledgeBase.class.getSimpleName(), ".tmpgraph").toString();

		final RocksIterator iterator = db.newIterator(columnFamilyHandles.get(0));

		for(iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
			final byte[] key = iterator.key();
			final long index = Longs.fromByteArray(key);
			final byte[] value = iterator.value();
			final int nInternal = kb.callGraphs.get(index).nInternal;

			final Input input = new Input(value);

			final var graphs = new ImmutableGraph[] { kryo.readObject(input, BVGraph.class), kryo.readObject(input, BVGraph.class) };
			// Skip properties
			kryo.readObject(input, Properties.class);
			kryo.readObject(input, Properties.class);
			final long[] origLID2GID = kryo.readObject(input, long[].class);

			ImmutableGraph graph = graphs[0];
			ImmutableGraph transpose = graphs[1];
			final int numNodes = graph.numNodes();

			final ImmutableGraph symGraph = new ArrayListMutableGraph(Transform.symmetrize(graph)).immutableView();
			final LayeredLabelPropagation clustering = new LayeredLabelPropagation(symGraph, null, Math.min(Runtime.getRuntime().availableProcessors(), 1 + numNodes / 100), 0, false);
			final int[] perm = clustering.computePermutation(LayeredLabelPropagation.DEFAULT_GAMMAS, null);

			Util.invertPermutationInPlace(perm);
			final int[] sorted = new int[numNodes];
			int internal = 0, external = nInternal;
			for (int j = 0; j < numNodes; j++) {
				if (perm[j] < nInternal) sorted[internal++] = perm[j];
				else sorted[external++] = perm[j];
			}
			Util.invertPermutationInPlace(sorted);

			graph = new ArrayListMutableGraph(Transform.map(graph, sorted)).immutableView();
			transpose = new ArrayListMutableGraph(Transform.map(transpose, sorted)).immutableView();

			BVGraph.store(graph, f.toString());
			FileInputStream propertyFile = new FileInputStream(f + BVGraph.PROPERTIES_EXTENSION);
			final Properties graphProperties = new Properties();
			graphProperties.load(propertyFile);
			propertyFile.close();

			final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
			final ByteBufferOutput bbo = new ByteBufferOutput(fbaos);
			kryo.writeObject(bbo, BVGraph.load(f.toString()));

			// Compute LIDs according to the current node renumbering based on BFS
			final long[] LID2GID = new long[origLID2GID.length];

			final Long2IntOpenHashMap GID2LID = new Long2IntOpenHashMap();
			GID2LID.defaultReturnValue(-1);

			for (int x = 0; x < origLID2GID.length; x++) LID2GID[sorted[x]] = origLID2GID[x];
			for (int j = 0; j < origLID2GID.length; j++) GID2LID.put(LID2GID[j], j);

			// Compress, load and serialize transpose graph
			BVGraph.store(transpose, f.toString());
			propertyFile = new FileInputStream(f + BVGraph.PROPERTIES_EXTENSION);
			final Properties transposeProperties = new Properties();
			transposeProperties.load(propertyFile);
			propertyFile.close();

			kryo.writeObject(bbo, BVGraph.load(f.toString()));

			// Write out properties
			kryo.writeObject(bbo, graphProperties);
			kryo.writeObject(bbo, transposeProperties);

			// Write out LID2GID info
			kryo.writeObject(bbo, LID2GID);
			// This could be rebuilt if data were input correctly (i.e., no duplicate internal and external
			// nodes, see assert above).
			kryo.writeObject(bbo, GID2LID);
			bbo.flush();

			// Write to DB
			db.put(columnFamilyHandles.get(0), key, 0, 8, fbaos.array, 0, fbaos.length);
			pl.update();
		}

		db.close();
		pl.done();
		new File(f.toString());
		new File(f.toString() + BVGraph.PROPERTIES_EXTENSION).delete();
		new File(f.toString() + BVGraph.OFFSETS_EXTENSION).delete();
		new File(f.toString() + BVGraph.GRAPH_EXTENSION).delete();
	}

}
