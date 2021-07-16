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

package eu.fasten.analyzer.restapiplugin.mvn.api;

import org.springframework.http.ResponseEntity;
import java.util.List;

public interface ResolutionApiService {

    ResponseEntity<String> resolveDependencies(String package_name, String version, boolean transitive, long timestamp, boolean useDepGraph);

    ResponseEntity<String> resolveDependents(String package_name, String version, boolean transitive, long timestamp);

    ResponseEntity<String> resolveMultipleDependencies(List<String> mavenCoordinates);

    ResponseEntity<String> getDirectedGraph(long packageVersionId, boolean needStitching, long timestamp);

    ResponseEntity<String> getTransitiveVulnerabilities(String package_name, String version);

}
