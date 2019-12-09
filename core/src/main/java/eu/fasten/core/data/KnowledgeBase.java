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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.Random;

import org.apache.commons.lang3.SerializationUtils;
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
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastByteArrayInputStream;
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
 *   The knowledge base keeps the actual graphs in an associated {@linkplain #callGraphDB database},
 *   whereas all other informations about call graphs (both local information, such as {@link CallGraph#LID2GID}, and
 *   global information, such as {@link #genericURI2GID}) is kept in memory and serialized when the knowledge
 *   base is stored.
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
			assert genericURI.getProduct().equals(callGraph.product) : genericURI.getProduct() + " != " + callGraph.product;
			return FastenURI.create(callGraph.forge, callGraph.product, callGraph.version, genericURI.getRawNamespace(), genericURI.getRawEntity());
		}

		@Override
		public String toString() {
			return 	"[GID=" + gid +
					", LID=" + callGraphs.get(index).GID2LID.get(gid) +
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

	private static byte[] KB_KEY = Longs.toByteArray(-1);

	/** Maps schemeless, <em>generic</em> (i.e., without forge and without version, but with a product) FASTEN URIs to a unique identifier. */
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

	/** Instances represent call graphs and the associated metadata. Each call
	 *  graph corresponds to a specific release (product, version, forge), and has a unique
	 *  revision index. Its nodes are divided into internal nodes and external nodes
	 *  (the former have smaller values, the latter have larger values). Each node number is called
	 *  a local identifier (LID); LIDs are mapped to global identifiers (GIDs).
	 *  The GID of node x has a different product than the product of the call graph iff x is external.
	 *  External nodes have no outgoing arcs.
	 */
	public class CallGraph implements Serializable {
		private static final long serialVersionUID = 1L;
		/** Number of internal nodes (first {@link #nInternal} GIDs in {@link #LID2GID}). */
		public final int nInternal;
		/** Maps LIDs to GIDs. */
		public final long[] LID2GID;
		/** Inverse to {@link #LID2GID}: maps GIDs to LIDs. */
		public final Long2IntOpenHashMap GID2LID = new Long2IntOpenHashMap();
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
		private transient SoftReference<ImmutableGraph[]> graphs;

		// ALERT unsynchronized update of Knowledge Base maps.
		/** Creates a call graph from a {@link RevisionCallGraph}. All maps of the knowledge base (e.g. {@link KnowledgeBase#GIDAppearsIn}) are updated
		 *  appropriately. The graphs are stored in the database.
		 *
		 * @param g the revision call graph.
		 * @param index the revision index.
		 * @throws IOException
		 * @throws RocksDBException
		 */
		protected CallGraph(final RevisionCallGraph g, final long index) throws IOException, RocksDBException {
			product = g.product;
			version = g.version;
			forge = g.forge;
			this.index = index;

			LOGGER.debug("Analyzing fasten://" + forge + "!" + product + "$" + version);
			final ArrayList<FastenURI[]> arcs = g.graph;

			/*
			 * Pass over arc list, building schemeless generic pairs URIs.
			 * adding products where missing and skipping NULL_FASTEN_URIs.
			 * Source and target URIs are passed to addURI(), updating GenericURI2GID
			 * and its inverse GID2GenericURI.
			 */
			final ObjectArrayList<FastenURI> genericSources = new ObjectArrayList<>();
			final ObjectArrayList<FastenURI> genericTargets = new ObjectArrayList<>();
			final LongLinkedOpenHashSet internalGIDs = new LongLinkedOpenHashSet(); // List of internal GIDs
			final LongLinkedOpenHashSet externalGIDs = new LongLinkedOpenHashSet(); // List of external GIDs

			for(final FastenURI[] arc: arcs) {
				// TODO: this should be a raw product
				final FastenURI sourceSchemelessGenericURI = FastenURI.createSchemeless(null, product, null, arc[0].getRawNamespace(), arc[0].getRawEntity());
				final long sourceGID = addURI(sourceSchemelessGenericURI);
				addGidRev(GIDAppearsIn, sourceGID, index);
				internalGIDs.add(sourceGID);

				if (!FastenURI.NULL_FASTEN_URI.equals(arc[1])) {
					genericSources.add(sourceSchemelessGenericURI);
					// TODO: one should check that forge/version are null
					String targetRawProduct = arc[1].getRawProduct();
					final boolean internal = targetRawProduct == null;
					if (internal) targetRawProduct = product;
					final FastenURI target = FastenURI.createSchemeless(null, targetRawProduct, null, arc[1].getRawNamespace(), arc[1].getRawEntity());
					final long targetGID = addURI(target);

					if (internal) {
						addGidRev(GIDAppearsIn, targetGID, index);
						internalGIDs.add(targetGID);
					}
					else {
						addGidRev(GIDCalledBy, targetGID, index);
						externalGIDs.add(targetGID);
					}

					genericTargets.add(target);
				}
			}
			// Set up local bijection
			nInternal = internalGIDs.size();

			long[] l2g = new long[internalGIDs.size() + externalGIDs.size()];
			LongIterators.unwrap(internalGIDs.iterator(), l2g);
			LongIterators.unwrap(externalGIDs.iterator(), l2g, nInternal, Integer.MAX_VALUE);
			GID2LID.defaultReturnValue(-1);
			for(int i = 0; i < l2g.length; i++) GID2LID.put(l2g[i], i);

			// Create, store and load compressed versions of the graph and of the transpose.
			final ArrayListMutableGraph mutableGraph = new ArrayListMutableGraph(l2g.length);
			for(int i = 0; i < genericSources.size(); i++) {
				assert genericURI2GID.getLong(genericSources.get(i)) != -1;
				assert genericURI2GID.getLong(genericTargets.get(i)) != -1;
				mutableGraph.addArc(GID2LID.get(
						genericURI2GID.getLong(genericSources.get(i))),
						GID2LID.get(
								genericURI2GID.getLong(genericTargets.get(i))));
			}

			final File f = File.createTempFile(KnowledgeBase.class.getSimpleName(), ".tmpgraph");

            Properties graphProperties = new Properties(), transposeProperties = new Properties();
            FileInputStream propertyFile;
            
			// Compress, load and serialize graph
            int[] bfsperm = bfsperm(mutableGraph.immutableView(), -1, internalGIDs.size());
			ImmutableGraph graph = Transform.map(mutableGraph.immutableView(), bfsperm);
			BVGraph.store(graph, f.toString());
            propertyFile = new FileInputStream(f + BVGraph.PROPERTIES_EXTENSION);
            graphProperties.load(propertyFile);
            propertyFile.close();

			final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
			final ByteBufferOutput bbo = new ByteBufferOutput(fbaos);
			kryo.writeObject(bbo, BVGraph.load(f.toString()));

			// Permute LID2GID accordingly
			LID2GID = new long[l2g.length];
			for (int x = 0; x < l2g.length; x++) LID2GID[bfsperm[x]] = l2g[x];
			for(int i = 0; i < l2g.length; i++) GID2LID.put(LID2GID[i], i);
			
			// Compress, load and serialize transpose graph
			BVGraph.store(Transform.transpose(graph), f.toString());
            propertyFile = new FileInputStream(f + BVGraph.PROPERTIES_EXTENSION);
            transposeProperties.load(propertyFile);
            propertyFile.close();

			kryo.writeObject(bbo, BVGraph.load(f.toString()));
			
			// Write out properties
			kryo.writeObject(bbo, graphProperties);
			kryo.writeObject(bbo, transposeProperties);
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
		public ImmutableGraph[] graphs() {
			if (graphs != null) {
				final var graphs = this.graphs.get();
				if (graphs != null) return graphs;
			}
			try {
				// TODO: dynamic
				final byte[] buffer = new byte[1000000];
				callGraphDB.get(Longs.toByteArray(index), buffer);
				final Input input = new Input(buffer);
				assert kryo != null;
				final var graphs = new ImmutableGraph[] {kryo.readObject(input, BVGraph.class),  kryo.readObject(input, BVGraph.class)}; 
				this.graphs = new SoftReference<>(graphs);
				return graphs;
			} catch (final RocksDBException e) {
				throw new RuntimeException(e);
			}
		}

		/** Returns the properties of the call graph and its transpose in a 2-element array.
		 *
		 * @return an array containing the properties of the call graph and its transpose.
		 */
		public Properties[] graphProperties() {
			try {
				// TODO: dynamic
				final byte[] buffer = new byte[1000000];
				callGraphDB.get(Longs.toByteArray(index), buffer);
				final Input input = new Input(buffer);
				assert kryo != null;
				kryo.readObject(input, BVGraph.class); // throw away graph
				kryo.readObject(input, BVGraph.class); // throw away transpose
				final Properties[] properties = new Properties[] { kryo.readObject(input, Properties.class), kryo.readObject(input, Properties.class) };
				return properties;
				
			} catch (final RocksDBException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder();
			for(final NodeIterator nodeIterator = graphs()[0].nodeIterator(); nodeIterator.hasNext(); ) {
				final FastenURI u = GID2GenericURI.get(LID2GID[nodeIterator.nextInt()]);
				final LazyIntIterator successors = nodeIterator.successors();
				for(int s; (s = successors.nextInt()) != -1; )
					b.append(u).append('\t').append(GID2GenericURI.get(LID2GID[s])).append('\n');
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

	public static KnowledgeBase getInstance(final String kbDir) throws RocksDBException, ClassNotFoundException, IOException {
		RocksDB.loadLibrary();
		final Options options = new Options();
		options.setCreateIfMissing(true);

		final RocksDB db = RocksDB.open(options, kbDir);
		final byte[] array = db.get(KB_KEY);
		final KnowledgeBase kb = array != null ? (KnowledgeBase)BinIO.loadObject(new FastByteArrayInputStream(array)) : new KnowledgeBase();
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

		final ImmutableGraph graph = callGraph.graphs()[0];
		final LazyIntIterator s = graph.successors(callGraph.GID2LID.get(gid));

		final ObjectList<Node> result = new ObjectArrayList<>();
		int x;

		/* In the successor case, internal nodes can be added directly... */

		while((x = s.nextInt()) != -1 && x < callGraph.nInternal) result.add(new Node(callGraph.LID2GID[x], index));

		if (x == -1) return result;

		/* ...but external nodes must be search for in the revision call graphs in which they appear. */
		do {
			final long xGid = callGraph.LID2GID[x];
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

		final ImmutableGraph graph = callGraph.graphs()[1];
		final LazyIntIterator s = graph.successors(callGraph.GID2LID.get(gid));

		final ObjectList<Node> result = new ObjectArrayList<>();
		int x;

		/* In the predecessor case, all nodes returned by the graph are necessarily internal. */
		while((x = s.nextInt()) != -1) {
			assert x < callGraph.nInternal;
			result.add(new Node(callGraph.LID2GID[x], index));
		}

		/* To move backward in the call graph, we use GIDCalledBy to find revisions that might
		 * contain external nodes of the form <gid, index>. */
		do
			for(final LongIterator revisions = GIDCalledBy.get(gid).iterator(); revisions.hasNext();) {
				final long revIndex = revisions.nextLong();
				final CallGraph precCallGraph = callGraphs.get(revIndex);
				final ImmutableGraph transpose = precCallGraph.graphs()[1];
				final LazyIntIterator p = transpose.successors(precCallGraph.GID2LID.get(gid));
				for(int y; (y = p.nextInt()) != -1;) result.add(new Node(precCallGraph.LID2GID[y], revIndex));
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
	public synchronized void add(final RevisionCallGraph g, final long index) throws IOException, RocksDBException {
		callGraphs.put(index, new CallGraph(g, index));
	}

	@Override
	public void close() throws IOException {
		try {
			callGraphDB.put(KB_KEY, SerializationUtils.serialize(this));
		} catch (final RocksDBException e) {
			throw new IOException(e);
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
