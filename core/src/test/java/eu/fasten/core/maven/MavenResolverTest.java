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

import eu.fasten.core.data.metadatadb.codegen.tables.Dependencies;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.DependencyTree;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MavenResolverTest {

    private MavenResolver mavenResolver;

    @BeforeEach
    public void setup() {
        mavenResolver = new MavenResolver();
    }

    @Test
    public void resolveDependenciesTest() {
        class DataProvider implements MockDataProvider {
            private int queries = 0;

            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                if (queries >= 3) {
                    return new MockResult[]{new MockResult(0)};
                }
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
                var packageVersionMetadataResult = create.newResult(PackageVersions.PACKAGE_VERSIONS.METADATA);
                packageVersionMetadataResult.add(create
                        .newRecord(PackageVersions.PACKAGE_VERSIONS.METADATA)
                        .values(JSONB.valueOf("{\"parentCoordinate\":\"\"}")));
                System.err.println(ctx.sql());
                if (ctx.sql().startsWith("select \"public\".\"dependencies\".\"metadata\"")) {
                    mockData[0] = new MockResult(dependencyResult.size(), dependencyResult);
                    queries++;
                } else if (ctx.sql().startsWith("select \"public\".\"package_versions\".\"version\"")) {
                    mockData[0] = new MockResult(packageVersionsResult.size(), packageVersionsResult);
                    queries++;
                } else if (ctx.sql().startsWith("select \"public\".\"package_versions\".\"metadata\"")) {
                    mockData[0] = new MockResult(packageVersionMetadataResult.size(), packageVersionMetadataResult);
                    queries++;
                }
                return mockData;
            }
        }
        var dbContext = DSL.using(new MockConnection(new DataProvider()));
        var expected = Set.of(new Dependency("org.hamcrest", "hamcrest-core", "1.2"));
//        var actual = mavenResolver.resolveFullDependencySet("junit", "junit", "4.12", 1307318400000L, false, dbContext);
//        assertEquals(expected, actual);
    }

    @Test
    public void buildFullDependencyTreeTest() {
        class DataProvider implements MockDataProvider {
            private boolean queried = false;

            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                if (!queried) {
                    queried = true;
                    var create = DSL.using(SQLDialect.POSTGRES);
                    var mockData = new MockResult[1];
                    var dependencyResult = create.newResult(Dependencies.DEPENDENCIES.METADATA);
                    dependencyResult.add(create
                            .newRecord(Dependencies.DEPENDENCIES.METADATA)
                            .values(JSONB.valueOf("{\"type\": \"\", \"scope\": \"\", \"groupId\": \"org.hamcrest\", \"optional\": false, \"artifactId\": \"hamcrest-core\", \"classifier\": \"\", \"exclusions\": [], \"versionConstraints\": [{\"lowerBound\": \"1.3\", \"upperBound\": \"1.3\", \"isLowerHardRequirement\": false, \"isUpperHardRequirement\": false}]}")));
                    mockData[0] = new MockResult(dependencyResult.size(), dependencyResult);
                    return mockData;
                } else {
                    return new MockResult[]{};
                }
            }
        }
        var dbContext = DSL.using(new MockConnection(new DataProvider()));
        var expected = new DependencyTree(new Dependency("junit", "junit", "4.12"), List.of(new DependencyTree(new Dependency("org.hamcrest", "hamcrest-core", "1.3"), emptyList())));
//        var actual = mavenResolver.buildFullDependencyTree("junit", "junit", "4.12", false, dbContext);
//        assertEquals(expected, actual);
    }

    @Test
    public void buildFullDependencyTreeWithNoDependenciesTest() {
        class DataProvider implements MockDataProvider {
            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                return new MockResult[]{};
            }
        }
        var dbContext = DSL.using(new MockConnection(new DataProvider()));
        var expected = new DependencyTree(new Dependency("hello", "world", "42"), emptyList());
