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
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.json.JSONObject;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.search.Util;

/**
 * A predicate factory that builds search predicates resolving them against a specific database
 * instance. No caching is attempted. */
public class TrivialPredicateFactory implements PredicateFactory {
	/** The database context. */
	protected final DSLContext dbContext;

	/** A factory for predicates that will be matched against a given database.
	 *
	 * @param dbContext the db context that will be used to match predicates.
	 */
	public TrivialPredicateFactory(final DSLContext dbContext) {
		this.dbContext = dbContext;
	}

	/** Given a select returning a single record with a single {@link JSONB} field, it
	 *  returns the field, returning {@code null} if anything goes wrong at any step.
	 *
	 * @param selectResult the select result.
	 * @return the {@link JSONObject} corresponding to the result, or {@code null}.
	 */
	private JSONObject selectJSONObject(final SelectConditionStep<Record1<JSONB>> selectResult) {
		if (selectResult == null) return null;
		final Record1<JSONB> record = selectResult.fetchOne();
		if (record == null) return null;
		final JSONB jsonb = record.component1();
		if (jsonb == null || jsonb.data() == null) return null;
		return new JSONObject(jsonb.data());
	}

	/** Returns the metadata field of a given callable.
	 *
	 * @param callableGID the callable GID.
	 * @return the metadata field associated to it.
	 */
	protected JSONObject getCallableMetadata(final long callableGID) {
		return selectJSONObject(dbContext.select(Callables.CALLABLES.METADATA)
				.from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(callableGID)));
	}

	/** Returns the metadata field of the package version corresponding to a given callable.
	 *
	 * @param callableGID the callable GID.
	 * @return the metadata field associated to the package version of the callable.
	 */
	protected JSONObject getModuleMetadata(final long callableGID) {
		return selectJSONObject(dbContext.select(Modules.MODULES.METADATA)
				.from(Callables.CALLABLES)
				.join(Modules.MODULES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
				.where(Callables.CALLABLES.ID.eq(callableGID)));
	}

	/** Returns the metadata field of the package version corresponding to a given callable.
	 *
	 * @param callableGID the callable id.
	 * @return the metadata field associated to the package version of the callable.
	 */
	protected JSONObject getPackageVersionMetadata(final long callableGID) {
		return selectJSONObject(dbContext.select(PackageVersions.PACKAGE_VERSIONS.METADATA)
					.from(Callables.CALLABLES)
					.join(Modules.MODULES).on(Callables.CALLABLES.MODULE_ID.eq(Modules.MODULES.ID))
					.join(PackageVersions.PACKAGE_VERSIONS).on(Modules.MODULES.PACKAGE_VERSION_ID.eq(PackageVersions.PACKAGE_VERSIONS.ID))
					.where(Callables.CALLABLES.ID.eq(callableGID)));
	}

	/**
	 * Returns the metadata of a given callable for a specific source.
	 *
	 * @param source the source of the metadata we want to obtain.
	 * @param callableGID the {@linkplain Callables#CALLABLES GID} of the callable under consideration.
	 * @return the JSON metadata.
	 */
	protected JSONObject getMetadata(final MetadataSource source, final long callableGID) {
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
	@Override
	public MetadataContains metadataContains(final MetadataSource source, final String key, final Predicate<String> valuePredicate) {
		return t -> getMetadata(source, t) != null && getMetadata(source, t).has(key) && valuePredicate.test(getMetadata(source, t).get(key).toString());
	}

	/** A predicate that holds true if a given metadata contains a specific key.
	 *
	 * @param source the source containing the metadata.
	 * @param key the (exact) key that the callable must contain.
	 * @return the predicate.
	 */
	@Override
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
	@Override
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
	@Override
	public MetadataContains metadataQueryJSONPointer(final MetadataSource source, final String jsonPointer, final Predicate<String> valuePredicate) {
		return t -> {
			final var md = getMetadata(source, t);
			final var qr = md != null? md.query(jsonPointer) : null;
			return md != null && qr != null && valuePredicate.test(qr.toString());
		};
	}


	/** A predicate that holds true if the callable {@link FastenURI} matches a certain predicate.
	 *
	 * @param fastenURIPredicate the predicate that the callable {@link FastenURI} is matched against.
	 * @return the predicate.
	 */
	@Override
	public FastenURIMatches fastenURIMatches(final Predicate<FastenURI> fastenURIPredicate) {
		return x -> fastenURIPredicate.test(Util.getCallableName(x, dbContext));
	}


}