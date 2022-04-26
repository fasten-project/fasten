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

import eu.fasten.core.data.callableindex.RocksDao;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

/**
 * A class keeping track in a cache of which revisions are available in a callable index.
 * 
 * <p>
 * This class is thread safe.
 */

public class RevisionCache {
	/**
	 * A map from GIDs to booleans. Returns true upon a revision that is present in the callable index.
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
	 * Puts a key/value pair in the cache.
	 * 
	 * @param gid the GID of a revision.
	 * @param o an associated value.
	 */
	public boolean contains(final long gid) {
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
