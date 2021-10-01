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

package eu.fasten.core.maven;

import eu.fasten.core.maven.data.Revision;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MavenResolverTest {

    private MavenResolver mavenResolver;

    @BeforeEach
    public void setup() {
        mavenResolver = new MavenResolver();
    }

    @Test
    public void resolveFullDependencySetOnlineTest() {
        try {
            var expected = Set.of(new Revision("org.hamcrest", "hamcrest-core", "1.3", new Timestamp(-1)));
            var actual = mavenResolver.resolveDependencies("junit:junit:4.12");
            assertEquals(expected, actual);
        } catch (RuntimeException e) {
            Assume.assumeNoException("Online Resolver failed. Maybe Maven Central is down!", e);
        }
    }
}
