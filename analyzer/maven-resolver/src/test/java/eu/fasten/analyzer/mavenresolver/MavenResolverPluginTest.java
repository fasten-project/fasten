package eu.fasten.analyzer.mavenresolver;

import eu.fasten.analyzer.mavenresolver.data.MavenCoordinate;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.data.metadatadb.codegen.tables.records.PackageVersionsRecord;
import eu.fasten.server.connectors.PostgresConnector;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class MavenResolverPluginTest {

    private MavenResolverPlugin.MavenResolver mavenResolver;

    @BeforeEach
    public void setup() {
        mavenResolver = new MavenResolverPlugin.MavenResolver();
        mavenResolver.setTopic("fasten.POMAnalyzer.out");
    }

    @Test
    public void consumeTest() {
        var record = new JSONObject("{\"groupId\":\"junit\",\"artifactId\":\"junit\",\"version\":\"4.12\"}");
        var expectedResolvedDependencies = new JSONArray();
        expectedResolvedDependencies.put(new JSONObject("{\"groupId\":\"org.hamcrest\",\"artifactId\":\"hamcrest-core\",\"version\":\"1.3\"}"));
        mavenResolver.consume(record.toString());
        assertNull(mavenResolver.getPluginError());
        var optionalResult = mavenResolver.produce();
        assertTrue(optionalResult.isPresent());
        var result = new JSONObject(optionalResult.get());
        var actualResolvedDependencies = result.getJSONArray("resolvedDependencies");
        assertEquals(expectedResolvedDependencies.toString(), actualResolvedDependencies.toString());
    }

    @Test
    public void resolveArtifactDependenciesTest() {
        class DataProvider implements MockDataProvider {
            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                var create = DSL.using(SQLDialect.POSTGRES);
                var mockData = new MockResult[2];
                var record1 = create.newResult(PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT);
                record1.add(create
                        .newRecord(PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT)
                        .values("1.2", new Timestamp(1285113600000L)));
                var record2 = create.newResult(PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT);
                record2.add(create
                        .newRecord(PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT)
                        .values("1.3", new Timestamp(1341792000000L)));
                mockData[0] = new MockResult(1, record1);
                mockData[1] = new MockResult(1, record2);
                return mockData;
            }
        }
        var provider = new DataProvider();
        var connection = new MockConnection(provider);
        var dbContext = DSL.using(connection, SQLDialect.POSTGRES);
        var expected = Set.of(new MavenCoordinate("org.hamcrest", "hamcrest-core", "1.2"));
        var actual = mavenResolver.resolveArtifactDependencies("junit:junit:4.12", 1307318400000L, dbContext);
        assertEquals(expected, actual);
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.POMAnalyzer.out"));
        assertEquals(topics, mavenResolver.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.POMAnalyzer.out"));
        assertEquals(topics1, mavenResolver.consumeTopic());
        var differentTopic = "DifferentKafkaTopic";
        var topics2 = Optional.of(Collections.singletonList(differentTopic));
        mavenResolver.setTopic(differentTopic);
        assertEquals(topics2, mavenResolver.consumeTopic());
    }

    @Test
    public void nameTest() {
        var name = "Maven Resolver Plugin";
        assertEquals(name, mavenResolver.name());
    }

    @Test
    public void descriptionTest() {
        var description = "Maven Resolver Plugin. Given a Maven coordinate, "
                + "it resolves the artifact to the complete dependency tree. "
                + "Optionally, a timestamp can be provided - no dependency versions released "
                + "later than timestamp will be included in the tree.";
        assertEquals(description, mavenResolver.description());
    }

    @Test
    public void versionTest() {
        var version = "0.0.1";
        assertEquals(version, mavenResolver.version());
    }
}
