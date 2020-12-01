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

import org.apache.commons.io.FileUtils;
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

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.ArrayImmutableDirectedGraph.Builder;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.GOV3LongFunction;
import eu.fasten.core.data.KnowledgeBase;
import eu.fasten.core.index.BVGraphSerializer;
import eu.fasten.core.index.LayeredLabelPropagation;
import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
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
    public RocksDao(final String dbDir, final boolean readOnly) throws RocksDBException {
        RocksDB.loadLibrary();
        final ColumnFamilyOptions cfOptions = new ColumnFamilyOptions();
        @SuppressWarnings("resource") final DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);
        final List<ColumnFamilyDescriptor> cfDescriptors = Collections.singletonList(
                new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));
        final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        this.rocksDb = readOnly
                ? RocksDB.openReadOnly(dbOptions, dbDir, cfDescriptors, columnFamilyHandles)
                : RocksDB.open(dbOptions, dbDir, cfDescriptors, columnFamilyHandles);
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
        kryo.register(Long2ObjectOpenHashMap.class);
        kryo.register(LongOpenHashSet.class);
        kryo.register(ArrayImmutableDirectedGraph.class);
        kryo.register(GOV3LongFunction.class, new JavaSerializer());
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
    public void saveToRocksDb(final long index, List<Long> nodes, int numInternal, final List<List<Long>> edges)
            throws IOException, RocksDBException {

        var internalIds = new LongArrayList(numInternal);
        var externalIds = new LongArrayList(nodes.size() - numInternal);
        for (int i = 0; i < numInternal; i++) {
            internalIds.add(nodes.get(i).longValue());
        }
        for (int i = numInternal; i < nodes.size(); i++) {
            externalIds.add(nodes.get(i).longValue());
        }
        var internalNodesSet = new LongLinkedOpenHashSet(internalIds);
        var externalNodesSet = new LongLinkedOpenHashSet(externalIds);
        numInternal = internalNodesSet.size();
        nodes = new LongArrayList(internalNodesSet.size() + externalNodesSet.size());
        nodes.addAll(internalNodesSet);
        nodes.addAll(externalNodesSet);

        final var nodesSet = new LongLinkedOpenHashSet(nodes);
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

        if (nodes.size() <= Constants.MIN_COMPRESSED_GRAPH_SIZE) {
            /*
             * In this case (very small graphs) there is not much difference with a compressed version, so we
             * use the fast-and-easy implementation of a DirectedGraph given in ArrayImmutableDirectedGraph. The
             * graph is simply serialized with kryo, as it already implements the return interface of
             * getGraphData().
             */
            final Builder builder = new ArrayImmutableDirectedGraph.Builder();
            for (int i = 0; i < numInternal; i++) builder.addInternalNode(nodes.get(i));
            for (int i = numInternal; i < nodes.size(); i++) builder.addExternalNode(nodes.get(i));
            for (final var edge : edges) builder.addArc(edge.get(0), edge.get(1));
            final ArrayImmutableDirectedGraph graph = builder.build();
            final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
            final ByteBufferOutput bbo = new ByteBufferOutput(fbaos);
            kryo.writeObject(bbo, Boolean.FALSE);
            kryo.writeObject(bbo, graph);
            bbo.flush();
            // Write to DB
            rocksDb.put(defaultHandle, Longs.toByteArray(index), 0, 8, fbaos.array, 0, fbaos.length);
        } else {
            /*
             * In this case we compress the graph: first, we remap GIDs into a compact temporary ID space
             * [0..nodes.size()). Then, we build an ArrayListMutableGraph that represent the original graph in
             * the temporary ID space. We run LLP on the graph obtaining a permutation of the temporary ID space
             * that improves greatly compression. Finally, we store the permuted graph and the transpose using
             * BVGraph, and store the bijective mapping between GIDs and the (permuted) temporary ID space.
             */
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

            final ImmutableGraph unpermutedGraph = mutableGraph.immutableView();
            final int numNodes = unpermutedGraph.numNodes();
            // Run LLP on the graph
            final ImmutableGraph symGraph = new ArrayListMutableGraph(Transform.symmetrize(unpermutedGraph)).immutableView();
            final LayeredLabelPropagation clustering = new LayeredLabelPropagation(symGraph, null, Math.min(Runtime.getRuntime().availableProcessors(), 1 + numNodes / 100), 0, false);
            final int[] perm = clustering.computePermutation(LayeredLabelPropagation.DEFAULT_GAMMAS, null);

            // Fix permutation returned by LLP so that it doesn't mix internal and external nodes
            Util.invertPermutationInPlace(perm);
            final int[] sorted = new int[numNodes];
            int internal = 0, external = numInternal;
            for (int j = 0; j < numNodes; j++) {
                if (perm[j] < numInternal) sorted[internal++] = perm[j];
                else sorted[external++] = perm[j];
            }
            Util.invertPermutationInPlace(sorted);

            // Permute, compress and load the graph
            final ImmutableGraph graph = Transform.map(unpermutedGraph, sorted);
            BVGraph.store(graph, file.toString());
            propertyFile = new FileInputStream(file + BVGraph.PROPERTIES_EXTENSION);
            graphProperties.load(propertyFile);
            propertyFile.close();
            final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
            final ByteBufferOutput bbo = new ByteBufferOutput(fbaos);
            kryo.writeObject(bbo, Boolean.TRUE);
            kryo.writeObject(bbo, BVGraph.load(file.toString()));

            // Compute LIDs according to the current node numbering based on the LLP permutation
            final long[] LID2GID = new long[temporary2GID.length];
            for (int x = 0; x < temporary2GID.length; x++) {
                LID2GID[sorted[x]] = temporary2GID[x];
            }

            // Compute a succinct version of the function mapping GIDs to LIDs
            final GOV3LongFunction GID2LID = new GOV3LongFunction.Builder().keys(LongArrayList.wrap(LID2GID)).build();

            // Compress and load the transpose
            BVGraph.store(Transform.transpose(graph), file.toString());
            propertyFile = new FileInputStream(file + BVGraph.PROPERTIES_EXTENSION);
            transposeProperties.load(propertyFile);
            propertyFile.close();
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
            var fileProperties = new File(file.toString() + BVGraph.PROPERTIES_EXTENSION);
            var fileOffsets = new File(file.toString() + BVGraph.OFFSETS_EXTENSION);
            var fileGraph = new File(file.toString() + BVGraph.GRAPH_EXTENSION);
            try {
                FileUtils.forceDelete(fileProperties);
                FileUtils.forceDelete(fileOffsets);
                FileUtils.forceDelete(fileGraph);
                FileUtils.forceDelete(file);
            } finally {
                if (fileProperties.exists()) {
                    fileProperties.delete();
                }
                if (fileOffsets.exists()) {
                    fileOffsets.delete();
                }
                if (fileGraph.exists()) {
                    fileGraph.delete();
                }
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Retrieves graph data from RocksDB database.
     *
     * @param index Index of the graph
     * @return the directed graph stored in the database
     * @throws RocksDBException if there was problem retrieving data from RocksDB
     */
    public DirectedGraph getGraphData(final long index)
            throws RocksDBException {
        try {
            final byte[] buffer = rocksDb.get(Longs.toByteArray(index));
            final Input input = new Input(buffer);
            assert kryo != null;

            final boolean compressed = kryo.readObject(input, Boolean.class);
            if (compressed) {
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
            } else {
                return kryo.readObject(input, ArrayImmutableDirectedGraph.class);
            }
        } catch (final NullPointerException e) {
            logger.warn("Graph with index " + index + " could not be found");
            return null;
        }
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
