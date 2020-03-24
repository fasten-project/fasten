package eu.fasten.core.data;

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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.Options;
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

import eu.fasten.core.index.BVGraphSerializer;
import it.unimi.dsi.Util;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import it.unimi.dsi.fastutil.objects.AbstractObjectCollection;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.NullInputStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;
import it.unimi.dsi.webgraph.Transform;

/**  Instances of this class represent a knowledge base (i.e., a set of revision call graphs).
 *   The knowledge base keeps the actual graphs in an associated {@linkplain #callGraphDB database}.
 */
public class KnowledgeBase implements Serializable, Closeable {
	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBase.class);

	/** A node in the knowledge base is represented by a revision index and a GID, with the proviso
	 *  that the gid corresponds to an internal node of the call graph specified by the index.
	 */
	public class Node {

		/** Builds a node.
		 *
		 * @param gid the GID.
		 * @param index the revision index.
		 */
		public Node(final long gid, final long index) {
			this.gid = gid;
			this.index = index;
		}

		/** The GID. */
		public long gid;
		/** The revision index. */
		public long index;

		/** Returns the {@link FastenURI} corresponding to this node.
		 *
		 * @return the {@link FastenURI} corresponding to this node.
		 */
		public FastenURI toFastenURI() {
			final FastenURI genericURI = GID2GenericURI.get(gid);
			if (genericURI == null) return null;
			final CallGraph callGraph = callGraphs.get(index);
			return FastenURI.create(callGraph.forge, callGraph.product, callGraph.version, genericURI.getRawNamespace(), genericURI.getRawEntity());
		}

		@Override
		public String toString() {
			return 	"[GID=" + gid +
					", LID=" + callGraphs.get(index).callGraphData().GID2LID.get(gid) +
					", revision=" + index +
					"]: " + toFastenURI().toString();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result + (int) (gid ^ (gid >>> 32));
			result = prime * result + (int) (index ^ (index >>> 32));
			return result;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final Node other = (Node) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (gid != other.gid)
				return false;
			if (index != other.index)
				return false;
			return true;
		}

		private KnowledgeBase getOuterType() {
			return KnowledgeBase.this;
		}
	}

	/** Maps <em>generic</em> (i.e., schemeless, forgeless, productless and versionless) FASTEN URIs to a unique identifier. */
	protected final Object2LongMap<FastenURI> genericURI2GID;

	/** The inverse of {@link #genericURI2GID}. */
	protected final Long2ObjectMap<FastenURI> GID2GenericURI;

	/** Maps each GID to a list of revisions (identified by their revision index) in which the GID appears as an internal node. */
	protected final Long2ObjectMap<LongSet> GIDAppearsIn;

	/** Maps each GID to a list of revisions (identified by their revision index) in which the GID appears as an external node. */
	protected final Long2ObjectMap<LongSet> GIDCalledBy;

	/** Maps revision indices to the corresponding call graph. */
	public final Long2ObjectOpenHashMap<CallGraph> callGraphs;

	/** The RocksDB instance used by this indexer. */
	private transient RocksDB callGraphDB;

	/** The {@link Kryo} object used to serialize data to the database. */
	private transient Kryo kryo;

	/** The pathname of the file containing the metadata of this knowledgebase. */
	private String metadataPathname;


	public static final class CallGraphData {
		public final ImmutableGraph graph, transpose;
		public final Properties graphProperties, transposeProperties;
		/** Maps LIDs to GIDs. TODO: in database */
		public final long[] LID2GID;
		/** Inverse to {@link #LID2GID}: maps GIDs to LIDs. TODO: in database */
		public final Long2IntOpenHashMap GID2LID;

		public CallGraphData(final ImmutableGraph graph, final ImmutableGraph transpose, final Properties graphProperties, final Properties transposeProperties, final long[] LID2GID, final Long2IntOpenHashMap GID2LID) {
			super();
			this.graph = graph;
			this.transpose = transpose;
			this.graphProperties = graphProperties;
			this.transposeProperties = transposeProperties;
			this.LID2GID = LID2GID;
			this.GID2LID = GID2LID;
		}
	}


	/** Instances represent call graphs and the associated metadata. Each call
	 *  graph corresponds to a specific release (product, version, forge), and has a unique
	 *  revision index. Its nodes are divided into internal nodes and external nodes
	 *  (the former have smaller values, the latter have larger values). Each node number is called
	 *  a local identifier (LID); LIDs are mapped to global identifiers (GIDs).
	 *  External nodes have no outgoing arcs.
	 */
	public class CallGraph implements Serializable {
		private static final long serialVersionUID = 1L;
		/** Number of internal nodes (first {@link #nInternal} GIDs in {@link #LID2GID}). */
		public final int nInternal;
		/** The product described in this call graph. */
		private final String product;
		/** The version described in this call graph. */
		private final String version;
		/** The forge described in this call graph. */
		private final String forge;
		/** The revision index of this call graph. */
		private final long index;
		/** An array of two graphs: the call graph (index 0) and its transpose (index 1). */
		@SuppressWarnings("null")


		private transient SoftReference<CallGraphData> callGraphData;

		// ALERT unsynchronized update of Knowledge Base maps.
		/** Creates a call graph from a {@link ExtendedRevisionCallGraph}. All maps of the knowledge base (e.g. {@link KnowledgeBase#GIDAppearsIn}) are updated
		 *  appropriately. The graphs are stored in the database.
		 *
		 * @param g the revision call graph.
		 * @param index the revision index.
		 * @throws IOException
		 * @throws RocksDBException
		 */
		protected CallGraph(final ExtendedRevisionCallGraph g, final long index) throws IOException, RocksDBException {
			product = g.product;
			version = g.version;
			forge = g.forge;
			this.index = index;

			LOGGER.debug("Analyzing fasten://" + forge + "!" + product + "$" + version);

			final LongLinkedOpenHashSet internalGIDs = new LongLinkedOpenHashSet(); // List of internal GIDs
			final LongLinkedOpenHashSet externalGIDs = new LongLinkedOpenHashSet(); // List of external GIDs
			final Int2IntOpenHashMap jsonId2Temporary = new Int2IntOpenHashMap();

			final Map<Integer, FastenURI> mapOfAllMethods = g.mapOfAllMethods();
			for(final Entry<Integer, FastenURI> e : mapOfAllMethods.entrySet()) {
				final int jsonId = e.getKey().intValue();
				final FastenURI uri = e.getValue();
				final FastenURI genericUri = FastenURI.createSchemeless(null, null, null, uri.getRawNamespace(), uri.getRawEntity());
				final long gid = addURI(genericUri);
				addGidRev(GIDAppearsIn, gid, index);
				jsonId2Temporary.put(jsonId, internalGIDs.size());
				internalGIDs.add(gid);
			}

			nInternal = internalGIDs.size();

			for(final Pair<Integer, FastenURI> e : g.getGraph().getExternalCalls().keySet()) {
				final FastenURI uri = e.getValue();
				final FastenURI genericUri = FastenURI.createSchemeless(null, null, null, uri.getRawNamespace(), uri.getRawEntity());
				final long gid = addURI(genericUri);
				if (internalGIDs.contains(gid)) LOGGER.error("GID " + gid + " (URL " + uri + ") appears both as an internal and as an external node: considering it internal");
				else {
					addGidRev(GIDCalledBy, gid, index);
					externalGIDs.add(gid);
				}
			}


			final long[] temporary2GID = new long[internalGIDs.size() + externalGIDs.size()];
			LongIterators.unwrap(internalGIDs.iterator(), temporary2GID);
			LongIterators.unwrap(externalGIDs.iterator(), temporary2GID, nInternal, temporary2GID.length - nInternal);
			final Long2IntOpenHashMap GID2Temporary = new Long2IntOpenHashMap();
			GID2Temporary.defaultReturnValue(-1);
			for(int i = 0; i < temporary2GID.length; i++) {
				final long result = GID2Temporary.put(temporary2GID[i], i);
				assert result == -1; // Internal and external GIDs should be disjoint by construction
			}

			// Create, store and load compressed versions of the graph and of the transpose.
			final ArrayListMutableGraph mutableGraph = new ArrayListMutableGraph(temporary2GID.length);

			for(final List<Integer> a : g.getGraph().getInternalCalls()) {
				final int jsonSource = a.get(0).intValue();
				final int jsonTarget = a.get(1).intValue();

				try {
					mutableGraph.addArc(jsonId2Temporary.get(jsonSource), jsonId2Temporary.get(jsonTarget));
				} catch(final IllegalArgumentException e ) {
					LOGGER.error("Duplicate arc " + GID2GenericURI.get(temporary2GID[jsonId2Temporary.get(jsonSource)]) + " -> " + GID2GenericURI.get(temporary2GID[jsonId2Temporary.get(jsonSource)]));
				}
			}

			for(final Pair<Integer, FastenURI> a : g.getGraph().getExternalCalls().keySet()) {
				final int jsonSource = a.getLeft().intValue();
				final FastenURI targetUri = a.getRight();
				final FastenURI genericTargetUri = FastenURI.createSchemeless(null, null, null, targetUri.getRawNamespace(), targetUri.getRawEntity());
				final long targetGID = addURI(genericTargetUri);

				try {
					mutableGraph.addArc(jsonId2Temporary.get(jsonSource), GID2Temporary.get(targetGID));
				} catch(final IllegalArgumentException e ) {
					LOGGER.error("Duplicate arc " + GID2GenericURI.get(temporary2GID[jsonId2Temporary.get(jsonSource)]) + " -> " + genericTargetUri);
				}
			}

			final File f = File.createTempFile(KnowledgeBase.class.getSimpleName(), ".tmpgraph");

			final Properties graphProperties = new Properties(), transposeProperties = new Properties();
			FileInputStream propertyFile;

			// Compress, load and serialize graph
			final int[] bfsperm = Util.identity(temporary2GID.length); //bfsperm(mutableGraph.immutableView(), -1, internalGIDs.size());
			final ImmutableGraph graph = Transform.map(mutableGraph.immutableView(), bfsperm);
			BVGraph.store(graph, f.toString());
			propertyFile = new FileInputStream(f + BVGraph.PROPERTIES_EXTENSION);
			graphProperties.load(propertyFile);
			propertyFile.close();

			final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
			final ByteBufferOutput bbo = new ByteBufferOutput(fbaos);
			kryo.writeObject(bbo, BVGraph.load(f.toString()));

			// Permute LID2GID accordingly
			final long[] LID2GID = new long[temporary2GID.length];
			final Long2IntOpenHashMap GID2LID = new Long2IntOpenHashMap();

			for (int x = 0; x < temporary2GID.length; x++) LID2GID[bfsperm[x]] = temporary2GID[x];
			for(int i = 0; i < temporary2GID.length; i++) GID2LID.put(LID2GID[i], i);

			// Compress, load and serialize transpose graph
			BVGraph.store(Transform.transpose(graph), f.toString());
			propertyFile = new FileInputStream(f + BVGraph.PROPERTIES_EXTENSION);
			transposeProperties.load(propertyFile);
			propertyFile.close();

			kryo.writeObject(bbo, BVGraph.load(f.toString()));

			// Write out properties
			kryo.writeObject(bbo, graphProperties);
			kryo.writeObject(bbo, transposeProperties);

			kryo.writeObject(bbo, LID2GID);
			kryo.writeObject(bbo, GID2LID);

			bbo.flush();

			// Write to DB
			callGraphDB.put(Longs.toByteArray(index), 0, 8, fbaos.array, 0, fbaos.length);

			new File(f.toString() + BVGraph.PROPERTIES_EXTENSION).delete();
			new File(f.toString() + BVGraph.OFFSETS_EXTENSION).delete();
			new File(f.toString() + BVGraph.GRAPH_EXTENSION).delete();
			f.delete();
		}

		/** Returns the call graph and its transpose in a 2-element array. The graphs are cached,
		 *  and read from the database if needed.
		 *
		 * @return an array containing the call graph and its transpose.
		 */
		public CallGraphData callGraphData() {
			if (callGraphData != null) {
				final var callGraphData = this.callGraphData.get();
				if (callGraphData != null) return callGraphData;
			}
			try {
				byte[] buffer = new byte[0];
				for(int length; (length = callGraphDB.get(Longs.toByteArray(index), buffer)) > buffer.length; ) buffer = new byte[length];
				final Input input = new Input(buffer);
				assert kryo != null;
				final var graphs = new ImmutableGraph[] {kryo.readObject(input, BVGraph.class),  kryo.readObject(input, BVGraph.class)};
				final Properties[] properties = new Properties[] { kryo.readObject(input, Properties.class), kryo.readObject(input, Properties.class) };
				final long[] LID2GID = kryo.readObject(input, long[].class);
				final Long2IntOpenHashMap GID2LID = kryo.readObject(input, Long2IntOpenHashMap.class);
				final CallGraphData callGraphData = new CallGraphData(graphs[0], graphs[1], properties[0], properties[1], LID2GID, GID2LID);
				this.callGraphData = new SoftReference<>(callGraphData);
				return callGraphData;
			} catch (final RocksDBException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder();

			final CallGraphData callGraphData = callGraphData();
			for(final NodeIterator nodeIterator = callGraphData.graph.nodeIterator(); nodeIterator.hasNext(); ) {
				final FastenURI u = GID2GenericURI.get(callGraphData.LID2GID[nodeIterator.nextInt()]);
				final LazyIntIterator successors = nodeIterator.successors();
				for(int s; (s = successors.nextInt()) != -1; )
					b.append(u).append('\t').append(GID2GenericURI.get(callGraphData.LID2GID[s])).append('\n');
			}
			return b.toString();
		}
	}

	/** Wraps a set of nodes, and allows one to iterate over it with an iterator that returns the {@link FastenURI} of the
	 *  node each time.
	 */
	private final class NamedResult extends AbstractObjectCollection<FastenURI> {
		private final ObjectLinkedOpenHashSet<Node> reaches;

		/** Wraps a given set of nodes.
		 *
		 * @param objectLinkedOpenHashSet the set of nodes.
		 */
		private NamedResult(final ObjectLinkedOpenHashSet<Node> objectLinkedOpenHashSet) {
			this.reaches = objectLinkedOpenHashSet;
		}

		@Override
		public int size() {
			return reaches.size();
		}

		@Override
		public boolean isEmpty() {
			return reaches.isEmpty();
		}

		@Override
		public ObjectIterator<FastenURI> iterator() {
			final ObjectIterator<Node> iterator = reaches.iterator();
			return new ObjectIterator<>() {

				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}

				@Override
				public FastenURI next() {
					return iterator.next().toFastenURI();
				}
			};
		}
	}

	/** Initializes the kryo instance used for serialization. */
	private void initKryo() {
		kryo = new Kryo();
		kryo.register(BVGraph.class, new BVGraphSerializer(kryo));
		kryo.register(byte[].class);
		kryo.register(InputBitStream.class);
		kryo.register(NullInputStream.class);
		kryo.register(EliasFanoMonotoneLongBigList.class, new JavaSerializer());
		kryo.register(MutableString.class, new FieldSerializer<>(kryo, MutableString.class));
		kryo.register(Properties.class);
		kryo.register(long[].class);
		kryo.register(Long2IntOpenHashMap.class);
	}

	/** Creates a new knowledge base with no associated database; initializes kryo. One has to explicitly call {@link #callGraphDB(RocksDB)}
	 *  or {@link #callGraphDB(String)} (typically only once) before using the resulting instance. */
	private KnowledgeBase() {
		genericURI2GID = new Object2LongOpenHashMap<>();
		GID2GenericURI = new Long2ObjectOpenHashMap<>();
		GIDAppearsIn = new Long2ObjectOpenHashMap<>();
		GIDCalledBy = new Long2ObjectOpenHashMap<>();
		callGraphs = new Long2ObjectOpenHashMap<>();

		genericURI2GID.defaultReturnValue(-1);
		GIDAppearsIn.defaultReturnValue(LongSets.EMPTY_SET);
		GIDCalledBy.defaultReturnValue(LongSets.EMPTY_SET);

		initKryo();
	}

	/** Associates the given database to this knowledge base.
	 *
	 * @param db the database to be associated.
	 */
	public void callGraphDB(final RocksDB db) {
		this.callGraphDB = db;
	}

	public static KnowledgeBase getInstance(final String kbDir, final String kbMetadataPathname) throws RocksDBException, ClassNotFoundException, IOException {
		final boolean metadataExists = new File(kbMetadataPathname).exists();
		final boolean kbDirExists = new File(kbDir).exists();
		if (metadataExists != kbDirExists) throw new IllegalArgumentException("Either both or none of the knowledge-base directory and metadata must exist");

		RocksDB.loadLibrary();
		final Options options = new Options();
		options.setCreateIfMissing(true);

		final RocksDB db = RocksDB.open(options, kbDir);
		final KnowledgeBase kb = metadataExists ? (KnowledgeBase)BinIO.loadObject(kbMetadataPathname) :  new KnowledgeBase();
		kb.metadataPathname = kbMetadataPathname;
		kb.callGraphDB(db);
		return kb;
	}

	/** Adds a given revision index to the set associated to the given gid.
	 *
	 * @param map the map associating gids to sets revision indices.
	 * @param gid the gid whose associated set should be modified.
	 * @param revIndex the revision index to be added.
	 *
	 * @return true iff the revision index was not present.
	 */
	protected static boolean addGidRev(final Long2ObjectMap<LongSet> map, final long gid, final long revIndex) {
		LongSet set = map.get(gid);
		if (set == LongSets.EMPTY_SET) map.put(gid, set = new LongOpenHashSet());
		return set.add(revIndex);
	}

	/** Adds a URI to the global maps. If the URI is already present, returns its GID.
	 *
	 * @param uri a Fasten URI.
	 * @return the associated GID.
	 */
	protected long addURI(final FastenURI uri) {
		long gid = genericURI2GID.getLong(uri);
		if (gid != -1) return gid;
		gid = genericURI2GID.size();
		genericURI2GID.put(uri, gid);
		GID2GenericURI.put(gid, uri);
		return gid;
	}

	/** Returns the successors of a given node.
	 *
	 * @param node a node (say, corresponding to the pair [<code>index</code>, <code>LID</code>])
	 * @return the list of all successors; these are obtained as follows: for every successor <code>x</code>
	 * of <code>node</code> in the call graph
	 * <ul>
	 * 	<li>if <code>x</code> is internal, [<code>index</code>, <code>LID</code>] is a successor
	 *  <li>if <code>x</code> is external and it corresponds to the GID <code>g</code> (which in turn corresponds to a
	 *  generic {@link FastenURI}), we look at every index <code>otherIndex</code> where <code>g</code>
	 *  appears, and let <code>otherLID</code> be the corresponding LID: then [<code>otherIndex</code>, <code>otherLID</code>]
	 *  is a successor.
	 * </ul>
	 */
	public ObjectList<Node> successors(final Node node) {
		final long gid = node.gid;
		final long index = node.index;
		final CallGraph callGraph = callGraphs.get(index);
		assert callGraph != null;

		final CallGraphData callGraphData = callGraph.callGraphData();
		final ImmutableGraph graph = callGraphData.graph;
		final LazyIntIterator s = graph.successors(callGraphData.GID2LID.get(gid));

		final ObjectList<Node> result = new ObjectArrayList<>();
		int x;

		/* In the successor case, internal nodes can be added directly... */

		while((x = s.nextInt()) != -1 && x < callGraph.nInternal) result.add(new Node(callGraphData.LID2GID[x], index));

		if (x == -1) return result;

		/* ...but external nodes must be search for in the revision call graphs in which they appear. */
		do {
			final long xGid = callGraphData.LID2GID[x];
			for(final LongIterator revisions = GIDAppearsIn.get(xGid).iterator(); revisions.hasNext();)
				result.add(new Node(xGid, revisions.nextLong()));
		} while((x = s.nextInt()) != -1);

		return result;
	}

	/** Returns the predecessors of a given node.
	 *
	 * @param node a node (for the form [<code>index</code>, <code>LID</code>])
	 * @return the list of all predecessors; these are obtained as follows:
	 * <ul>
	 * 	<li>for every predecessor <code>x</code>
	 *  of <code>node</code> in the call graph, [<code>index</code>, <code>LID</code>] is a predecessor
	 *  <li>let <code>g</code> be the GID of <code>node</code>: for every index <code>otherIndex</code>
	 *  that calls <code>g</code> (i.e., where <code>g</code> is the GID of an external node),
	 *  and for all the predecessors <code>x</code> of the node with GID <code>g</code> in <code>otherIndex</code>,
	 *  [<code>otherIndex</code>, <code>x</code>] is a predecessor.
	 * </ul>
	 */
	public ObjectList<Node> predecessors(final Node node) {
		final long gid = node.gid;
		final long index = node.index;
		final CallGraph callGraph = callGraphs.get(index);
		assert callGraph != null;

		final CallGraphData callGraphData = callGraph.callGraphData();
		final ImmutableGraph graph = callGraphData.transpose;
		final LazyIntIterator s = graph.successors(callGraphData.GID2LID.get(gid));

		final ObjectList<Node> result = new ObjectArrayList<>();
		int x;

		/* In the predecessor case, all nodes returned by the graph are necessarily internal. */
		while((x = s.nextInt()) != -1) {
			assert x < callGraph.nInternal;
			result.add(new Node(callGraphData.LID2GID[x], index));
		}

		/* To move backward in the call graph, we use GIDCalledBy to find revisions that might
		 * contain external nodes of the form <gid, index>. */
		do
			for(final LongIterator revisions = GIDCalledBy.get(gid).iterator(); revisions.hasNext();) {
				final long revIndex = revisions.nextLong();
				final CallGraph precCallGraph = callGraphs.get(revIndex);
				final CallGraphData precCallGraphData = precCallGraph.callGraphData();
				final ImmutableGraph transpose = precCallGraphData.transpose;
				final LazyIntIterator p = transpose.successors(precCallGraphData.GID2LID.get(gid));
				for(int y; (y = p.nextInt()) != -1;) result.add(new Node(precCallGraphData.LID2GID[y], revIndex));
			}
		while((x = s.nextInt()) != -1);

		return result;
	}

	/** Returns the node corresponding to a given (non-generic) {@link FastenURI}.
	 *
	 * @param fastenURI a {@link FastenURI} with version.
	 * @return the corresponding node, or <code>null</code>.
	 */
	public Node fastenURI2Node(final FastenURI fastenURI) {
		if (fastenURI.getVersion() == null) throw new IllegalArgumentException("The FASTEN URI must be versioned");
		final FastenURI genericURI = FastenURI.createSchemeless(null, fastenURI.getRawProduct(), null, fastenURI.getRawNamespace(), fastenURI.getRawEntity());
		final long gid = genericURI2GID.getLong(genericURI);
		if (gid == -1) return null;
		final String version = fastenURI.getVersion();
		for(final long index: GIDAppearsIn.get(gid))
			if (version.equals(callGraphs.get(index).version)) return new Node(gid, index);

		return null;
	}

	/** Given a generic URI (one without a version), returns all the matching non-generic URIs.
	 *
	 * @param genericURI a generic URI.
	 * @return the list of all non-generic URIs matching <code>genericURI</code>.
	 */
	public ObjectList<FastenURI> genericURI2URIs(final FastenURI genericURI) {
		if (genericURI.getVersion() != null || genericURI.getScheme() != null) throw new IllegalArgumentException("The FASTEN URI must be generic and schemeless");
		final long gid = genericURI2GID.getLong(genericURI);
		if (gid == -1) return null;
		final ObjectArrayList<FastenURI> result = new ObjectArrayList<>();
		for(final long index: GIDAppearsIn.get(gid)) result.add(FastenURI.createSchemeless(genericURI.getRawForge(), genericURI.getRawProduct(), callGraphs.get(index).version, genericURI.getRawNamespace(), genericURI.getRawEntity()));
		return result;
	}

	/** The set of all nodes that are reachable from <code>start</code>.
	 *
	 * @param start the starting node.
	 * @return the set of all nodes for which there is a directed path from <code>start</code> to that node.
	 */
	public synchronized ObjectLinkedOpenHashSet<Node> reaches(final Node start) {
		final ObjectLinkedOpenHashSet<Node> result = new ObjectLinkedOpenHashSet<>();
		// Visit queue
		final ObjectArrayFIFOQueue<Node> queue = new ObjectArrayFIFOQueue<>();
		queue.enqueue(start);

		while(!queue.isEmpty()) {
			final Node node = queue.dequeue();
			if (result.add(node)) for(final Node s: successors(node))
				if (!result.contains(s)) queue.enqueue(s);
		}

		return result;
	}

	/** The set of all {@link FastenURI} that are reachable from a given {@link FastenURI}; just a convenience
	 *  method to be used instead of {@link #reaches(Node)}.
	 *
	 * @param fastenURI the starting node.
	 * @return all the nodes that can be reached from <code>fastenURI</code>.
	 */
	public Collection<FastenURI> reaches(final FastenURI fastenURI) {
		final Node start = fastenURI2Node(fastenURI);
		if (start == null) return null;
		return new NamedResult(reaches(start));
	}

	/** The set of all nodes that are coreachable from <code>start</code>.
	 *
	 * @param start the starting node.
	 * @return the set of all nodes for which there is a directed path from that node to <code>start</code>.
	 */
	public synchronized ObjectLinkedOpenHashSet<Node> coreaches(final Node start) {
		final ObjectLinkedOpenHashSet<Node> result = new ObjectLinkedOpenHashSet<>();
		// Visit queue
		final ObjectArrayFIFOQueue<Node> queue = new ObjectArrayFIFOQueue<>();
		queue.enqueue(start);

		while(!queue.isEmpty()) {
			final Node node = queue.dequeue();
			if (result.add(node))
				for(final Node s: predecessors(node))
					if (!result.contains(s)) queue.enqueue(s);
		}

		return result;
	}

	/** The set of all {@link FastenURI} that are coreachable from a given {@link FastenURI}; just a convenience
	 *  method to be used instead of {@link #coreaches(Node)}.
	 *
	 * @param fastenURI the starting node.
	 * @return all the nodes that can be coreached from <code>fastenURI</code>.
	 */
	public synchronized Collection<FastenURI> coreaches(final FastenURI fastenURI) {
		final Node start = fastenURI2Node(fastenURI);
		if (start == null) return null;
		return new NamedResult(coreaches(start));
	}

	/** Adds a new {@link CallGraph} to the list of all call graphs.
	 *
	 * @param g the revision call graph from which the call graph will be created.
	 * @param index the revision index to which the new call graph will be associated.
	 * @throws IOException
	 * @throws RocksDBException
	 */
	public synchronized void add(final ExtendedRevisionCallGraph g, final long index) throws IOException, RocksDBException {
		callGraphs.put(index, new CallGraph(g, index));
	}

	@Override
	public void close() throws IOException {
		try {
			BinIO.storeObject(this, metadataPathname);
		} finally {
			callGraphDB.close();
		}
	}

	/** The number of call graphs.
	 *
	 * @return the number of call graphs.
	 */
	public long size() {
		return callGraphs.size();
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		initKryo();
	}

	/** Return the permutation induced by the visit order of a depth-first visit.
	 *
	 * @param graph a graph.
	 * @param startingNode the only starting node of the visit, or -1 for a complete visit.
	 * @param internalNodes number of internal nodes in the graph
	 * @return  the permutation induced by the visit order of a depth-first visit.
	 */
	public static int[] bfsperm(final ImmutableGraph graph, final int startingNode, final int internalNodes) {
		final int n = graph.numNodes();

		final int[] visitOrder = new int[n];
		Arrays.fill(visitOrder, -1);
		final IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
		final LongArrayBitVector visited = LongArrayBitVector.ofLength(n);
		final ProgressLogger pl = new ProgressLogger(LOGGER);
		pl.expectedUpdates = n;
		pl.itemsName = "nodes";
		pl.start("Starting breadth-first visit...");
		Arrays.fill(visitOrder, -1);

		int internalPos = 0, externalPos = internalNodes;

		for(int i = 0; i < n; i++) {
			final int start = i == 0 && startingNode != -1 ? startingNode : i;
			if (visited.getBoolean(start)) continue;
			queue.enqueue(start);
			visited.set(start);

			int currentNode;
			final IntArrayList successors = new IntArrayList();

			while(! queue.isEmpty()) {
				currentNode = queue.dequeueInt();
				if (currentNode < internalNodes)
					visitOrder[internalPos++] = currentNode;
				else
					visitOrder[externalPos++] = currentNode;
				int degree = graph.outdegree(currentNode);
				final LazyIntIterator iterator = graph.successors(currentNode);

				successors.clear();
				while(degree-- != 0) {
					final int succ = iterator.nextInt();
					if (! visited.getBoolean(succ)) {
						successors.add(succ);
						visited.set(succ);
					}
				}

				final int[] randomSuccessors = successors.elements();
				IntArrays.quickSort(randomSuccessors, 0, successors.size(), (x, y) -> x - y);

				for(int j = successors.size(); j-- != 0;) queue.enqueue(randomSuccessors[j]);
				pl.update();
			}

			if (startingNode != -1) break;
		}

		pl.done();
		for (int i = 0; i < visitOrder.length; i++)
			assert (i < internalNodes) == (visitOrder[i] < internalNodes);
		return visitOrder;
	}


}
