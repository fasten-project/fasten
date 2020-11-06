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

package eu.fasten.core.maven.utils;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MavenUtilitiesTest {

    @Test
    public void getReposDefaultTest() {
        var actual = MavenUtilities.getRepos();
        var expected = List.of("https://repo.maven.apache.org/maven2/");
        assertEquals(expected, actual);
    }

    @Test
    public void downloadValidPomTest() {
        var file = MavenUtilities.downloadPom("junit", "junit", "4.12");
        assertTrue(file.isPresent());
    }

    @Test
    public void downloadInvalidPomTest() {
        var file = MavenUtilities.downloadPom("fake", "fake", "4.12");
        assertTrue(file.isEmpty());
    }

    @Test
    public void downloadPomWithInvalidRepoTest() {
        var file = MavenUtilities.downloadPom("junit", "junit", "4.12", List.of("https://google.com/"));
        assertTrue(file.isEmpty());
    }

}
