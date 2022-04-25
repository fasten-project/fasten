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

package eu.fasten.core.search;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.SerializationUtils;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import com.google.common.primitives.Longs;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

/**
 * A persistent cache for merged graphs, dependencies and components.
 * 
 * <p>All accessors are synchronized and can be used by multiple threads.
 */

public class PersistentCache implements AutoCloseable {
	/** Marker singleton for revision whose merged graph cannot be computed. */
	public static final ArrayImmutableDirectedGraph NO_GRAPH = new ArrayImmutableDirectedGraph.Builder().build();

	/** The persistent cache. */
	private final RocksDB cache;
	/** The handle for merged graphs in the persistent cache. */
	private ColumnFamilyHandle mergedHandle;
	/** The handle for dependencies in the persistent cache. */
	private ColumnFamilyHandle dependenciesHandle;
	/** A size-bound cache from GIDs to memory-cached merged graphs. */
	private final SizeBoundCache<ArrayImmutableDirectedGraph> mergedCache = new SizeBoundCache<>(64L << 30, x -> x.numNodes() * 32L + x.numArcs() * 8L);
	/** A size-bound cache from GIDs to memory-cached resolved dependency sets. */
	private final SizeBoundCache<LongLinkedOpenHashSet> depsCache = new SizeBoundCache<>(8L << 30, x -> x.size() * 16L);

	/** Structure gathering the fields returned by {@link #openCache(String, boolean)}. */
	public static final class RocksDBData {
		/** A reference to the RocksDB cache. */
		public final RocksDB cache;
		/** The available columns of the RocksDB cache; in order:
		 * <ul>
		 * <li>the merged graph;
		 * <li>the known dependencies of the merged graph at merge time;
		 * <li>the actual components used to build the merged graph (as some dependencies might be missing at merge time).
		 * </ul> 
		 */
		public final List<ColumnFamilyHandle> columnFamilyHandles;

		public RocksDBData(RocksDB cache, List<ColumnFamilyHandle> columnFamilyHandles) {
			this.cache = cache;
			this.columnFamilyHandles = columnFamilyHandles;
		}
	}
	
	/** Utility method to open the persistent cache.
	 * 
	 * @param cacheDir the path to the persistent cache.
	 * @param readOnly open RocksDB read-only.
	 * @return a {@link RocksDBData} structure.
	 */
	public static final RocksDBData openCache(final String cacheDir, final boolean readOnly) throws RocksDBException {
		RocksDB.loadLibrary();
		final ColumnFamilyOptions defaultOptions = new ColumnFamilyOptions();
		@SuppressWarnings("resource")
		final DBOptions dbOptions = new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
		final List<ColumnFamilyDescriptor> cfDescriptors = List.of(new ColumnFamilyDescriptor(
				RocksDB.DEFAULT_COLUMN_FAMILY, defaultOptions), 
				new ColumnFamilyDescriptor("merged".getBytes(), defaultOptions), 
				new ColumnFamilyDescriptor("dependencies".getBytes(), defaultOptions),
				new ColumnFamilyDescriptor("components".getBytes(), defaultOptions));
		final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
		return new RocksDBData(readOnly ?
				RocksDB.openReadOnly(dbOptions, cacheDir, cfDescriptors, columnFamilyHandles) :
				RocksDB.open(dbOptions, cacheDir, cfDescriptors, columnFamilyHandles), columnFamilyHandles); 
	}

	public PersistentCache(final String cacheDir, final boolean readOnly) throws RocksDBException {
		RocksDBData data = openCache(cacheDir, readOnly);
		this.cache = data.cache;
		mergedHandle = data.columnFamilyHandles.get(0);
		dependenciesHandle = data.columnFamilyHandles.get(1);
	}
	
	/** Puts a merged graph in the cache.
	 * 
	 * @param key the GID of the revision upon which the merged graph is based.
	 * @param graph the merged graph; if {@code null}, the memory cache will store
	 * {@link #NO_GRAPH} and the persistent cache will store a zero-length byte array.
	 */
	public synchronized void putMerged(final long key, ArrayImmutableDirectedGraph graph) throws RocksDBException {
		if (graph == null) {
			mergedCache.put(key, NO_GRAPH);
			cache.put(mergedHandle, Longs.toByteArray(key), new byte[0]);
		} else {
			mergedCache.put(key, graph);
			cache.put(mergedHandle, Longs.toByteArray(key), SerializationUtils.serialize(graph));
		}
	}
	
	/** Puts a resolved dependency set in the cache.
	 * 
	 * @param key the GID of the revision upon which the resolved dependency set is based.
	 * @param deps the resolved dependency set.
	 */
	public synchronized void putDeps(final long key, LongLinkedOpenHashSet deps) throws RocksDBException {
		depsCache.put(key, deps);
		cache.put(dependenciesHandle, Longs.toByteArray(key), SerializationUtils.serialize(deps));				
	}

	/** Attempts to retrieve a merged graph from the cache.
	 * 
	 * <p>If the graph can be retrieved from the memory cache, it will be returned; otherwise,
	 * this method will attempt to retrieve the graph from the persistent cache and, in case
	 * of success, the graph will be added to the memory cache.
	 * 
	 * @param key the GID of the revision upon which the requested merged graph is based.
	 * @return {@code null} if the graph is not present in the cache; {@link #NO_GRAPH} if
	 * it is known that the graph cannot be merged; a merged graph for the given GID, otherwise. 
	 */
	public synchronized ArrayImmutableDirectedGraph getMerged(final long key) throws RocksDBException {
		ArrayImmutableDirectedGraph merged = mergedCache.get(key);
		if (merged != null) return merged;
		final byte[] array = cache.get(mergedHandle, Longs.toByteArray(key));
		if (array == null) return null;
		if (array.length == 0) merged = NO_GRAPH;
		else merged = SerializationUtils.deserialize(array);
		mergedCache.put(key, merged);
		return merged;
	}
	
	/** Attempts to retrieve a resolved dependency set from the cache.
	 * 
	 * <p>If the dependency set can be retrieved from the memory cache, it will be returned; otherwise,
	 * this method will attempt to retrieve the set from the persistent cache and, in case
	 * of success, the set will be added to the memory cache.
	 * 
	 * @param key the GID of the revision for which the resolved dependency set is requested.
	 * @return the resolved dependency set for the given GID, if present; {@code null}, otherwise. 
	 */
	public synchronized LongLinkedOpenHashSet getDeps(final long key) throws RocksDBException {
		LongLinkedOpenHashSet deps = depsCache.get(key);
		if (deps != null) return deps;
		final byte[] array = cache.get(dependenciesHandle, Longs.toByteArray(key));
		if (array == null) return null;
		deps = SerializationUtils.deserialize(array);
		depsCache.put(key, deps);
		return deps;
	}
	
	@Override
	public synchronized void close() throws Exception {
		cache.close();
	}
}
