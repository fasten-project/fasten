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

package eu.fasten.analyzer.repoclonerplugin;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import org.apache.commons.io.FileUtils;

public class JarDownloader {

    private final String baseDir;

    public JarDownloader(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Downloads JAR file from provided URL into hierarchical directory structure.
     *
     * @param jarUrl  URL at which the JAR file is located
     * @param product Product name (whose jar is downloading)
     * @return Path to saved JAR file
     * @throws IOException if could not save the file
     */
    public String downloadJarFile(String jarUrl, String product) throws IOException {
        if (!jarUrl.endsWith(".jar")) {
            throw new IllegalArgumentException("Invalid link to download JAR");
        }
        var dirHierarchy = new DirectoryHierarchyBuilder(baseDir);
        var jarPath = Paths.get(dirHierarchy.getDirectoryFromHierarchy(product).getAbsolutePath(),
                product + ".jar");
        FileUtils.copyURLToFile(new URL(jarUrl), new File(jarPath.toString()));
        return jarPath.toString();
    }
}
