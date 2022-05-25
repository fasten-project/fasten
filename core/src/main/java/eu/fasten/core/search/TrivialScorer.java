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
import it.unimi.dsi.bits.Fast;

/**
 * A trivial ranking algorithm using local information only.
 *
 * <p>
 * As this scorer is stateless, its only implementation can be retrieved using the factory method
 * {@link #getInstance()}.
 */

public class TrivialScorer implements Scorer {

	private TrivialScorer() {
	}

	private static TrivialScorer INSTANCE = new TrivialScorer();

	/** {inheritDoc}
	 * @implSpec Returns the sum of the indegree and outdegree of the provided callable,
	 * discounted by the base-2 logarithm of the distance plus 2.
	 */
	@Override
	public double score(final DirectedGraph graph, final long gid, final int distance) {
		return (graph.outdegree(gid) + graph.indegree(gid)) / Fast.log2(distance + 2);
	}

	/**
	 * Returns the only instance of this scorer.
	 *
	 * @return the only instance of this scorer.
	 */
	public static Scorer getInstance() {
		return INSTANCE;
	}

}