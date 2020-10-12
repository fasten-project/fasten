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

package eu.fasten.analyzer.repoclonerplugin.utils;

import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DirectoryHierarchyBuilderTest {

    @Test
    public void getDirectoryFromHierarchyWithArtifactTest() {
        String baseDir = "test";
        String name = "repo";
        String owner = "test";
        var hierarchyBuilder = new DirectoryHierarchyBuilder(baseDir);
        var dir = hierarchyBuilder.getDirectoryFromHierarchy(owner, name);
        Assertions.assertEquals(Path.of("test","t","test","repo").toString(), dir.getPath());
    }
}
