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

import java.util.function.Predicate;

import org.json.JSONObject;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;

/**
 * A factory that builds search predicates.
 *
 * <p>
 * All predicates are about a given callable (specified with its {@linkplain Callables#CALLABLES
 * GID}); some of them, in particular, determine if the JSON metadata associated with the callable
 * satisfy a given property. Notice that a callable has three different sources of metadata
 * associated with it:
 *
 * <ul>
 * <li>the actual metadata of the given callable ({@linkplain Callables#CALLABLES metadata});
 * <li>the metadata associated with the module of the given callable ({@linkplain Modules#MODULES
 * metadata});
 * <li>the metadata associated with the package version of the given callable
 * ({@linkplain PackageVersions#PACKAGE_VERSIONS metadata}).
 * </ul>
 *
 * <p>
 * The factory must be able to produce predicates for all types of sources (specified using the
 * {@link MetadataSource} enum).
 */

public interface PredicateFactory {

	/** An enum corresponding to the possible sources (i.e., database table) of metadata. */
	public enum MetadataSource {
		/** It refers to the {@linkplain Callables#CALLABLES callable} source. */
		CALLABLE,
		/** It refers to the {@linkplain Modules#MODULES module} source. */
		MODULE,
		/** It refers to the {@linkplain PackageVersions#PACKAGE_VERSIONS revision} source. */
		PACKAGE_VERSION
	}

	/**
	 * A predicate that holds true if a given metadata contains a key/value pair with a specific key and
	 * a value satisfying a certain property.
	 *
	 * @param source the source containing the metadata.
	 * @param key the (exact) key that the callable must contain.
	 * @param valuePredicate the predicate that the value must satisfy. It can be of any type, but its
	 *            {@link Object#toString()} method is used to get the value.
	 * @return the predicate.
	 */
	MetadataContains metadataContains(MetadataSource source, String key, Predicate<String> valuePredicate);

	/** A predicate that holds true if a given metadata contains a specific key.
	 *
	 * @param source the source containing the metadata.
	 * @param key the (exact) key that the callable must contain.
	 * @return the predicate.
	 */
	MetadataContains metadataContains(MetadataSource source, String key);

	/**
	 * A predicate that holds true if a given metadata contains a specific key/value pair.
	 *
	 * @param source the source containing the metadata.
	 * @param key the (exact) key that the callable must contain.
	 * @param value the (exact) string value that the callable must contain associated with the key. It
	 *            can be of any type, but its {@link Object#toString()} method is used to get the value.
	 * @return the predicate.
	 */
	MetadataContains metadataContains(MetadataSource source, String key, String value);

	/**
	 * A predicate that holds true if a given metadata, queried with a certain
	 * {@link org.json.JSONPointer}, has a string value satisfying some predicate.
	 *
	 * @param source the source containing the metadata.
	 * @param jsonPointer the {@link org.json.JSONPointer}
	 *            (<a href="https://tools.ietf.org/html/rfc6901">RFC 6901</a>).
	 * @param valuePredicate a predicate that is matched against the value returned
	 *            {@linkplain JSONObject#query(String) by the JSONPointer query}, after
	 *            {@linkplain Object#toString() conversion to a string}.
	 * @return true iff the metadata {@link JSONObject} matches the jsonPointer and the corresponding
	 *         value has a string representation that satisfies the predicate.
	 */
	MetadataContains metadataQueryJSONPointer(MetadataSource source, String jsonPointer, Predicate<String> valuePredicate);

	/** A predicate that holds true if the callable {@link FastenURI} matches a certain predicate.
	 *
	 * @param fastenURIPredicate the predicate that the callable {@link FastenURI} is matched against.
	 * @return the predicate.
	 */
	FastenURIMatches fastenURIMatches(Predicate<FastenURI> fastenURIPredicate);

}