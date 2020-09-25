package eu.fasten.core.maven;

import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.MavenCoordinate;
import org.jooq.JSONB;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
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

//    @Test
//    public void resolveArtifactDependenciesTest() {
//        class DataProvider implements MockDataProvider {
//            @Override
//            public MockResult[] execute(MockExecuteContext ctx) {
//                var create = DSL.using(SQLDialect.POSTGRES);
//                var mockData = new MockResult[2];
//                var record1 = create.newResult(PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT);
//                record1.add(create
//                        .newRecord(PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT)
//                        .values("1.2", new Timestamp(1285113600000L)));
//                var record2 = create.newResult(PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT);
//                record2.add(create
//                        .newRecord(PackageVersions.PACKAGE_VERSIONS.VERSION, PackageVersions.PACKAGE_VERSIONS.CREATED_AT)
//                        .values("1.3", new Timestamp(1341792000000L)));
//                mockData[0] = new MockResult(1, record1);
//                mockData[1] = new MockResult(1, record2);
//                return mockData;
//            }
//        }
//        var provider = new DataProvider();
//        var connection = new MockConnection(provider);
//        var dbContext = DSL.using(connection, SQLDialect.POSTGRES);
//        var expected = Set.of(new MavenCoordinate("org.hamcrest", "hamcrest-core", "1.2"));
//        var actual = mavenResolver.resolveArtifactDependenciesOnline("junit:junit:4.12", 1307318400000L, dbContext);
//        assertEquals(expected, actual);
//    }

//    @Test
//    public void resolveDependenciesTest() {
//        class DataProvider implements MockDataProvider {
//            @Override
//            public MockResult[] execute(MockExecuteContext ctx) {
//                var create = DSL.using(SQLDialect.POSTGRES);
//                var mockData = new MockResult[1];
//                var dependencyResult = create.newResult(Dependencies.DEPENDENCIES.METADATA);
//                dependencyResult.add(create
//                        .newRecord(Dependencies.DEPENDENCIES.METADATA)
//                        .values(JSONB.valueOf("{\"type\": \"\", \"scope\": \"\", \"groupId\": \"org.hamcrest\", \"optional\": false, \"artifactId\": \"hamcrest-core\", \"classifier\": \"\", \"exclusions\": [], \"versionConstraints\": [{\"lowerBound\": \"1.3\", \"upperBound\": \"1.3\", \"isLowerHardRequirement\": false, \"isUpperHardRequirement\": false}]}")));
//                var packageVersionsResult = create.newResult(PackageVersions.PACKAGE_VERSIONS.VERSION);
//                packageVersionsResult.add(create
//                        .newRecord(PackageVersions.PACKAGE_VERSIONS.VERSION)
//                        .values("1.2"));
//                System.err.println(ctx.sql());
//                if (ctx.sql().startsWith("select \"public\".\"dependencies\".\"metadata\"")) {
//                    mockData[0] = new MockResult(dependencyResult.size(), dependencyResult);
//                } else if (ctx.sql().startsWith("select \"public\".\"package_versions\".\"version\"")) {
//                    mockData[0] = new MockResult(packageVersionsResult.size(), packageVersionsResult);
//                } else {
//                    mockData = new MockResult[]{};
//                }
//                return mockData;
//            }
//        }
//        var connection = new MockConnection(new DataProvider());
//        var dbContext = DSL.using(connection, SQLDialect.POSTGRES);
//        var expected = Set.of(new Dependency("org.hamcrest", "hamcrest-core", "1.2"));
//        var actual = mavenResolver.resolveDependencies("junit", "junit", "4.12", 1307318400000L, dbContext);
//        assertEquals(expected, actual);
//    }
}
