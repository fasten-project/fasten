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

import java.io.File;
import java.nio.file.Paths;

public class DirectoryHierarchyBuilder {

    private final String baseDir;

    public DirectoryHierarchyBuilder(String baseDir) {
        this.baseDir = baseDir;
    }

    public File getDirectoryFromHierarchy(String name) {
        var dir = Paths.get(this.baseDir, "mvn", String.valueOf(name.charAt(0)), name);
        return new File(dir.toString());
    }

    public File getDirectoryFromHierarchy(String artifact, String name) {
        var dir = Paths.get(this.baseDir, "mvn", String.valueOf(artifact.charAt(0)), name);
        return new File(dir.toString());
    }
}
