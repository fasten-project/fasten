package eu.fasten.core.data;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;

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
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
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
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.NullInputStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.webgraph.ArrayListMutableGraph;
import it.unimi.dsi.webgraph.BVGraph;
import it.unimi.dsi.webgraph.ImmutableGraph;
import it.unimi.dsi.webgraph.LazyIntIterator;
import it.unimi.dsi.webgraph.NodeIterator;
import it.unimi.dsi.webgraph.Transform;

/**  Instances of this class represent the set of revision call graphs.
 */
public class KnowledgeBase implements Serializable, Closeable {
	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBase.class);

	/** Maps schemeless, <em>generic</em> (i.e., without forge and without version, but with a product) FASTEN URIs to a unique identifier. */
	protected final Object2LongMap<FastenURI> genericURI2GID;

	/** The inverse of {@link #genericURI2GID}. */
	protected final Long2ObjectMap<FastenURI> GID2GenericURI;

	/** Maps each GID to a list of revisions (identified by their index) in which the GID appears as an internal node. */
	protected final Long2ObjectMap<LongSet> GIDAppearsIn;

	/** Maps each GID to a list of revisions (identified by their index) in which the GID appears as an external node. */
	protected final Long2ObjectMap<LongSet> GIDCalledBy;

	/** Maps revision indices to the corresponding call graph. */
	public final Long2ObjectOpenHashMap<CallGraph> callGraphs;

	/** The RocksDB instance used by this indexer. */
	private transient RocksDB callGraphDB;

	/** The {@link Kryo} object used to serialize data to the database. */
	private transient Kryo kryo;

	public class CallGraph implements Serializable {
		private static final long serialVersionUID = 1L;
		/** Number of internal nodes (first {@link #nInternal} GIDs in {@link #LID2GID}). */
		public final int nInternal;
		public final long[] LID2GID;
		public final Long2IntOpenHashMap GID2LID = new Long2IntOpenHashMap();
		private final String product;
		private final String version;
		private final String forge;
		private final long index;

		protected CallGraph(final RevisionCallGraph g, final long index) throws IOException, RocksDBException {
			product = g.product;
			version = g.version;
			forge = g.forge;
			this.index = index;

			LOGGER.debug("Analyzing fasten://" + forge + "!" + product + "$" + version);
			final ArrayList<FastenURI[]> arcs = g.graph;

			/*
			 * Pass over arc list, building schemeless generic pairs URIs.
			 * adding products where missing and skipping NULL_FASTEN_URIs).
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

			LID2GID = new long[internalGIDs.size() + externalGIDs.size()];
			LongIterators.unwrap(internalGIDs.iterator(), LID2GID);
			LongIterators.unwrap(externalGIDs.iterator(), LID2GID, nInternal, Integer.MAX_VALUE);
			GID2LID.defaultReturnValue(-1);
			for(int i = 0; i < LID2GID.length; i++) GID2LID.put(LID2GID[i], i);

			// Create, store and load compressed versions of the graph and of the transpose.
			final ArrayListMutableGraph mutableGraph = new ArrayListMutableGraph(LID2GID.length);
			for(int i = 0; i < genericSources.size(); i++) {
				assert genericURI2GID.getLong(genericSources.get(i)) != -1;
				assert genericURI2GID.getLong(genericTargets.get(i)) != -1;
				mutableGraph.addArc(GID2LID.get(
						genericURI2GID.getLong(genericSources.get(i))),
						GID2LID.get(
								genericURI2GID.getLong(genericTargets.get(i))));
			}

			final File f = File.createTempFile(KnowledgeBase.class.getSimpleName(), ".tmpgraph");

			// Compress, load and serialize graph
			BVGraph.store(mutableGraph.immutableView(), f.toString());

			final FastByteArrayOutputStream fbaos = new FastByteArrayOutputStream();
			final ByteBufferOutput bbo = new ByteBufferOutput(fbaos);
			kryo.writeObject(bbo, BVGraph.load(f.toString()));

			// Compress, load and serialize transpose graph
			BVGraph.store(Transform.transpose(mutableGraph.immutableView()), f.toString());

			kryo.writeObject(bbo, BVGraph.load(f.toString()));
			bbo.flush();
			callGraphDB.put(Longs.toByteArray(index), 0, 8, fbaos.array, 0, fbaos.length);

			new File(f.toString() + BVGraph.PROPERTIES_EXTENSION).delete();
			new File(f.toString() + BVGraph.OFFSETS_EXTENSION).delete();
			new File(f.toString() + BVGraph.GRAPH_EXTENSION).delete();
			f.delete();
		}

		@SuppressWarnings("null")
		SoftReference<ImmutableGraph[]> graphs;

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
				final var graphs = new ImmutableGraph[] { kryo.readObject(input, BVGraph.class),  kryo.readObject(input, BVGraph.class) };
				this.graphs = new SoftReference<>(graphs);
				return graphs;
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

	private final class NamedResult extends AbstractObjectCollection<FastenURI> {
		private final ObjectLinkedOpenCustomHashSet<long[]> reaches;

		private NamedResult(final ObjectLinkedOpenCustomHashSet<long[]> reaches) {
			this.reaches = reaches;
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
			final ObjectListIterator<long[]> iterator = reaches.iterator();
			return new ObjectIterator<>() {

				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}

				@Override
				public FastenURI next() {
					return node2FastenURI(iterator.next());
				}
			};
		}
	}


	private void initKryo() {
		kryo = new Kryo();
		kryo.register(BVGraph.class, new BVGraphSerializer(kryo));
		kryo.register(byte[].class);
		kryo.register(InputBitStream.class);
		kryo.register(NullInputStream.class);
		kryo.register(EliasFanoMonotoneLongBigList.class, new JavaSerializer());
		kryo.register(MutableString.class, new FieldSerializer<>(kryo, MutableString.class));
	}

	public KnowledgeBase() {
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

	public void callGraphDB(final RocksDB db) {
		this.callGraphDB = db;
	}

	public void callGraphDB(final String dbName) throws RocksDBException {
		RocksDB.loadLibrary();
		final Options options = new Options();
		options.setCreateIfMissing(true);
		callGraphDB(RocksDB.open(options, dbName));
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
	 * @param node
	 * @return
	 */
	public ObjectList<long[]> successors(final long... node) {
		assert node.length == 2;
		final long gid = node[0];
		final long index = node[1];
		final CallGraph callGraph = callGraphs.get(index);
		assert callGraph != null;

		final ImmutableGraph graph = callGraph.graphs()[0];
		final LazyIntIterator s = graph.successors(callGraph.GID2LID.get(gid));

		final ObjectList<long[]> result = new ObjectArrayList<>();
		int x;

		/* In the successor case, internal nodes can be added directly... */

		while((x = s.nextInt()) != -1 && x < callGraph.nInternal) result.add(new long[] { callGraph.LID2GID[x], index } );

		if (x == -1) return result;

		/* ...but external nodes must be search for in the revision call graphs in which they appear. */
		do {
			final long xGid = callGraph.LID2GID[x];
			for(final LongIterator revisions = GIDAppearsIn.get(xGid).iterator(); revisions.hasNext();)
				result.add(new long[] { xGid, revisions.nextLong() });
		} while((x = s.nextInt()) != -1);

		return result;
	}

	public ObjectList<long[]> predecessors(final long... node) {
		assert node.length == 2;
		final long gid = node[0];
		final long index = node[1];
		final CallGraph callGraph = callGraphs.get(index);
		assert callGraph != null;

		final ImmutableGraph graph = callGraph.graphs()[1];
		final LazyIntIterator s = graph.successors(callGraph.GID2LID.get(gid));

		final ObjectList<long[]> result = new ObjectArrayList<>();
		int x;

		/* In the predecessor case, all nodes returned by the graph are necessarily internal. */
		while((x = s.nextInt()) != -1) {
			assert x < callGraph.nInternal;
			result.add(new long[] { callGraph.LID2GID[x], index } );
		}

		/* To move backward in the call graph, we use GIDCalledBy to find revisions that might
		 * contain external nodes of the form <gid, index>. */
		do
			for(final LongIterator revisions = GIDCalledBy.get(gid).iterator(); revisions.hasNext();) {
				final long revIndex = revisions.nextLong();
				final CallGraph precCallGraph = callGraphs.get(revIndex);
				final ImmutableGraph transpose = precCallGraph.graphs()[1];
				final LazyIntIterator p = transpose.successors(precCallGraph.GID2LID.get(gid));
				for(int y; (y = p.nextInt()) != -1;) result.add(new long[] { precCallGraph.LID2GID[y], revIndex });
			}
		while((x = s.nextInt()) != -1);

		return result;
	}

	public FastenURI node2FastenURI(final long...node) {
		assert node.length == 2;
		final long gid = node[0];
		final long index = node[1];
		final FastenURI genericURI = GID2GenericURI.get(gid);
		if (genericURI == null) return null;
		final CallGraph callGraph = callGraphs.get(index);
		assert genericURI.getProduct().equals(callGraph.product) : genericURI.getProduct() + " != " + callGraph.product;
		return FastenURI.create(callGraph.forge, callGraph.product, callGraph.version, genericURI.getRawNamespace(), genericURI.getRawEntity());
	}

	public long[] fastenURI2Node(final FastenURI fastenURI) {
		if (fastenURI.getVersion() == null) throw new IllegalArgumentException("The FASTEN URI must be versioned");
		final FastenURI genericURI = FastenURI.createSchemeless(null, fastenURI.getRawProduct(), null, fastenURI.getRawNamespace(), fastenURI.getRawEntity());
		final long gid = genericURI2GID.getLong(genericURI);
		if (gid == -1) return null;
		final String version = fastenURI.getVersion();
		for(final long index: GIDAppearsIn.get(gid))
			if (version.equals(callGraphs.get(index).version)) return new long[] { gid, index };

		return null;
	}

	public ObjectList<FastenURI> genericURI2URIs(final FastenURI genericURI) {
		if (genericURI.getVersion() != null || genericURI.getScheme() != null) throw new IllegalArgumentException("The FASTEN URI must be generic and schemeless");
		final long gid = genericURI2GID.getLong(genericURI);
		if (gid == -1) return null;
		final ObjectArrayList<FastenURI> result = new ObjectArrayList<>();
		for(final long index: GIDAppearsIn.get(gid)) result.add(FastenURI.createSchemeless(genericURI.getRawForge(), genericURI.getRawProduct(), callGraphs.get(index).version, genericURI.getRawNamespace(), genericURI.getRawEntity()));
		return result;
	}

	public synchronized ObjectLinkedOpenCustomHashSet<long[]> reaches(final long... start) {
		assert start != null;
		assert start.length == 2;
		final ObjectLinkedOpenCustomHashSet<long[]> result = new ObjectLinkedOpenCustomHashSet<>(LongArrays.HASH_STRATEGY);
		// Visit queue
		final ObjectArrayFIFOQueue<long[]> queue = new ObjectArrayFIFOQueue<>();
		queue.enqueue(start);

		while(!queue.isEmpty()) {
			final long[] node = queue.dequeue();
			assert node != null;
			assert node.length == 2;
			if (result.add(node)) for(final long[] s: successors(node))
				if (!result.contains(s)) queue.enqueue(s);
		}

		return result;
	}

	public Collection<FastenURI> reaches(final FastenURI fastenURI) {
		final long[] start = fastenURI2Node(fastenURI);
		if (start == null) return null;
		return new NamedResult(reaches(start));
	}

	public synchronized ObjectLinkedOpenCustomHashSet<long[]> coreaches(final long... start) {
		assert start != null;
		assert start.length == 2;
		final ObjectLinkedOpenCustomHashSet<long[]> result = new ObjectLinkedOpenCustomHashSet<>(LongArrays.HASH_STRATEGY);
		// Visit queue
		final ObjectArrayFIFOQueue<long[]> queue = new ObjectArrayFIFOQueue<>();
		queue.enqueue(start);

		while(!queue.isEmpty()) {
			final long[] node = queue.dequeue();
			assert node != null;
			assert node.length == 2;
			if (result.add(node))
				for(final long[] s: predecessors(node))
					if (!result.contains(s)) queue.enqueue(s);
		}

		return result;
	}

	public synchronized Collection<FastenURI> coreaches(final FastenURI fastenURI) {
		final long[] start = fastenURI2Node(fastenURI);
		if (start == null) return null;
		return new NamedResult(coreaches(start));
	}

	public synchronized void add(final RevisionCallGraph g, final long index) throws IOException, RocksDBException {
		callGraphs.put(index, new CallGraph(g, index));
	}

	@Override
	public void close() throws IOException {
		callGraphDB.close();
	}

	public long size() {
		return callGraphs.size();
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		initKryo();
	}

}
