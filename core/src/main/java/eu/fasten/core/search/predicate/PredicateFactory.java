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

import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;

/* A factory that builds search predicates resolving them against a specific database instance.
 */
public class PredicateFactory {
	
	private DSLContext dbContext;

	/** A factory for predicates that will be matched against a given database.
	 * 
	 * @param dbContext the db context that will be used to match predicates. 
	 */
	public PredicateFactory(final DSLContext dbContext) {
		this.dbContext = dbContext;
	}
	
	/** An enum corresponding to the possible sources (i.e., database table) of metadata. */
	public enum MetadataSource {
		CALLABLE, MODULE, PACKAGE_VERSION
	}
	
	/** Returns the metadata field of a given callable.
	 * 
	 * @param callableId the callable id.
	 * @return the metadata field associated to it.
	 */
	@SuppressWarnings("unused")
	private JSONObject getCallableMetadata(final long callableId) {
		JSONObject jsonMetadata = new JSONObject(dbContext.select(Callables.CALLABLES.METADATA).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(callableId)).fetchOne().component1().data());
		return jsonMetadata;
	}

	/** Returns the metadata field of the package version corresponding to a given callable.
	 * 
	 * @param callableId the callable id.
	 * @return the metadata field associated to the package version of the callable.
	 */
	@SuppressWarnings("unused")
	private JSONObject getModuleMetadata(final long callableId) {
		Record2<JSONB, Long> queryResult = dbContext.select(Modules.MODULES.METADATA, Modules.MODULES.ID)
				.from(Modules.MODULES)
				.join(Callables.CALLABLES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
				.where(Callables.CALLABLES.ID.eq(callableId)).fetchOne();
		
		long moduleId = queryResult.component2().longValue();
		JSONObject jsonMetadata = new JSONObject(queryResult.component1().data());
		return jsonMetadata;
	}

	/** Returns the metadata field of the package version corresponding to a given callable.
	 * 
	 * @param callableId the callable id.
	 * @return the metadata field associated to the package version of the callable.
	 */
	@SuppressWarnings("unused")
	private JSONObject getPackageVersionMetadata(final long callableId) {
		Record3<JSONB, Long, Long> queryResult = dbContext.select(PackageVersions.PACKAGE_VERSIONS.METADATA, PackageVersions.PACKAGE_VERSIONS.ID, Modules.MODULES.ID)
				.from(Modules.MODULES, PackageVersions.PACKAGE_VERSIONS)
				.join(Callables.CALLABLES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
				.join(PackageVersions.PACKAGE_VERSIONS).on(PackageVersions.PACKAGE_VERSIONS.ID.eq(Modules.MODULES.PACKAGE_VERSION_ID))
				.where(Callables.CALLABLES.ID.eq(callableId)).fetchOne();
		
		long packageVersionId = queryResult.component2().longValue();
		long moduleId = queryResult.component3().longValue();
		JSONObject jsonMetadata = new JSONObject(queryResult.component1().data());
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

	/** A predicate that holds if a given metadata contains a key/value pair with a specific key and
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

	public MetadataContains metadataContains(final MetadataSource source, final String key) {
		return metadataContains(source, key, x -> true);
	}

	public MetadataContains metadataContains(final MetadataSource source, final String key, final String value) {
		return metadataContains(source, key, x -> value.equals(x));
	}

}