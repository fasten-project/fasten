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

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.SelectConditionStep;
import org.json.JSONObject;

import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/**
 * A predicate factory that builds search predicates resolving them against a specific database
 * instance.
 *
 * <p>This class keeps a LRU cache of the metadata returned most recently, and also of the association
 * between callable IDs, module IDs and package version IDs.
 */
public class CachingPredicateFactory extends TrivialPredicateFactory {
	/** Maximum size of all metadata caches (all maps are kept trimmed to this size). */
	private static final int METADATA_MAP_MAXSIZE = 1024;
	/** Maximum size of all ID association caches (all maps are kept trimmed to this size). */
	private static final int LONG_MAP_MAXSIZE = 1024*1024;
	/** LRU cache of the last metadata from the {@link Callables#CALLABLES} table. */
	private final Long2ObjectLinkedOpenHashMap<JSONObject> callableGID2callableMetadata;
	/** LRU cache of the last metadata from the {@link Modules#MODULES} table. */
	private final Long2ObjectLinkedOpenHashMap<JSONObject> moduleGID2moduleMetadata;
	/** LRU cache of the last metadata from the {@link PackageVersions#PACKAGE_VERSIONS} table. */
	private final Long2ObjectLinkedOpenHashMap<JSONObject> packageVersionGID2packageVersionMetadata;
	/**
	 * LRU cache of the map between {@linkplain Callables#CALLABLES GIDs} and
	 * {@linkplain Modules#MODULES module database ids}.
	 */
	private final Long2LongLinkedOpenHashMap callableGID2moduleGID;
	/**
	 * LRU cache of the map between {@linkplain Modules#MODULES module database ids} and
	 * {@linkplains PackageVersions#PACKAGE_VERSIONS revision ids}.
	 */
	private final Long2LongLinkedOpenHashMap moduleGID2packageVersionGID;

	/** A factory for predicates that will be matched against a given database.
	 *
	 * @param dbContext the db context that will be used to match predicates.
	 */
	public CachingPredicateFactory(final DSLContext dbContext) {
		super(dbContext);
		this.callableGID2callableMetadata = new Long2ObjectLinkedOpenHashMap<>();
		this.moduleGID2moduleMetadata = new Long2ObjectLinkedOpenHashMap<>();
		this.packageVersionGID2packageVersionMetadata = new Long2ObjectLinkedOpenHashMap<>();
		this.callableGID2moduleGID = new Long2LongLinkedOpenHashMap();
		callableGID2moduleGID.defaultReturnValue(-1);
		this.moduleGID2packageVersionGID = new Long2LongLinkedOpenHashMap();
		moduleGID2packageVersionGID.defaultReturnValue(-1);
	}

	/** Puts a given key/value pair in a map, and guarantees that the pair is in the first
	 *  position, ensuring also that the map size does not exceed a given maximum.
	 *
	 * @param map the map to be modified.
	 * @param key the key.
	 * @param value the value associated to the key.
	 * @param maxSize the maximum size.
	 */
	private static <T> void putLRUMap(final Long2ObjectLinkedOpenHashMap<T> map, final long key, final T value, final int maxSize) {
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
	private static void putLRUMap(final Long2LongLinkedOpenHashMap map, final long key, final long value, final int maxSize) {
		map.putAndMoveToFirst(key, value);
		if (map.size() > maxSize) map.removeLastLong();
	}

	/** Returns the metadata field of a given callable.
	 *
	 * @param callableGID the callable GID.
	 * @return the metadata field associated to it.
	 */
	@Override
	protected JSONObject getCallableMetadata(final long callableGID) {
		JSONObject jsonMetadata = callableGID2callableMetadata.get(callableGID);
		if (jsonMetadata == null) {
			final SelectConditionStep<Record1<JSONB>> rs = dbContext.select(Callables.CALLABLES.METADATA).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(callableGID));
			if (rs != null) {
				final Record1<JSONB> record = rs.fetchOne();
				if (record != null) {
					jsonMetadata = new JSONObject(record.component1().data());
				}
			}
			putLRUMap(callableGID2callableMetadata, callableGID, jsonMetadata, METADATA_MAP_MAXSIZE);
		}
		return jsonMetadata;
	}

	/** Returns the metadata field of the package version corresponding to a given callable.
	 *
	 * @param callableGID the callable GID.
	 * @return the metadata field associated to the package version of the callable.
	 */
	@Override
	protected JSONObject getModuleMetadata(final long callableGID) {
		long moduleGID = callableGID2moduleGID.get(callableGID);
		JSONObject jsonMetadata = moduleGID >= 0? moduleGID2moduleMetadata.get(moduleGID) : null;
		if (jsonMetadata == null) {
			final Record2<JSONB, Long> queryResult = dbContext.select(Modules.MODULES.METADATA, Modules.MODULES.ID)
					.from(Callables.CALLABLES)
					.join(Modules.MODULES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
					.where(Callables.CALLABLES.ID.eq(callableGID)).fetchOne();
			moduleGID = queryResult.component2().longValue();
			jsonMetadata = moduleGID >=0? new JSONObject(queryResult.component1().data()) : null;
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
	@Override
	protected JSONObject getPackageVersionMetadata(final long callableGID) {
		long moduleGID = callableGID2moduleGID.get(callableGID);
		long packageVersionGID = callableGID >= 0? moduleGID2packageVersionGID.get(moduleGID) : -1;
		JSONObject jsonMetadata = packageVersionGID >= 0? packageVersionGID2packageVersionMetadata.get(packageVersionGID) : null;
		if (jsonMetadata == null ) {
			final Record3<JSONB, Long, Long> queryResult = dbContext.select(PackageVersions.PACKAGE_VERSIONS.METADATA, PackageVersions.PACKAGE_VERSIONS.ID, Modules.MODULES.ID)
					.from(Callables.CALLABLES)
					.join(Modules.MODULES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
					.join(PackageVersions.PACKAGE_VERSIONS).on(Modules.MODULES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID))
					.where(Callables.CALLABLES.ID.eq(callableGID)).fetchOne();
			packageVersionGID = queryResult.component2().longValue();
			moduleGID = queryResult.component3().longValue();
			jsonMetadata = packageVersionGID >= 0 && moduleGID >= 0? new JSONObject(queryResult.component1().data()) : null;
			putLRUMap(callableGID2moduleGID, callableGID, moduleGID, LONG_MAP_MAXSIZE);
			putLRUMap(moduleGID2packageVersionGID, moduleGID, packageVersionGID, LONG_MAP_MAXSIZE);
			putLRUMap(packageVersionGID2packageVersionMetadata, packageVersionGID, jsonMetadata, METADATA_MAP_MAXSIZE);
		}
		return jsonMetadata;
	}

	public static void main(final String[] args) {
	    final var a = new JSONObject("{\"final\": false, \"access\": \"public\", \"superClasses\": [\"/it.unimi.dsi.webgraph/ImmutableGraph\", \"/java.lang/Object\"], \"superInterfaces\": [\"/it.unimi.dsi.lang/FlyweightPrototype\", \"/it.unimi.dsi.webgraph/CompressionFlags\"]}");
	    System.out.println(a.query("/superInterfaces"));
	}

}