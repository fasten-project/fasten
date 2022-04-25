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

import java.util.function.ToLongFunction;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/**
 * A class implementing a fast LRU cache bounded by memory size.
 * 
 * <p>Elements are purged by the cache following an LRU logic to maintain
 * the cache size within the provided bound.
 * 
 * <p>This class is thread safe.
 */

public class SizeBoundCache<T>  {
	/** A map from GIDs to objects of type {@code T}. */
	private final Long2ObjectLinkedOpenHashMap<T> cache = new Long2ObjectLinkedOpenHashMap<>();
	/** A function measuring the size of an instance of {@code T}.*/
	private final ToLongFunction<T> size;
	/** The maximum size in bytes of the cache. */
	private final long maxSize;
	/** The current size in bytes of the cache. */
	private long currentSize;

	/** Creates a new size-bound cache.
	 * 
	 * @param maxSize the maximum size of the cache in bytes.
	 * @param size a function estimating the size in bytes of the objects of type {@code T}.
	 */
	public SizeBoundCache(long maxSize, final ToLongFunction<T> size) {
		this.maxSize = maxSize;
		this.size = size;
	}

	/** Puts a key/value pair in the cache.
	 * 
	 * @param key a key.
	 * @param o an associated value.
	 */
	public synchronized void put(final long key, T o) {
		cache.putAndMoveToFirst(key, o);
		currentSize += size.applyAsLong(o);
		while(currentSize > maxSize) currentSize -= size.applyAsLong(cache.removeLast());
	}

	/** Retrieves the value associated with a key in the cache.
	 * 
	 * @param key a key.
	 * @return the associated value, or {@code null} if no such value is present.
	 */
	public synchronized T get(final long key) {
		return cache.get(key);
	}

	/** Returns the number of elements in the cache.
	  *
	  * @return the number of elements in the cache.
	  */
	public synchronized int size() {
		return cache.size();
	}

	/** Returns the estimated number of bytes currently contained in the cache.
	  *
	  * @return the estimated number of bytes currently contained in the cache.
	  */
	public long bytes() {
		return currentSize;
	}

	/** Returns the estimated occupied fraction of the cache.
	  *
	  * @return the estimated occupied fraction of the cache.
	  */
	public double occupation() {
		return (double)currentSize / maxSize;
	}
}
