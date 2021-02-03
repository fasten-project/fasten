/* Licensed to the Apache Software Foundation (ASF) under one
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

package eu.fasten.core.search.predicate;

import java.util.NoSuchElementException;
import java.util.function.Predicate;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record2;
import org.jooq.Record3;
import org.json.JSONObject;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.search.Util;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/** A factory that builds search predicates resolving them against a specific database instance.
 *  All predicates are about a given callable (specified with its {@link Callables#CALLABLES#ID}); some
 *  of them, in particular, determine if the JSON metadata associated with the callable satisfy a given property.
 *  Notice that a callable has three different sources of metadata associated with it:
 *  <ul>
 *  	<li>the actual metadata of the given callable ({@link Callables#CALLABLES#METADATA});
 *  	<li>the metadata associated with the module of the given callable ({@link Modules#MODULES#METADATA});
 *  	<li>the metadata associated with the package version of the given callable ({@link PackageVersions#PACKAGE_VERSIONS#METADATA}).
 *  </ul>
 *  The factory is able to produce predicates for all types of sources (specified using the {@link MetadataSource} enum).
 *  This class keeps a LRU cache of the metadata returned most recently, and also of the association between callable IDs,
 *  module IDs and package version IDs. 
 */
public class PredicateFactory {
	/** Maximum size of all metadata caches (all maps are kept trimmed to this size). */
	private static final int METADATA_MAP_MAXSIZE = 1024;
	/** Maximum size of all ID association caches (all maps are kept trimmed to this size). */
	private static final int LONG_MAP_MAXSIZE = 1024*1024;
	
	private DSLContext dbContext;
	/** LRU cache of the last metadata from the {@link Callables#CALLABLES} table. */
	private Long2ObjectLinkedOpenHashMap<JSONObject> callableGID2callableMetadata;
	/** LRU cache of the last metadata from the {@link Modules#MODULES} table. */
	private Long2ObjectLinkedOpenHashMap<JSONObject> moduleGID2moduleMetadata;
	/** LRU cache of the last metadata from the {@link PackageVersions#PACKAGE_VERSIONS} table. */
	private Long2ObjectLinkedOpenHashMap<JSONObject> packageVersionGID2packageVersionMetadata;
	/** LRU cache of the map between {@link Callables#CALLABLES#ID} and {@link Modules#MODULES#ID}. */
	private Long2LongLinkedOpenHashMap callableGID2moduleGID;
	/** LRU cache of the map between {@link Modules#MODULES#ID} and {@link PackageVersions#PACKAGE_VERSIONS#ID}. */
	private Long2LongLinkedOpenHashMap moduleGID2packageVersionGID;

	/** A factory for predicates that will be matched against a given database.
	 * 
	 * @param dbContext the db context that will be used to match predicates. 
	 */
	public PredicateFactory(final DSLContext dbContext) {
		this.dbContext = dbContext;
		this.callableGID2callableMetadata = new Long2ObjectLinkedOpenHashMap<>();
		this.moduleGID2moduleMetadata = new Long2ObjectLinkedOpenHashMap<>();
		this.packageVersionGID2packageVersionMetadata = new Long2ObjectLinkedOpenHashMap<>();
		this.callableGID2moduleGID = new Long2LongLinkedOpenHashMap();
		callableGID2moduleGID.defaultReturnValue(-1);
		this.moduleGID2packageVersionGID = new Long2LongLinkedOpenHashMap();
		moduleGID2packageVersionGID.defaultReturnValue(-1);
	}
	
	/** An enum corresponding to the possible sources (i.e., database table) of metadata. */
	public enum MetadataSource {
		/** It refers to the {@link Callables#CALLABLES#METADATA} source. */
		CALLABLE, 
		/** It refers to the {@link Modules#MODULES#METADATA} source. */
		MODULE, 
		/** It refers to the {@link PackageVersions#PACKAGE_VERSIONS#METADATA} source. */
		PACKAGE_VERSION
	}
	
	/** Puts a given key/value pair in a map, and guarantees that the pair is in the first
	 *  position, ensuring also that the map size does not exceed a given maximum.
	 * 
	 * @param map the map to be modified.
	 * @param key the key.
	 * @param value the value associated to the key.
	 * @param maxSize the maximum size.
	 */
	private static <T> void putLRUMap(Long2ObjectLinkedOpenHashMap<T> map, final long key, final T value, final int maxSize) {
		map.putAndMoveToFirst(key, value);
		if (map.size() > maxSize) map.removeLast();
	}

