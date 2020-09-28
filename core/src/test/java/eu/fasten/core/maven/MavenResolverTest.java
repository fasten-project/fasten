package eu.fasten.core.maven;

import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.DependencyTree;
import org.jooq.JSONB;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MavenResolverTest {

    private MavenResolver mavenResolver;

    @BeforeEach
    public void setup() {
        mavenResolver = new MavenResolver();
    }

    @Test
    public void resolveDependenciesTest() {
        class DataProvider implements MockDataProvider {
            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                var create = DSL.using(SQLDialect.POSTGRES);
                var mockData = new MockResult[1];
                var dependencyResult = create.newResult(Dependencies.DEPENDENCIES.METADATA);
                dependencyResult.add(create
                        .newRecord(Dependencies.DEPENDENCIES.METADATA)
                        .values(JSONB.valueOf("{\"type\": \"\", \"scope\": \"\", \"groupId\": \"org.hamcrest\", \"optional\": false, \"artifactId\": \"hamcrest-core\", \"classifier\": \"\", \"exclusions\": [], \"versionConstraints\": [{\"lowerBound\": \"1.3\", \"upperBound\": \"1.3\", \"isLowerHardRequirement\": false, \"isUpperHardRequirement\": false}]}")));
                var packageVersionsResult = create.newResult(PackageVersions.PACKAGE_VERSIONS.VERSION);
                packageVersionsResult.add(create
                        .newRecord(PackageVersions.PACKAGE_VERSIONS.VERSION)
                        .values("1.2"));
                System.err.println(ctx.sql());
                if (ctx.sql().startsWith("select \"public\".\"dependencies\".\"metadata\"")) {
                    mockData[0] = new MockResult(dependencyResult.size(), dependencyResult);
                } else if (ctx.sql().startsWith("select \"public\".\"package_versions\".\"version\"")) {
                    mockData[0] = new MockResult(packageVersionsResult.size(), packageVersionsResult);
                } else {
                    mockData = new MockResult[]{};
                }
                return mockData;
            }
        }
        var connection = new MockConnection(new DataProvider());
        var dbContext = DSL.using(connection, SQLDialect.POSTGRES);
        var expected = Set.of(new Dependency("org.hamcrest", "hamcrest-core", "1.2"));
//        var actual = mavenResolver.resolveFullDependencySet("junit", "junit", "4.12", 1307318400000L, false, dbContext);
//        assertEquals(expected, actual);
    }

    @Test
    public void filterOptionalDependenciesTest() {
        var noOptionalDependenciesTree = new DependencyTree(new Dependency("junit:junit:4.12"),
                List.of(new DependencyTree(new Dependency("org.hamcrest:hamcrest-core:1.2"), emptyList()))
        );
        assertEquals(noOptionalDependenciesTree, mavenResolver.filterOptionalDependencies(noOptionalDependenciesTree));

        var optionalDependenciesTree = new DependencyTree(new Dependency("junit:junit:4.12"),
                List.of(new DependencyTree(new Dependency("org.hamcrest:hamcrest-core:1.2"),
                        List.of(new DependencyTree(new Dependency("optional", "dependency", "1", emptyList(), "", true, "", ""),
                                List.of(new DependencyTree(new Dependency("foo:bar:42"), emptyList())))))));
        assertEquals(noOptionalDependenciesTree, mavenResolver.filterOptionalDependencies(optionalDependenciesTree));
    }
}
