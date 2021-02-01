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

import org.jooq.DSLContext;
import org.json.JSONObject;

import eu.fasten.core.data.metadatadb.codegen.tables.Callables;

/* A factory that builds search predicates resolving them against a specific database instance.
 */
public class PredicateFactory {
	
	private DSLContext dbContext;

	/** Returns the metadata field relative to a given callable.
	 * 
	 * @param callableId the callable id.
	 * @return the metadata field associated to it.
	 */
	@SuppressWarnings("unused")
	private JSONObject getCallableMetadata(final long callableId) {
		return new JSONObject(dbContext.select(Callables.CALLABLES.METADATA).from(Callables.CALLABLES).where(Callables.CALLABLES.ID.eq(callableId)).fetchOne().component1().data());
	}
	
	public PredicateFactory(final DSLContext dbContext) {
		this.dbContext = dbContext;
	}
	
	public CallableContains callableContains(final String key, final Predicate<String> valuePredicate) {
		return t -> getCallableMetadata(t).has(key) && valuePredicate.test(getCallableMetadata(t).getString(key));
	}

	public CallableContains callableContains(final String key) {
		return callableContains(key, x -> true);
	}

	public CallableContains callableContains(final String key, final String value) {
		return callableContains(key, x -> value.equals(x));
	}

}