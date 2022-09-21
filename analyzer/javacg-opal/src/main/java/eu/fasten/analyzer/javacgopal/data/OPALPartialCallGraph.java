/*
 * Copyright 2021 Delft University of Technology
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.analyzer.javacgopal.data;

import eu.fasten.core.data.JavaGraph;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class OPALPartialCallGraph {

	public JavaGraph graph;
	public AtomicInteger nodeCount;
	public EnumMap<JavaScope, Map<String, JavaType>> classHierarchy;

	public OPALPartialCallGraph(EnumMap<JavaScope, Map<String, JavaType>> ch, JavaGraph graph,
								AtomicInteger nodeCount) {
		this.graph = graph;
		this.nodeCount = nodeCount;
		this.classHierarchy = ch;
	}
}