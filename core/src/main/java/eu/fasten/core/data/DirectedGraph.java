package eu.fasten.core.data;

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

import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * A directed graph with internal and external nodes, providing both successors and predecessors
 * lists.
 *
 * <p>
 * Nodes in the graph are given by 64-bit (long) arbitrary identifiers. The set of nodes can be
 * recovered with {@link #nodes()}, and the set of external nodes with {@link #externalNodes()}.
 */

public interface DirectedGraph {
	/**
	 * The number of nodes in the graph.
	 *
	 * @return the number of nodes in the graph.
	 */
	public int numNodes();

	/**
	 * The number of arcs in the graph
	 *
	 * @return the number of arcs in the graph.
	 */
	public long numArcs();

	/**
	 * The list of successors of a given node.
	 *
	 * @param node a node in the graph.
	 * @return its successors.
	 * @throws IllegalArgumentException if <code>node</code> is not a node of the graph.
	 */
	public LongList successors(final long node);

	/**
	 * The list of predecessors of a given node.
	 *
	 * @param node a node in the graph.
	 * @return its successors.
	 * @throws IllegalArgumentException if <code>node</code> is not a node of the graph.
	 */
	public LongList predecessors(final long node);

	/**
	 * The set of nodes of the graph.
	 *
	 * @return the set of nodes of the graph. // TODO this should be a LongSet.
	 */
	public LongList nodes();

	/**
	 * The set of external nodes of the graph.
	 *
	 * @return the set of external nodes of the graph.
	 */
	public LongSet externalNodes();

	/**
	 * Returns whether a node is internal.
	 *
	 * @param node a node of the graph.
	 * @return whether <code>node</code> is internal.
	 */
	public boolean isInternal(final long node);

	/**
	 * Returns whether a node is external.
	 *
	 * @param node a node of the graph.
	 * @return whether <code>node</code> is external.
	 */
	public boolean isExternal(final long node);
}
