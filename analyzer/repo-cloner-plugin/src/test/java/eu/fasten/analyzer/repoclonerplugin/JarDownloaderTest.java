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

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JarDownloaderTest {

    private JarDownloader jarDownloader;
    private String baseDir;

    @BeforeEach
    public void setup() throws IOException {
        this.baseDir = Files.createTempDirectory("").toString();
        this.jarDownloader = new JarDownloader(baseDir);
    }

    @AfterEach
    public void teardown() throws IOException {
        FileUtils.deleteDirectory(Path.of(baseDir).toFile());
    }

    @Test
    public void downloadJarTest() throws IOException {
        var jarUrl = "https://search.maven.org/remotecontent?filepath=junit/junit/4.11/junit-4.11-sources.jar";
        var product = "junit-4.11";
        var jar = Path.of(baseDir, "mvn/j/" + product + "/" + product + ".jar").toFile();
        var path = jarDownloader.downloadJarFile(jarUrl, product);
        Assertions.assertEquals(jar.getAbsolutePath(), path);
        Assertions.assertTrue(jar.exists());
        Assertions.assertTrue(jar.isFile());
    }

    @Test
    public void downloadJarErrorTest() throws IOException {
        var jarUrl = "https://search.maven.org/";
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            jarDownloader.downloadJarFile(jarUrl, "test");
        });
    }
}
