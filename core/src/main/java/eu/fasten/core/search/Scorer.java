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

import eu.fasten.core.data.DirectedGraph;

/**
 * A scoring algorithm over {@link SearchEngine} queries.
 * <p>
 * Instances of this class are used by {@link SearchEngine} to compute scores over the callables
 * that are part of a result set. Global score information should be injected at construction time.
 *
 * @apiNote this interface is not stable, and might be changed in the future.
 */

public interface Scorer {

	/**
	 * Returns the score associated with a given callable (specified via GID) contained in a graph,
	 * given distance information.
	 *
	 * @param graph the context graph (usually, a stitched graph over which a visit is happening).
	 * @param gid the GID of a callable.
	 * @param distance distance information about the callable with GID {@code GID}.
	 */
	public double score(DirectedGraph graph, long gid, int distance);

}