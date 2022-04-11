/*
 * Copyright 2022 Delft University of Technology
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
package eu.fasten.core.maven.resolution;

import static eu.fasten.core.maven.data.Scope.IMPORT;
import static eu.fasten.core.maven.data.Scope.TEST;
import static eu.fasten.core.maven.resolution.ResolverDepth.DIRECT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.f4sten.test.HttpTestServer;
import eu.f4sten.test.HttpTestServer.Request;
import eu.fasten.core.maven.data.ResolvedRevision;
import eu.fasten.core.maven.data.Revision;

public class RestMavenResolverTest {

    private static final Set<String> SOME_DEPS = Set.of("g:a:1", "g2:a2:2");

    private static final Revision DEFAULT_REVISION = new ResolvedRevision(0, "g", "a", "2.3.4",
            new Timestamp(1234567890000L), IMPORT);

    private static final int PORT = 8080;

    private static HttpTestServer httpd;
    private RestMavenResolver sut;

    @BeforeAll
    public static void setupAll() {
        httpd = new HttpTestServer(PORT);
        httpd.start();
    }

    @AfterAll
    public static void teardownAll() {
        httpd.stop();
    }

    @BeforeEach
    public void setup() {
        httpd.reset();
        httpd.setResponse("application/json",
                "[{\"groupId\":\"g\",\"artifactId\":\"a\",\"version\":\"2.3.4\",\"createdAt\":1234567890000,\"scope\":\"IMPORT\"}]");

        sut = new RestMavenResolver("http://127.0.0.1:" + PORT + "/");
    }

    @Test
    public void dependenciesCorrectMethod() {
        var r = resolveSomeDependencies();
        assertEquals("POST", r.method);
    }

    @Test
    public void dependenciesCorrectPath() {
        var r = resolveSomeDependencies();
        assertEquals("/depgraph/dependencies", r.path);
    }

    @Test
    public void dependenciesCorrectHeaders() {
        var r = resolveSomeDependencies();
        assertTrue(r.headers.containsKey("Content-Type"));
        assertEquals(APPLICATION_JSON, r.headers.get("Content-Type"));
        assertTrue(r.headers.containsKey("Accept"));
        assertEquals(APPLICATION_JSON, r.headers.get("Accept"));
    }

    @Test
    public void dependenciesCorrectTimestampDefault() {
        var cfg = new ResolverConfig();
        var r = resolveSomeDependencies(cfg);
        Map<String, String> expected = Map.of("resolveAt", Long.toString(cfg.resolveAt));
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependenciesCorrectTimestampExplicit() {
        var cfg = new ResolverConfig().at(1234567);
        var r = resolveSomeDependencies(cfg);
        Map<String, String> expected = Map.of("resolveAt", Long.toString(1234567));
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependenciesCorrectNonDefaultScope() {
        var cfg = new ResolverConfig().at(123).scope(TEST);
        var r = resolveSomeDependencies(cfg);
        Map<String, String> expected = Map.of( //
                "resolveAt", Long.toString(123), //
                "scope", "TEST");
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependenciesCorrectNonDefaultDepth() {
        var cfg = new ResolverConfig().at(123).depth(DIRECT);
        var r = resolveSomeDependencies(cfg);
        Map<String, String> expected = Map.of( //
                "resolveAt", Long.toString(123), //
                "depth", "DIRECT");
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependenciesCorrectIncludeProvided() {
        var cfg = new ResolverConfig().at(123).alwaysIncludeProvided(true);
        var r = resolveSomeDependencies(cfg);
        Map<String, String> expected = Map.of( //
                "resolveAt", Long.toString(123), //
                "alwaysIncludeProvided", "true");
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependenciesCorrectIncludeOptional() {
        var cfg = new ResolverConfig().at(123).alwaysIncludeOptional(true);
        var r = resolveSomeDependencies(cfg);
        Map<String, String> expected = Map.of( //
                "resolveAt", Long.toString(123), //
                "alwaysIncludeOptional", "true");
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependenciesCorrectBody() {
        var r = resolveSomeDependencies();
        var actual = parseBody(r.body);
        var expected = SOME_DEPS;
        assertEquals(expected, actual);
    }

    @Test
    public void dependenciesSuccessfullyParsed() {
        var expected = Set.of(DEFAULT_REVISION);
        var actual = sut.resolveDependencies(SOME_DEPS, new ResolverConfig());
        assertEquals(expected, actual);

        var a = actual.iterator().next();
        assertEquals(0L, a.id);
        assertEquals(IMPORT, a.scope);
    }

    @Test
    public void dependentsCorrectMethod() {
        var r = resolveSomeDependents();
        assertEquals("GET", r.method);
    }

    @Test
    public void dependentsCorrectPath() {
        var r = resolveSomeDependents();
        assertEquals("/depgraph/dependents/g/a/1.2.3", r.path);
    }

    @Test
    public void dependentsCorrectHeaders() {
        var r = resolveSomeDependents();
        assertTrue(r.headers.containsKey("Accept"));
        assertEquals(APPLICATION_JSON, r.headers.get("Accept"));
    }

    @Test
    public void dependentsCorrectTimestampDefault() {
        var cfg = new ResolverConfig();
        var r = resolveSomeDependents(cfg);
        Map<String, String> expected = Map.of("resolveAt", Long.toString(cfg.resolveAt));
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependentsCorrectTimestampExplicit() {
        var cfg = new ResolverConfig().at(1234567);
        var r = resolveSomeDependents(cfg);
        Map<String, String> expected = Map.of("resolveAt", Long.toString(1234567));
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependentsCorrectNonDefaultScope() {
        var cfg = new ResolverConfig().at(123).scope(TEST);
        var r = resolveSomeDependents(cfg);
        Map<String, String> expected = Map.of( //
                "resolveAt", Long.toString(123), //
                "scope", "TEST");
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependentsCorrectNonDefaultDepth() {
        var cfg = new ResolverConfig().at(123).depth(DIRECT);
        var r = resolveSomeDependents(cfg);
        Map<String, String> expected = Map.of( //
                "resolveAt", Long.toString(123), //
                "depth", "DIRECT");
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependentsCorrectIncludeProvided() {
        var cfg = new ResolverConfig().at(123).alwaysIncludeProvided(true);
        var r = resolveSomeDependents(cfg);
        Map<String, String> expected = Map.of( //
                "resolveAt", Long.toString(123), //
                "alwaysIncludeProvided", "true");
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependentsCorrectIncludeOptional() {
        var cfg = new ResolverConfig().at(123).alwaysIncludeOptional(true);
        var r = resolveSomeDependents(cfg);
        Map<String, String> expected = Map.of( //
                "resolveAt", Long.toString(123), //
                "alwaysIncludeOptional", "true");
        assertEquals(expected, r.queryParams);
    }

    @Test
    public void dependentsCorrectBody() {
        var r = resolveSomeDependents();
        var actual = parseBody(r.body);
        var expected = Set.of();
        assertEquals(expected, actual);
    }

    @Test
    public void dependentsSuccessfullyParsed() {
        var expected = Set.of(DEFAULT_REVISION);
        var actual = sut.resolveDependents("g", "a", "1", new ResolverConfig());
        assertEquals(expected, actual);

        var a = actual.iterator().next();
        assertEquals(0L, a.id);
        assertEquals(IMPORT, a.scope);
    }

    private Request resolveSomeDependencies() {
        return resolveSomeDependencies(new ResolverConfig());
    }

    private Request resolveSomeDependencies(ResolverConfig cfg) {
        sut.resolveDependencies(SOME_DEPS, cfg);
        assertEquals(1, httpd.requests.size());
        return httpd.requests.get(0);
    }

    private Request resolveSomeDependents() {
        return resolveSomeDependents(new ResolverConfig());
    }

    private Request resolveSomeDependents(ResolverConfig cfg) {
        sut.resolveDependents("g", "a", "1.2.3", cfg);
        assertEquals(1, httpd.requests.size());
        return httpd.requests.get(0);
    }

    private static Set<String> parseBody(String body) {
        if (body == null || body.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(body.substring(1, body.length() - 1).split(",")) //
                .map(gav -> gav.trim().substring(1, gav.length() - 1)) //
                .collect(Collectors.toSet());
    }
}