//        var actual = mavenResolver.buildFullDependencyTree("hello", "world", "42", false, dbContext);
//        assertEquals(expected, actual);
    }

    @Test
    public void buildFullDependencyTreeOnlineTest() {
        var dbContext = Mockito.mock(DSLContext.class);
        var expected = new DependencyTree(new Dependency("junit", "junit", "4.12"), List.of(new DependencyTree(new Dependency("org.hamcrest", "hamcrest-core", "1.3"), emptyList())));
        var actual = mavenResolver.buildFullDependencyTree("junit", "junit", "4.12", true, dbContext);
        assertEquals(expected, actual);
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

    @Test
    public void filterDependenciesByTimestampTest() {
        class DataProvider implements MockDataProvider {
            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                var create = DSL.using(SQLDialect.POSTGRES);
                var mockData = new MockResult[1];
                var packageVersionsResult = create.newResult(PackageVersions.PACKAGE_VERSIONS.VERSION);
                packageVersionsResult.add(create
                        .newRecord(PackageVersions.PACKAGE_VERSIONS.VERSION)
                        .values("1.2"));
                mockData[0] = new MockResult(packageVersionsResult.size(), packageVersionsResult);
                return mockData;
            }
        }
        var dbContext = DSL.using(new MockConnection(new DataProvider()));
        var expected = Set.of(new Dependency("org.hamcrest", "hamcrest-core", "1.2"));
        var actual = mavenResolver.filterDependenciesByTimestamp(Set.of(new Dependency("org.hamcrest", "hamcrest-core", "1.3")), new Timestamp(1307318400000L), dbContext);
        assertEquals(expected, actual);
    }

    @Test
    public void getArtifactDependenciesFromDatabaseTest() {
        class DataProvider implements MockDataProvider {
            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                var create = DSL.using(SQLDialect.POSTGRES);
                var mockData = new MockResult[1];
                var dependencyResult = create.newResult(Dependencies.DEPENDENCIES.METADATA);
                dependencyResult.add(create
                        .newRecord(Dependencies.DEPENDENCIES.METADATA)
                        .values(JSONB.valueOf("{\"type\": \"\", \"scope\": \"\", \"groupId\": \"org.hamcrest\", \"optional\": false, \"artifactId\": \"hamcrest-core\", \"classifier\": \"\", \"exclusions\": [], \"versionConstraints\": [{\"lowerBound\": \"1.3\", \"upperBound\": \"1.3\", \"isLowerHardRequirement\": false, \"isUpperHardRequirement\": false}]}")));
                dependencyResult.add(create
                        .newRecord(Dependencies.DEPENDENCIES.METADATA)
                        .values(JSONB.valueOf("{\"type\": \"\", \"scope\": \"\", \"groupId\": \"maven\", \"optional\": false, \"artifactId\": \"dependency\", \"classifier\": \"\", \"exclusions\": [], \"versionConstraints\": [{\"lowerBound\": \"1\", \"upperBound\": \"1\", \"isLowerHardRequirement\": false, \"isUpperHardRequirement\": false}]}")));
                mockData[0] = new MockResult(dependencyResult.size(), dependencyResult);
                return mockData;
            }
        }
        var connection = new MockConnection(new DataProvider());
        var dbContext = DSL.using(connection, SQLDialect.POSTGRES);
        var expected = List.of(new Dependency("org.hamcrest", "hamcrest-core", "1.3"), new Dependency("maven", "dependency", "1"));
        var actual = mavenResolver.getArtifactDependenciesFromDatabase("junit", "junit", "4.12", dbContext);
        assertEquals(expected, actual);
    }

    @Test
    public void getParentArtifactTest() {
        class DataProvider implements MockDataProvider {
            boolean parentExists;

            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                var create = DSL.using(SQLDialect.POSTGRES);
                var mockData = new MockResult[1];
                var packageVersionMetadataResult = create.newResult(PackageVersions.PACKAGE_VERSIONS.METADATA);
                if (!parentExists) {
                    packageVersionMetadataResult.add(create
                            .newRecord(PackageVersions.PACKAGE_VERSIONS.METADATA)
                            .values(JSONB.valueOf("{\"parentCoordinate\":\"\"}")));
                } else {
                    packageVersionMetadataResult.add(create
                            .newRecord(PackageVersions.PACKAGE_VERSIONS.METADATA)
                            .values(JSONB.valueOf("{\"parentCoordinate\":\"dependency:parent:1\"}")));
                }
                mockData[0] = new MockResult(packageVersionMetadataResult.size(), packageVersionMetadataResult);
                return mockData;
            }
        }
        var dataProvider = new DataProvider();
        var dbContext = DSL.using(new MockConnection(dataProvider));
        dataProvider.parentExists = false;
        var actual = mavenResolver.getParentArtifact("junit", "junit", "4.12", dbContext);
        assertNull(actual);
        dataProvider.parentExists = true;
        var parent = new Dependency("dependency", "parent", "1");
        actual = mavenResolver.getParentArtifact("junit", "junit", "4.12", dbContext);
        assertEquals(parent, actual);
    }
}
