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

import static eu.fasten.core.utils.VariableLengthByteCoder.readLong;
import static eu.fasten.core.utils.VariableLengthByteCoder.readString;
import static eu.fasten.core.utils.VariableLengthByteCoder.writeLong;
import static eu.fasten.core.utils.VariableLengthByteCoder.writeString;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
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
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.GOV3LongFunction;
import eu.fasten.core.data.graphdb.GraphMetadata.NodeMetadata;
import eu.fasten.core.data.graphdb.GraphMetadata.ReceiverRecord;
import eu.fasten.core.data.graphdb.GraphMetadata.ReceiverRecord.CallType;
import eu.fasten.core.data.metadatadb.codegen.tables.records.CallSitesRecord;
import eu.fasten.core.index.BVGraphSerializer;
import eu.fasten.core.index.LayeredLabelPropagation;
import eu.fasten.core.legacy.KnowledgeBase;
import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
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

    private final static byte[] METADATA_COLUMN_FAMILY = "metadata".getBytes();
    private final RocksDB rocksDb;
    private final ColumnFamilyHandle defaultHandle;
    private ColumnFamilyHandle metadataHandle;
    private Kryo kryo;
    private final static Logger logger = LoggerFactory.getLogger(RocksDao.class.getName());

    public RocksDao(final String dbDir, final boolean readOnly) throws RocksDBException {
        this(dbDir, readOnly, false);
    }

    /**
     * Constructor of RocksDao (Database Access Object).
     *
     * @param dbDir Directory where RocksDB data will be stored
     * @throws RocksDBException if there is an error loading or opening RocksDB instance
     */
    public RocksDao(final String dbDir, final boolean readOnly, final boolean onlyDefaultColumnFamily) throws RocksDBException {    // TODO: Remove onlyDefaultColumnFamily
        RocksDB.loadLibrary();
		final ColumnFamilyOptions defaultOptions = new ColumnFamilyOptions();
        ColumnFamilyOptions metadataOptions = null;
        if (!onlyDefaultColumnFamily) {
            metadataOptions = new ColumnFamilyOptions().setCompressionType(CompressionType.ZSTD_COMPRESSION);
        }
        @SuppressWarnings("resource") final DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);
		final List<ColumnFamilyDescriptor> cfDescriptors = onlyDefaultColumnFamily ?
                List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, defaultOptions)) :
                List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, defaultOptions), new ColumnFamilyDescriptor(METADATA_COLUMN_FAMILY, metadataOptions));
        final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        this.rocksDb = readOnly
                ? RocksDB.openReadOnly(dbOptions, dbDir, cfDescriptors, columnFamilyHandles)
                : RocksDB.open(dbOptions, dbDir, cfDescriptors, columnFamilyHandles);
        this.defaultHandle = columnFamilyHandles.get(0);
        if (!onlyDefaultColumnFamily) {
            this.metadataHandle = columnFamilyHandles.get(1);
        }
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

    private GraphMetadata.ReceiverRecord.CallType transformCallType(eu.fasten.core.data.metadatadb.codegen.enums.CallType type) {
        switch (type) {
            case dynamic:
                return CallType.DYNAMIC;
            case special:
                return CallType.SPECIAL;
            case static_:
                return CallType.STATIC;
            case interface_:
                return CallType.INTERFACE;
            case virtual:
                return CallType.VIRTUAL;
            default:
                return null;
        }
    }

    public void saveToRocksDb(final GidGraph gidGraph) throws IOException, RocksDBException {
        // Save and obtain graph
        final DirectedGraph graph = saveToRocksDb(gidGraph.getIndex(), gidGraph.getNodes(), gidGraph.getNumInternalNodes(), gidGraph.getEdges());
        if (gidGraph instanceof ExtendedGidGraph) {
            // Save metadata
			final ExtendedGidGraph extendedGidGraph = (ExtendedGidGraph)gidGraph;

			final Map<Pair<Long, Long>, CallSitesRecord> edgesInfo = extendedGidGraph.getCallsInfo();
			final Map<Long, String> typeMap = extendedGidGraph.getTypeMap();
            final Long2ObjectOpenHashMap<List<ReceiverRecord>> map = new Long2ObjectOpenHashMap<>();

            final Map<Long, String> gidToUriMap = extendedGidGraph.getGidToUriMap();

            // Gather data by source and store it in lists of GraphMetadata.ReceiverRecord.
            edgesInfo.forEach((pair, record) -> map.compute(pair.getFirst().longValue(), (k, list) -> {
                if (list == null) list = new ArrayList<>();
                list.add(new ReceiverRecord(record.getLine(), transformCallType(record.getCallType()), gidToUriMap.get(pair.getFirst()), Arrays.stream(record.getReceiverTypeIds()).map(typeMap::get).collect(Collectors.toList())));
                return list;
            }));


            // Serialize information in compact form, following the standard node enumeration order
            final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
			for (final LongIterator iterator = graph.iterator(); iterator.hasNext();) {
                final long node = iterator.nextLong();

                final FastenJavaURI uri = FastenJavaURI.create(gidToUriMap.get(node)).decanonicalize();
                writeString("/" + uri.getNamespace() + "/" + uri.getClassName(), fbaos);
                writeString(StringUtils.substringAfter(uri.getEntity(), "."), fbaos);

				final List<ReceiverRecord> list = map.get(node);
                // TODO: is this acceptable behavior?
                if (list == null) writeLong(0, fbaos); // no data
                else {
                    // Encode list length
                    writeLong(list.size(), fbaos);
                    // Encode elements
					for (final var r : list) {
                        writeLong(r.line, fbaos);
                        writeLong(r.callType.ordinal(), fbaos);
						writeString(r.receiverSignature, fbaos);
						writeLong(r.receiverTypes.size(), fbaos);
						for (final String s : r.receiverTypes) writeString(s, fbaos);
                    }
                }
            }
			rocksDb.put(metadataHandle, Longs.toByteArray(gidGraph.getIndex()), 0, 8, fbaos.array, 0, fbaos.length);
		}
    }

    /**
	 * Inserts graph (nodes and edges) into RocksDB database.
	 *
	 * @param index Index of the graph (ID from postgres)
	 * @param nodes List of GID nodes (first internal nodes, then external nodes)
	 * @param numInternal Number of internal nodes in nodes list
	 * @param edges List of edges (pairs of GIDs)
	 * @return the stored {@link DirectedGraph}, or {@code null} if the provided index key is already
	 *         present.
	 * @throws IOException if there was a problem writing to files
	 * @throws RocksDBException if there was a problem inserting in the database
	 */
    public DirectedGraph saveToRocksDb(final long index, List<Long> nodes, int numInternal, final List<List<Long>> edges)
            throws IOException, RocksDBException {
        final var internalIds = new LongArrayList(numInternal);
        final var externalIds = new LongArrayList(nodes.size() - numInternal);
        for (int i = 0; i < numInternal; i++) {
            internalIds.add(nodes.get(i).longValue());
        }
        for (int i = numInternal; i < nodes.size(); i++) {
            externalIds.add(nodes.get(i).longValue());
        }
        final var internalNodesSet = new LongLinkedOpenHashSet(internalIds);
        final var externalNodesSet = new LongLinkedOpenHashSet(externalIds);
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
            return graph;
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
            final ImmutableGraph storedGraph = BVGraph.load(file.toString());
            kryo.writeObject(bbo, storedGraph);

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
            final ImmutableGraph storedTranspose = BVGraph.load(file.toString());
            kryo.writeObject(bbo, storedTranspose);
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
			final var fileProperties = new File(file + BVGraph.PROPERTIES_EXTENSION);
			final var fileOffsets = new File(file + BVGraph.OFFSETS_EXTENSION);
			final var fileGraph = new File(file + BVGraph.GRAPH_EXTENSION);
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
            return new CallGraphData(storedGraph, storedTranspose, graphProperties, transposeProperties,
                    LID2GID, GID2LID, numInternal, fbaos.length);

        }
    }

    /**
     * Retrieves graph data from RocksDB database.
     *
     * @param index Index of the graph
     * @return the directed graph stored in the database
     * @throws RocksDBException if there was problem retrieving data from RocksDB
     */
    public DirectedGraph getGraphData(final long index) throws RocksDBException {
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

    /**
     * Retrieves graph metadata from RocksDB database.
     *
     * @param index index of the graph
     * @param graph the graph associated with {@code} index
     * @return the metadata associated with the graph, or {@code null}
     * if no metadata record exists for the provided graph
     * @throws RocksDBException if there was problem retrieving data from RocksDB
     */
    public GraphMetadata getGraphMetadata(final long index, final DirectedGraph graph) throws RocksDBException {
        final byte[] metadata = rocksDb.get(metadataHandle, Longs.toByteArray(index));
        if (metadata != null) {
            final Long2ObjectOpenHashMap<NodeMetadata> map = new Long2ObjectOpenHashMap<>();

            final FastByteArrayInputStream fbais = new FastByteArrayInputStream(metadata);

            try {
                // Deserialize map following the standard node enumeration order
				for (final LongIterator iterator = graph.iterator(); iterator.hasNext();) {
                    final long node = iterator.nextLong();

                    final String type = readString(fbais);
                    final String signature = readString(fbais);

                    final long length = readLong(fbais);
                    List<ReceiverRecord> list;
                    if (length == 0) list = Collections.emptyList();
                    else {
                        list = new ArrayList<>();
						for (long i = 0; i < length; i++) {
							final int line = (int)readLong(fbais);
							final CallType callType = CallType.values()[(int)readLong(fbais)];
							final String receiverSignature = readString(fbais);
							final int size = (int)readLong(fbais);
							final ArrayList<String> t = new ArrayList<>();
							for (int j = 0; j < size; j++) t.add(readString(fbais));
							list.add(new ReceiverRecord(line, callType, receiverSignature, t));
						}
                    }

                    // Make the list immutable
                    map.put(node, new NodeMetadata(type, signature, List.copyOf(list)));
                }
            } catch (final IOException cantHappen) {
                // Not really I/O
                throw new RuntimeException(cantHappen.getCause());
            }
            return new GraphMetadata(map);
        }
        return null;
    }


    /**
     * Deletes a graph from the graph database based on its index.
     *
     * @param index Index of thr graph (package_version.id)
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteCallGraph(final long index) {
        try {
            rocksDb.delete(defaultHandle, Longs.toByteArray(index));
            rocksDb.delete(metadataHandle, Longs.toByteArray(index));
        } catch (final RocksDBException e) {
            logger.error("Could not delete graph with index " + index, e);
            return false;
        }
        return true;
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
