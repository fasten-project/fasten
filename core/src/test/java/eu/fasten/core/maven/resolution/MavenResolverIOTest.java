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

import static eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions.PACKAGE_VERSIONS;
import static eu.fasten.core.maven.data.Scope.COMPILE;
import static org.jooq.SQLDialect.POSTGRES;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jooq.JSONB;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.fasten.core.json.ObjectMapperBuilder;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.Exclusion;
import eu.fasten.core.maven.data.Pom;
import eu.fasten.core.maven.data.PomBuilder;
import eu.fasten.core.maven.data.ResolvedRevision;
import eu.fasten.core.maven.data.Scope;
import eu.fasten.core.maven.data.VersionConstraint;

public class MavenResolverIOTest {

    private static final ObjectMapper OM = new ObjectMapperBuilder().build();

    @TempDir
    public File tempDir;

    private MyProvider data;

    private MavenResolverIO sut;

    @BeforeEach
    public void setup() {
        data = new MyProvider();
        var connection = new MockConnection(data);
        var dbContext = DSL.using(connection, POSTGRES);

        sut = new MavenResolverIO(dbContext, tempDir);
    }

    @Test
    public void requestedPomsGetSimplified() {
        var pb = new PomBuilder();
        pb.groupId = "g";
        pb.artifactId = "a";
        pb.version = "v";
        pb.artifactRepository = "ar";
        pb.commitTag = "ct";
        pb.dependencies.add(new Dependency("dg", "da", Set.of(new VersionConstraint("dv")),
                Set.of(new Exclusion("eg", "ea")), Scope.TEST, true, "jar", "cla"));
        pb.dependencyManagement.add(new Dependency("dmg", "dma", Set.of(new VersionConstraint("dmv")),
                Set.of(new Exclusion("emg", "ema")), Scope.IMPORT, true, "pom", "foo"));
        pb.packagingType = "pt";
        pb.parentCoordinate = "pcoord";
        pb.projectName = "pn";
        pb.releaseDate = 1234L;
        pb.repoUrl = "repo";
        pb.sourcesUrl = "srcUrl";
        data.poms.put(123L, pb.pom());

        var actuals = sut.readFromDB();

        pb = new PomBuilder();
        pb.groupId = "g";
        pb.artifactId = "a";
        pb.version = "v";
        var d1 = new Dependency("dg", "da", Set.of(new VersionConstraint("dv")), Set.of(new Exclusion("eg", "ea")),
                Scope.TEST, true, null, null);
        pb.dependencies.add(d1);
        var dm1 = new Dependency("dmg", "dma", Set.of(new VersionConstraint("dmv")),
                Set.of(new Exclusion("emg", "ema")), Scope.IMPORT, true, null, null);
        pb.dependencyManagement.add(dm1);
        pb.releaseDate = 1234L;
        var expected = pb.pom();
        expected.id = 123L;

        assertEquals(Set.of(pb.pom()), actuals);
        assertEquals(123L, actuals.iterator().next().id);
    }

    @Test
    public void smokeTest() {
        var pom = pom(0);
        var rev = pom.toRevision();

        data.add(pom);

        var r = sut.loadResolver();
        var actualDeps = r.resolveDependencies(rev);
        var expectedDep = new ResolvedRevision(rev, COMPILE);
        assertEquals(Set.of(expectedDep), actualDeps);

        var actualDpds = r.resolveDependents(rev);
        assertEquals(Set.of(), actualDpds);
    }

    @Test
    public void writesToDisk() throws StreamReadException, DatabindException, IOException {
        data.add(pom(234));
        sut.loadResolver();

        var actuals = OM.readValue(new File(tempDir, "poms.json"), new TypeReference<Set<Pom>>() {});
        var expecteds = Set.of(pom(234));
        assertEquals(expecteds, actuals);

        assertEquals(1, data.numExecutes);
    }

    @Test
    public void readsFromDisk() throws StreamReadException, DatabindException, IOException {
        var pom = pom(234);
        var poms = Set.of(pom);
        OM.writeValue(new File(tempDir, "poms.json"), poms);

        var r = sut.loadResolver();

        var revision = pom.toRevision();
        var actualDeps = r.resolveDependencies(revision);
        var expectedDeps = Set.of(new ResolvedRevision(revision, COMPILE));
        assertEquals(expectedDeps, actualDeps);

        assertEquals(0, data.numExecutes);
    }

    private Pom pom(int i) {
        var pom = new PomBuilder();
        pom.groupId = "g" + i;
        pom.artifactId = "a" + i;
        pom.version = "v" + i;
        return pom.pom();
    }

    public class MyProvider implements MockDataProvider {

        // see https://www.jooq.org/doc/latest/manual/sql-execution/mocking-connection/

        public int numExecutes = 0;
        public final Map<Long, Pom> poms = new LinkedHashMap<>();

        public void add(Pom pom) {
            var next = Long.valueOf(poms.size());
            poms.put(next, pom);
        }

        @Override
        public MockResult[] execute(MockExecuteContext ctx) throws SQLException {
            numExecutes++;

            var create = DSL.using(SQLDialect.POSTGRES);
            var sql = ctx.sql();
            if (sql.toUpperCase().startsWith("SELECT")) {

                var result = create.newResult(PACKAGE_VERSIONS.METADATA, PACKAGE_VERSIONS.ID);
                for (var e : poms.entrySet()) {
                    var json = toJson(e.getValue());
                    var jsonb = JSONB.valueOf(json);
                    var id = e.getKey();
                    var rec = create.newRecord(PACKAGE_VERSIONS.METADATA, PACKAGE_VERSIONS.ID).values(jsonb, id);
                    result.add(rec);
                }
                return new MockResult[] { new MockResult(poms.size(), result) };
            } else {
                throw new SQLException("Statement not supported: " + sql);
            }
        }

        private String toJson(Pom pom) {
            try {
                return OM.writeValueAsString(pom);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}