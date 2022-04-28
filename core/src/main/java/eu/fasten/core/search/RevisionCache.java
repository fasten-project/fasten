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

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import eu.fasten.core.data.callableindex.RocksDao;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

/**
 * A class keeping track in a cache of which revisions are available (both graph and metadata) in a callable index.
 * 
 * <p>
 * This class is thread safe.
 */

public class RevisionCache {
	/**
	 * A map from GIDs to booleans. Returns true upon a revision that is present in the callable index (both graph and metadata).
	 * false upon a revision that is missing. A missing GID means we have to inquire the callable index
	 * directly.
	 */
	private final Long2BooleanOpenHashMap cache = new Long2BooleanOpenHashMap(1000, Hash.FAST_LOAD_FACTOR);

	/**
	 * The underlying callable index.
	 */
	private RocksDao rocksDao;

	/**
	 * Creates a new revision cache.
	 * 
	 * @param maxSize the maximum size of the cache in bytes.
	 * @param size a function estimating the size in bytes of the objects of type {@code T}.
	 */
	public RevisionCache(RocksDao rocksDao) {
		this.rocksDao = rocksDao;
	}

	/**
	 * Returns whether the underlying index might contain a GID.
	 * 
	 * <p>This method is based on {@link RocksDao#mayContain(long)}, which in turn is based
	 * on {@link RocksDB#keyMayExist(byte[], org.rocksdb.Holder)},
	 * and shares its limitations. Note that because of the behavior of the latter,
	 * only negative results a stored in the cache.
	 * 
	 * <p>For an exact answer, use {@link #contains(long)} (which is more disk intensive).
	 * 
	 * @param gid the GID of a revision.
	 * @return false if the GID does not belong to the callable index; true may be
	 * returned even if the GID does not belong to the callable index.
	 * @see #contains(long) 
	 */
	public boolean mayContain(final long gid) {
		synchronized (cache) {
			if (cache.containsKey(gid)) return cache.get(gid);
		}
		final boolean result = rocksDao.mayContain(gid);
		if (! result) synchronized (cache) {
			cache.put(gid, result);
		}
		return result;
	}

	/**
	 * Returns whether the underlying index contains a GID.
	 * 
	 * <p>For a faster, approximate answer, use {@link #mayContain(long)} (which is less disk intensive).
	 * 
	 * @param gid the GID of a revision.
	 * @return whether the GID belongs to the callable index.
	 * @see #mayContain(long) 
	 */
	public boolean contains(final long gid) throws RocksDBException {
		synchronized (cache) {
			if (cache.containsKey(gid)) return cache.get(gid);
		}
		final boolean result = rocksDao.contains(gid);
		synchronized (cache) {
			cache.put(gid, result);
		}
		return result;
	}
}
