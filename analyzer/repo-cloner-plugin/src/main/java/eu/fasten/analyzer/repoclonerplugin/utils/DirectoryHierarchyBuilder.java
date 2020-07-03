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

    /**
     * Provides hierarchical directory structure.
     *
     * @param dir Upper level directory name
     * @param subDir Lower level directory name
     * @return Directory at {@link #baseDir}/<first letter of dir>/<dir>/<subDir>
     */
    public File getDirectoryFromHierarchy(String dir, String subDir) {
        var path = Paths.get(this.baseDir, String.valueOf(dir.charAt(0)), dir, subDir);
        return new File(path.toString());
    }
}
