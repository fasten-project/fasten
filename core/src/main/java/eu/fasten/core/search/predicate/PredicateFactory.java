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
	private Long2ObjectLinkedOpenHashMap<JSONObject> callableId2callableMetadata;
	/** LRU cache of the last metadata from the {@link Modules#MODULES} table. */
	private Long2ObjectLinkedOpenHashMap<JSONObject> moduleId2moduleMetadata;
	/** LRU cache of the last metadata from the {@link PackageVersions#PACKAGE_VERSIONS} table. */
	private Long2ObjectLinkedOpenHashMap<JSONObject> packageVersionId2packageVersionMetadata;
	/** LRU cache of the map between {@link Callables#CALLABLES#ID} and {@link Modules#MODULES#ID}. */
	private Long2LongLinkedOpenHashMap callableId2moduleId;
	/** LRU cache of the map between {@link Modules#MODULES#ID} and {@link PackageVersions#PACKAGE_VERSIONS#ID}. */
	private Long2LongLinkedOpenHashMap moduleId2packageVersionId;

	/** A factory for predicates that will be matched against a given database.
	 * 
	 * @param dbContext the db context that will be used to match predicates. 
	 */
	public PredicateFactory(final DSLContext dbContext) {
		this.dbContext = dbContext;
		this.callableId2callableMetadata = new Long2ObjectLinkedOpenHashMap<>();
		this.callableId2moduleId = new Long2LongLinkedOpenHashMap();
		callableId2moduleId.defaultReturnValue(-1);
		this.moduleId2packageVersionId = new Long2LongLinkedOpenHashMap();
		moduleId2packageVersionId.defaultReturnValue(-1);
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
	
    private FastenURI getCallableName(final long id) {
        return FastenURI.create(dbContext.select(Callables.CALLABLES.FASTEN_URI).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(id)).fetchOne().component1());
    }


	/** Returns the metadata field of a given callable.
	 * 
	 * @param callableId the callable id.
	 * @return the metadata field associated to it.
	 */
	private JSONObject getCallableMetadata(final long callableId) {
		JSONObject jsonMetadata = callableId2callableMetadata.get(callableId);
		if (jsonMetadata == null) {
			jsonMetadata = new JSONObject(dbContext.select(Callables.CALLABLES.METADATA).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(callableId)).fetchOne().component1().data());
			putLRUMap(callableId2callableMetadata, callableId, jsonMetadata, METADATA_MAP_MAXSIZE);
		}
		return jsonMetadata;
	}

	/** Returns the metadata field of the package version corresponding to a given callable.
	 * 
	 * @param callableId the callable id.
	 * @return the metadata field associated to the package version of the callable.
	 */
	private JSONObject getModuleMetadata(final long callableId) {
		long moduleId = callableId2moduleId.get(callableId);
		JSONObject jsonMetadata = moduleId >= 0? moduleId2moduleMetadata.get(moduleId) : null;
		if (jsonMetadata == null) {
			Record2<JSONB, Long> queryResult = dbContext.select(Modules.MODULES.METADATA, Modules.MODULES.ID)
					.from(Modules.MODULES)
					.join(Callables.CALLABLES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
					.where(Callables.CALLABLES.ID.eq(callableId)).fetchOne();

			moduleId = queryResult.component2().longValue();
			jsonMetadata = new JSONObject(queryResult.component1().data());
			putLRUMap(callableId2moduleId, callableId, moduleId, LONG_MAP_MAXSIZE);
			putLRUMap(moduleId2moduleMetadata, moduleId, jsonMetadata, METADATA_MAP_MAXSIZE);
		}
		return jsonMetadata;
	}

	/** Returns the metadata field of the package version corresponding to a given callable.
	 * 
	 * @param callableId the callable id.
	 * @return the metadata field associated to the package version of the callable.
	 */
	private JSONObject getPackageVersionMetadata(final long callableId) {
		long moduleId = callableId2moduleId.get(callableId);
		long packageVersionId = callableId >= 0? moduleId2packageVersionId.get(moduleId) : -1;
		JSONObject jsonMetadata = packageVersionId >= 0? packageVersionId2packageVersionMetadata.get(packageVersionId) : null;
		if (jsonMetadata == null ) {
			Record3<JSONB, Long, Long> queryResult = dbContext.select(PackageVersions.PACKAGE_VERSIONS.METADATA, PackageVersions.PACKAGE_VERSIONS.ID, Modules.MODULES.ID)
					.from(Modules.MODULES, PackageVersions.PACKAGE_VERSIONS)
					.join(Callables.CALLABLES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
					.join(PackageVersions.PACKAGE_VERSIONS).on(PackageVersions.PACKAGE_VERSIONS.ID.eq(Modules.MODULES.PACKAGE_VERSION_ID))
					.where(Callables.CALLABLES.ID.eq(callableId)).fetchOne();
			packageVersionId = queryResult.component2().longValue();
			moduleId = queryResult.component3().longValue();
			jsonMetadata = new JSONObject(queryResult.component1().data());
			putLRUMap(callableId2moduleId, callableId, moduleId, LONG_MAP_MAXSIZE);
			putLRUMap(moduleId2packageVersionId, moduleId, packageVersionId, LONG_MAP_MAXSIZE);
			putLRUMap(packageVersionId2packageVersionMetadata, packageVersionId, jsonMetadata, METADATA_MAP_MAXSIZE);
		}
		return jsonMetadata;
	}
	
	private JSONObject getMetadata(final MetadataSource source, final long callableId) {
		switch(source) {
		case CALLABLE: return getCallableMetadata(callableId);
		case MODULE: return getModuleMetadata(callableId);
		case PACKAGE_VERSION: return getPackageVersionMetadata(callableId);
		default: throw new NoSuchElementException("Unknown source");
		}
	}

	/** A predicate that holds true if a given metadata contains a key/value pair with a specific key and
	 *  a value satisfying a certain property. 
	 * 
	 * @param source the source containing the metadata.
	 * @param key the (exact) key that the callable must contain.
	 * @param valuePredicate the predicate that the value must satisfy.
	 * @return the predicate.
	 */
	public MetadataContains metadataContains(final MetadataSource source, final String key, final Predicate<String> valuePredicate) {
		return t -> getMetadata(source, t).has(key) && valuePredicate.test(getCallableMetadata(t).getString(key));
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
	 * @param value the (exact) value that the callable must contain associated with the key.
	 * @return the predicate.
	 */
	public MetadataContains metadataContains(final MetadataSource source, final String key, final String value) {
		return metadataContains(source, key, x -> value.equals(x));
	}
	
	/** A predicate that holds true if the callable {@link FastenURI} matches a certain predicate.
	 * 
	 * @param fastenURIPredicate the predicate that the callable {@link FastenURI} is matched against.
	 * @return the predicate.
	 */
	public FastenURIMatches fastenURIMatches(final Predicate<FastenURI> fastenURIPredicate) {
		return x -> fastenURIPredicate.test(getCallableName(x));
	}

}