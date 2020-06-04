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

package eu.fasten.core.data.graphdb;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferOutput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.google.common.primitives.Longs;

import eu.fasten.core.data.GOV3LongFunction;
import eu.fasten.core.data.KnowledgeBase;
import eu.fasten.core.index.BVGraphSerializer;
import eu.fasten.core.index.LayeredLabelPropagation;
import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.NullInputStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.Transform;

public class RocksDao implements Closeable {

    private final RocksDB rocksDb;
    private final ColumnFamilyHandle defaultHandle;
    private Kryo kryo;
    private final Logger logger = LoggerFactory.getLogger(RocksDao.class.getName());

    /**
     * Constructor of RocksDao (Database Access Object).
     *
     * @param dbDir Directory where RocksDB data will be stored
     * @throws RocksDBException if there is an error loading or opening RocksDB instance
     */
    public RocksDao(final String dbDir) throws RocksDBException {
        RocksDB.loadLibrary();
        final ColumnFamilyOptions cfOptions = new ColumnFamilyOptions();
        final DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);
        final List<ColumnFamilyDescriptor> cfDescriptors = Collections.singletonList(
                new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));
        final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        this.rocksDb = RocksDB.open(dbOptions, dbDir, cfDescriptors, columnFamilyHandles);
        this.defaultHandle = columnFamilyHandles.get(0);
        initKryo();
    }

    private void initKryo() {
        kryo = new Kryo();
        kryo.register(BVGraph.class, new BVGraphSerializer(kryo));
		kryo.register(Boolean.class);
        kryo.register(byte[].class);
        kryo.register(InputBitStream.class);
        kryo.register(NullInputStream.class);
        kryo.register(EliasFanoMonotoneLongBigList.class, new JavaSerializer());
        kryo.register(MutableString.class, new FieldSerializer<>(kryo, MutableString.class));
        kryo.register(Properties.class);
        kryo.register(long[].class);
        kryo.register(Long2IntOpenHashMap.class);
		kryo.register(GOV3LongFunction.class, new FieldSerializer<>(kryo, GOV3LongFunction.class));
    }

    /**
     * Inserts graph (nodes and edges) into RocksDB database.
     *
     * @param index       Index of the graph (ID from postgres)
     * @param nodes       List of GID nodes (first internal nodes, then external nodes)
     * @param numInternal Number of internal nodes in nodes list
     * @param edges       List of edges (pairs of GIDs)
     * @throws IOException      if there was a problem writing to files
     * @throws RocksDBException if there was a problem inserting in the database
     */
    public void saveToRocksDb(final long index, final List<Long> nodes, final int numInternal, final List<List<Long>> edges)
            throws IOException, RocksDBException {
        final var nodesSet = new LongOpenHashSet(nodes);
        final var edgeNodesSet = new LongOpenHashSet();
        for (final var edge : edges) {
            edgeNodesSet.addAll(edge);
        }
        // Nodes list must contain all nodes which are in edges
        if (!nodesSet.containsAll(edgeNodesSet)) {
            edgeNodesSet.removeAll(nodesSet);
            throw new IllegalArgumentException("Some nodes from edges are not in the nodes list:\n"
                    + edgeNodesSet);
        }
        final long[] temporary2GID = new long[nodes.size()];
        final var nodesList = new LongArrayList(nodes);
        LongIterators.unwrap(nodesList.iterator(), temporary2GID);
        // Compute the reverse map
        final Long2IntOpenHashMap GID2Temporary = new Long2IntOpenHashMap();
        GID2Temporary.defaultReturnValue(-1);
        for (int i = 0; i < temporary2GID.length; i++) {
            final long result = GID2Temporary.put(temporary2GID[i], i);
            assert result == -1; // Internal and external GIDs should be
            // disjoint by construction
        }
        // Create, store and load compressed versions of the graph and of the transpose.
        // First create the graph as an ArrayListMutableGraph
        final ArrayListMutableGraph mutableGraph = new ArrayListMutableGraph(temporary2GID.length);
        // Add arcs between internal nodes
        for (final List<Long> edge : edges) {
            final int sourceId = GID2Temporary.applyAsInt(edge.get(0));
            final int targetId = GID2Temporary.applyAsInt(edge.get(1));
            try {
                mutableGraph.addArc(sourceId, targetId);
            } catch (final IllegalArgumentException e) {
                logger.error("Duplicate arc (" + sourceId + " -> " + targetId + ")", e);
            }
        }
        final var file = File.createTempFile(KnowledgeBase.class.getSimpleName(), ".tmpgraph");
        final var graphProperties = new Properties();
        final var transposeProperties = new Properties();
        FileInputStream propertyFile;
        // Compress, load and serialize graph

		final ImmutableGraph unpermutedGraph = mutableGraph.immutableView();
		final int numNodes = unpermutedGraph.numNodes();
		final ImmutableGraph symGraph = new ArrayListMutableGraph(Transform.symmetrize(unpermutedGraph)).immutableView();
		final LayeredLabelPropagation clustering = new LayeredLabelPropagation(symGraph, null, Math.min(Runtime.getRuntime().availableProcessors(), 1 + numNodes / 100), 0, false);
		final int[] perm = clustering.computePermutation(LayeredLabelPropagation.DEFAULT_GAMMAS, null);

		Util.invertPermutationInPlace(perm);
		final int[] sorted = new int[numNodes];
		int internal = 0, external = numInternal;
		for (int j = 0; j < numNodes; j++) {
			if (perm[j] < numInternal) sorted[internal++] = perm[j];
			else sorted[external++] = perm[j];
		}
		Util.invertPermutationInPlace(sorted);

		final ImmutableGraph graph = Transform.map(unpermutedGraph, sorted);
        BVGraph.store(graph, file.toString());
        propertyFile = new FileInputStream(file + BVGraph.PROPERTIES_EXTENSION);
        graphProperties.load(propertyFile);
        propertyFile.close();
        final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
        final ByteBufferOutput bbo = new ByteBufferOutput(fbaos);
        kryo.writeObject(bbo, BVGraph.load(file.toString()));
        // Compute LIDs according to the current node renumbering based on BFS
        final long[] LID2GID = new long[temporary2GID.length];
        for (int x = 0; x < temporary2GID.length; x++) {
			LID2GID[sorted[x]] = temporary2GID[x];
        }

		final GOV3LongFunction GID2LID = new GOV3LongFunction.Builder().keys(LongArrayList.wrap(LID2GID)).build();
        // Compress, load and serialize transpose graph
        BVGraph.store(Transform.transpose(graph), file.toString());
        propertyFile = new FileInputStream(file + BVGraph.PROPERTIES_EXTENSION);
        transposeProperties.load(propertyFile);
        propertyFile.close();
		kryo.writeObject(bbo, Boolean.TRUE);
        kryo.writeObject(bbo, BVGraph.load(file.toString()));
        kryo.writeObject(bbo, numInternal);
        // Write out properties
        kryo.writeObject(bbo, graphProperties);
        kryo.writeObject(bbo, transposeProperties);
        // Write out maps
        kryo.writeObject(bbo, LID2GID);
        kryo.writeObject(bbo, GID2LID);
        bbo.flush();
        // Write to DB
        rocksDb.put(defaultHandle, Longs.toByteArray(index), 0, 8, fbaos.array, 0, fbaos.length);
        new File(file.toString() + BVGraph.PROPERTIES_EXTENSION).delete();
        new File(file.toString() + BVGraph.OFFSETS_EXTENSION).delete();
        new File(file.toString() + BVGraph.GRAPH_EXTENSION).delete();
        file.delete();
    }

    /**
     * Retrieves graph data from RocksDB database.
     *
     * @param index Index of the graph
     * @return CallGraphData stored in the database
     * @throws RocksDBException if there was problem retrieving data from RocksDB
     */
	public CallGraphData getGraphData(final long index)
            throws RocksDBException {
        final byte[] buffer = rocksDb.get(Longs.toByteArray(index));
        final Input input = new Input(buffer);
        assert kryo != null;
		final boolean compressed = kryo.readObject(input, Boolean.class).booleanValue();

        final var graphs = new ImmutableGraph[]{
                kryo.readObject(input, BVGraph.class),
                kryo.readObject(input, BVGraph.class)
        };
        final int numInternal = kryo.readObject(input, int.class);
        final Properties[] properties = new Properties[]{
                kryo.readObject(input, Properties.class),
                kryo.readObject(input, Properties.class)
        };
        final long[] LID2GID = kryo.readObject(input, long[].class);
		final GOV3LongFunction GID2LID = kryo.readObject(input, GOV3LongFunction.class);
		return new CallGraphData(graphs[0], graphs[1], properties[0], properties[1],
                LID2GID, GID2LID, numInternal, buffer.length);
    }

    @Override
    public void close() {
        if (defaultHandle != null) {
            defaultHandle.close();
        }
        if (rocksDb != null) {
            rocksDb.close();
        }
    }
}