	/** Puts a given key/value pair in a map, and guarantees that the pair is in the first
	 *  position, ensuring also that the map size does not exceed a given maximum.
	 * 
	 * @param map the map to be modified.
	 * @param key the key.
	 * @param value the value associated to the key.
	 * @param maxSize the maximum size.
	 */
	private static void putLRUMap(Long2LongLinkedOpenHashMap map, final long key, final long value, final int maxSize) {
		map.putAndMoveToFirst(key, value);
		if (map.size() > maxSize) map.removeLastLong();
	}
	
	/** Returns the metadata field of a given callable.
	 * 
	 * @param callableGID the callable GID.
	 * @return the metadata field associated to it.
	 */
	private JSONObject getCallableMetadata(final long callableGID) {
		JSONObject jsonMetadata = callableGID2callableMetadata.get(callableGID);
		if (jsonMetadata == null) {
			jsonMetadata = new JSONObject(dbContext.select(Callables.CALLABLES.METADATA).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(callableGID)).fetchOne().component1().data());
			putLRUMap(callableGID2callableMetadata, callableGID, jsonMetadata, METADATA_MAP_MAXSIZE);
		}
		return jsonMetadata;
	}

	/** Returns the metadata field of the package version corresponding to a given callable.
	 * 
	 * @param callableGID the callable GID.
	 * @return the metadata field associated to the package version of the callable.
	 */
	private JSONObject getModuleMetadata(final long callableGID) {
		long moduleGID = callableGID2moduleGID.get(callableGID);
		JSONObject jsonMetadata = moduleGID >= 0? moduleGID2moduleMetadata.get(moduleGID) : null;
		if (jsonMetadata == null) {
			Record2<JSONB, Long> queryResult = dbContext.select(Modules.MODULES.METADATA, Modules.MODULES.ID)
					.from(Callables.CALLABLES)
					.join(Modules.MODULES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
					.where(Callables.CALLABLES.ID.eq(callableGID)).fetchOne();

			moduleGID = queryResult.component2().longValue();
			jsonMetadata = new JSONObject(queryResult.component1().data());
			putLRUMap(callableGID2moduleGID, callableGID, moduleGID, LONG_MAP_MAXSIZE);
			putLRUMap(moduleGID2moduleMetadata, moduleGID, jsonMetadata, METADATA_MAP_MAXSIZE);
		}
		return jsonMetadata;
	}

	/** Returns the metadata field of the package version corresponding to a given callable.
	 * 
	 * @param callableGID the callable id.
	 * @return the metadata field associated to the package version of the callable.
	 */
	private JSONObject getPackageVersionMetadata(final long callableGID) {
		long moduleGID = callableGID2moduleGID.get(callableGID);
		long packageVersionGID = callableGID >= 0? moduleGID2packageVersionGID.get(moduleGID) : -1;
		JSONObject jsonMetadata = packageVersionGID >= 0? packageVersionGID2packageVersionMetadata.get(packageVersionGID) : null;
		if (jsonMetadata == null ) {
			Record3<JSONB, Long, Long> queryResult = dbContext.select(PackageVersions.PACKAGE_VERSIONS.METADATA, PackageVersions.PACKAGE_VERSIONS.ID, Modules.MODULES.ID)
					.from(Callables.CALLABLES)
					.join(Modules.MODULES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
					.join(PackageVersions.PACKAGE_VERSIONS).on(Modules.MODULES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID))
					.where(Callables.CALLABLES.ID.eq(callableGID)).fetchOne();
			packageVersionGID = queryResult.component2().longValue();
			moduleGID = queryResult.component3().longValue();
			jsonMetadata = new JSONObject(queryResult.component1().data());
			putLRUMap(callableGID2moduleGID, callableGID, moduleGID, LONG_MAP_MAXSIZE);
			putLRUMap(moduleGID2packageVersionGID, moduleGID, packageVersionGID, LONG_MAP_MAXSIZE);
			putLRUMap(packageVersionGID2packageVersionMetadata, packageVersionGID, jsonMetadata, METADATA_MAP_MAXSIZE);
		}
		return jsonMetadata;
	}
	
	/** Returns the metadata of a given callable for a specific source.
	 * 
	 * @param source the source of the metadata we want to obtain.
	 * @param callableGID the {@link Callables#CALLABLES#ID} of the callable under consideration.
	 * @return the JSON metadata.
	 */
	private JSONObject getMetadata(final MetadataSource source, final long callableGID) {
		switch(source) {
		case CALLABLE: return getCallableMetadata(callableGID);
		case MODULE: return getModuleMetadata(callableGID);
		case PACKAGE_VERSION: return getPackageVersionMetadata(callableGID);
		default: throw new NoSuchElementException("Unknown source");
		}
	}

	/** A predicate that holds true if a given metadata contains a key/value pair with a specific key and
	 *  a value satisfying a certain property. 
	 * 
	 * @param source the source containing the metadata.
	 * @param key the (exact) key that the callable must contain.
	 * @param valuePredicate the predicate that the value must satisfy. It can be of any type, but its {@link #toString()} method
	 * is used to get the value.
	 * @return the predicate.
	 */
	public MetadataContains metadataContains(final MetadataSource source, final String key, final Predicate<String> valuePredicate) {
		return t -> getMetadata(source, t).has(key) && valuePredicate.test(getMetadata(source, t).get(key).toString());
	}

	/** A predicate that holds true if a given metadata contains a specific key. 
	 * 
	 * @param source the source containing the metadata.
	 * @param key the (exact) key that the callable must contain.
	 * @return the predicate.
	 */
	public MetadataContains metadataContains(final MetadataSource source, final String key) {
		return metadataContains(source, key, x -> true);
	}

	/** A predicate that holds true if a given metadata contains a specific key/value pair. 
	 * 
	 * @param source the source containing the metadata.
	 * @param key the (exact) key that the callable must contain.
	 * @param value the (exact) string value that the callable must contain associated with the key. It can be of any type, but its {@link #toString()} method
	 * is used to get the value.
	 * @return the predicate.
	 */
	public MetadataContains metadataContains(final MetadataSource source, final String key, final String value) {
		return metadataContains(source, key, x -> value.equals(x));
	}
	
	/** A predicate that holds true if a given metadata, queried with a certain {@link org.json.JSONPointer}, has
	 *  a string value satisfying some predicate. 
	 * 
	 * @param source the source containing the metadata.
	 * @param jsonPointer the {@link org.json.JSONPointer} (<a href="https://tools.ietf.org/html/rfc6901">RFC 6901</a>).
	 * @param valuePredicate a predicate that is matched against the value returned {@linkplain JSONObject#query(String) by the JSONPointer
	 * query}, after {@linkplain #toString() conversion to a string}. 
	 * @return true iff the metadata {@link JSONObject} matches the jsonPointer and the corresponding value has a string representation
	 * that satisfies the predicate.
	 */
	public MetadataContains metadataQueryJSONPointer(final MetadataSource source, final String jsonPointer, final Predicate<String> valuePredicate) {
		return t -> {
			System.out.println("METADATA: " + getMetadata(source, t));
			System.out.println("JSONPointer: " + jsonPointer);
			System.out.println("Query result: " + getMetadata(source, t).query(jsonPointer));
			return valuePredicate.test(getMetadata(source, t).query(jsonPointer).toString());
		};
	}

		
	/** A predicate that holds true if the callable {@link FastenURI} matches a certain predicate.
	 * 
	 * @param fastenURIPredicate the predicate that the callable {@link FastenURI} is matched against.
	 * @return the predicate.
	 */
	public FastenURIMatches fastenURIMatches(final Predicate<FastenURI> fastenURIPredicate) {
		return x -> fastenURIPredicate.test(Util.getCallableName(x, dbContext));
	}
	
	public static void main(String[] args) {
	    var a = new JSONObject("{\"final\": false, \"access\": \"public\", \"superClasses\": [\"/it.unimi.dsi.webgraph/ImmutableGraph\", \"/java.lang/Object\"], \"superInterfaces\": [\"/it.unimi.dsi.lang/FlyweightPrototype\", \"/it.unimi.dsi.webgraph/CompressionFlags\"]}");
	    System.out.println(a.query("/superInterfaces")); 
	}

}