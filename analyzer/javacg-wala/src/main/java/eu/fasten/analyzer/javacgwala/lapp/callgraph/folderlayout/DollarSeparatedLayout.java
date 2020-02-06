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


package eu.fasten.analyzer.javacgwala.lapp.callgraph.folderlayout;

import eu.fasten.analyzer.javacgwala.lapp.callgraph.ArtifactRecord;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;

public class DollarSeparatedLayout implements ArtifactFolderLayout {

    /**
     * Build ArtifactRecord directly from jar file.
     *
     * @param jarFile Jar file
     * @return parsed ArtifactRecord
     */
    @Override
    public ArtifactRecord artifactRecordFromJarFile(JarFile jarFile) {

        String path = jarFile.getName();

        return artifactRecordFromPath(path);
    }

    /**
     * Build Artifact record from path.
     *
     * @param path - path to jar file
     * @return - parsed ArtifactRecord
     */
    public ArtifactRecord artifactRecordFromPath(String path) {

        Path p = Paths.get(path);
        String filename = p.getFileName().toString();

        if (filename.endsWith(".jar")) {
            filename = filename
                    .substring(0, filename.length() - 4)
                    .replace('$', ':');
        }

        if (filename.equals("rt")) {
            // TODO find jdk version
            return new ArtifactRecord("jdk", filename, "?");
        }

        try {
            return new ArtifactRecord(filename);
        } catch (IllegalArgumentException e) {
            // File name probably isn't in the correct format
            return new ArtifactRecord("static", filename, "?");
        }
    }
}
