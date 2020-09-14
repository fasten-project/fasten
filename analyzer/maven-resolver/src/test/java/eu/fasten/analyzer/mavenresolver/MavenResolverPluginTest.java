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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        var dbContext = Mockito.mock(DSLContext.class);
        var selectStep = Mockito.mock(SelectSelectStep.class);
        Mockito.when(dbContext.select(PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT)).thenReturn(selectStep);
        var joinStep = Mockito.mock(SelectJoinStep.class);
        Mockito.when(selectStep.from(PackageVersions.PACKAGE_VERSIONS)).thenReturn(joinStep);
        var onStep = Mockito.mock(SelectOnStep.class);
        Mockito.when(joinStep.join(Packages.PACKAGES)).thenReturn(onStep);
        var onCondStep = Mockito.mock(SelectOnConditionStep.class);
        Mockito.when(onStep.on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))).thenReturn(onCondStep);
        var condStep = Mockito.mock(SelectConditionStep.class);
        Mockito.when(onCondStep.where(Packages.PACKAGES.PACKAGE_NAME.eq("org.hamcrest:hamcrest-core"))).thenReturn(condStep);
        Mockito.when(condStep.and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))).thenReturn(condStep);
        var resultSet = Mockito.mock(Result.class);
        Mockito.when(resultSet.isNotEmpty()).thenReturn(true);
        Mockito.when(resultSet.size()).thenReturn(2);
        Mockito.when(resultSet.iterator()).thenReturn(List.of(DSL.row("1.2", new Timestamp(1285113600000L)), DSL.row("1.3", new Timestamp(1341792000000L))).iterator());
        // TODO: Fix test - mock data is not returned from the database
        Mockito.when(condStep.fetch()).thenReturn(resultSet);
        var expected = List.of(new MavenCoordinate("org.hamcrest", "hamcrest-core", "1.2"));
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
