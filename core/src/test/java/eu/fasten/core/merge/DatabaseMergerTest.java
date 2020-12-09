package eu.fasten.core.merge;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.enums.ReceiverType;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Edges;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record2;
import org.jooq.Record3;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rocksdb.RocksDBException;

public class DatabaseMergerTest {

    private final static long MAIN_INIT = 0;
    private final static long MAIN_MAIN_METHOD = 1;
    private final static long FOO_CLINIT = 100;
    private final static long FOO_INIT = 101;
    private final static long FOO_FOO_METHOD = 102;
    private final static long FOO_STATIC_METHOD = 103;
    private final static long BAR_INIT = 200;
    private final static long BAR_SUPER_METHOD = 201;
    private final static long BAZ_INIT = 300;
    private final static long BAZ_SUPER_METHOD = 301;

    private static Map<Pair<Long, Long>, ReceiverRecord[]> arcs;
    private static Map<Long, String> typeDictionary;
    private static Map<Long, String> typeMap;
    private static Map<String, String> universalCHA;

    @BeforeAll
    static void setUp() {
        typeMap = Map.of(
                MAIN_INIT, "/test.group/Main.%3Cinit%3E()%2Fjava.lang%2FVoidType",
                MAIN_MAIN_METHOD, "/test.group/Main.main(%2Fjava.lang%2FString%5B%5D)%2Fjava.lang%2FVoidType",
                (long) 2, "/java.lang/Object.%3Cinit%3E()VoidType",
                (long) 3, "/test.group/Baz.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
                (long) 4, "/test.group/Bar.superMethod()%2Fjava.lang%2FVoidType",
                (long) 5, "/test.group/Bar.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
                (long) 6, "/test.group/Foo.staticMethod()%2Fjava.lang%2FIntegerType",
                (long) 7, "/test.group/Foo.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType"
        );

        typeDictionary = Map.of(
                MAIN_INIT, "/test.group/Main.%3Cinit%3E()%2Fjava.lang%2FVoidType",
                MAIN_MAIN_METHOD, "/test.group/Main.main(%2Fjava.lang%2FString%5B%5D)%2Fjava.lang%2FVoidType",
                FOO_CLINIT, "/test.group/Foo.%3Cclinit%3E()%2Fjava.lang%2FVoidType",
                FOO_INIT, "/test.group/Foo.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
                FOO_FOO_METHOD, "/test.group/Foo.fooMethod()%2Fjava.lang%2FVoidType",
                FOO_STATIC_METHOD, "/test.group/Foo.staticMethod()%2Fjava.lang%2FIntegerType",
                BAR_INIT, "/test.group/Bar.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
                BAR_SUPER_METHOD, "/test.group/Bar.superMethod()%2Fjava.lang%2FVoidType",
                BAZ_INIT, "/test.group/Baz.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
                BAZ_SUPER_METHOD, "/test.group/Baz.superMethod()%2Fjava.lang%2FVoidType"
        );

        universalCHA = Map.of(
                "/test.group/Main", "{\"superInterfaces\": [],\"superClasses\":[\"/java.lang/Object\"]}",
                "/test.group/Foo", "{\"superInterfaces\": [],\"superClasses\":[\"/java.lang/Object\"]}",
                "/test.group/Bar", "{\"superInterfaces\": [],\"superClasses\":[\"/java.lang/Object\"]}",
                "/test.group/Baz", "{\"superInterfaces\": [],\"superClasses\":[\"/test.group/Bar\"]}"
        );

        arcs = Map.of(
                Pair.of(MAIN_INIT, MAIN_INIT), new ReceiverRecord[0],
                Pair.of(MAIN_INIT, (long) 2), new ReceiverRecord[]{
                        new ReceiverRecord(6, ReceiverType.special, "/java.lang/Object")
                },
                Pair.of(MAIN_MAIN_METHOD, (long) 3), new ReceiverRecord[]{
                        new ReceiverRecord(8, ReceiverType.special, "/test.group/Baz")
                },
                Pair.of(MAIN_MAIN_METHOD, (long) 4), new ReceiverRecord[]{
                        new ReceiverRecord(9, ReceiverType.virtual, "/test.group/Bar"),
                        new ReceiverRecord(12, ReceiverType.interface_, "/test.group/Bar")
                },
                Pair.of(MAIN_MAIN_METHOD, (long) 5), new ReceiverRecord[]{
                        new ReceiverRecord(11, ReceiverType.special, "/test.group/Bar")
                },
                Pair.of(MAIN_MAIN_METHOD, (long) 6), new ReceiverRecord[]{
                        new ReceiverRecord(14, ReceiverType.static_, "/test.group/Foo")
                },
                Pair.of(MAIN_MAIN_METHOD, (long) 7), new ReceiverRecord[]{
                        new ReceiverRecord(15, ReceiverType.special, "/test.group/Foo")
                }
        );
    }

    @Test
    public void mergeWithCHATest() throws RocksDBException {
        var connection = new MockConnection(new MockProvider());
        var context = DSL.using(connection, SQLDialect.POSTGRES);

        var directedGraph = createMockDirectedGraph();

        var rocksDao = Mockito.mock(RocksDao.class);
        Mockito.when(rocksDao.getGraphData(42)).thenReturn(directedGraph);

        var merger = new DatabaseMerger(List.of("group1:art1:ver1", "group2:art2:ver2"),
                context, rocksDao);

        var mergedGraph = merger.mergeWithCHA(42);

        assertNotNull(mergedGraph);

        assertEquals(new LongArraySet(new long[]{MAIN_INIT, MAIN_MAIN_METHOD, FOO_CLINIT, FOO_INIT,
                        FOO_STATIC_METHOD, BAR_INIT, BAR_SUPER_METHOD, BAZ_INIT, BAZ_SUPER_METHOD, 2}),
                mergedGraph.nodes());
        assertEquals(new LongArraySet(new long[]{FOO_CLINIT, FOO_INIT, FOO_STATIC_METHOD, BAR_INIT,
                BAR_SUPER_METHOD, BAZ_INIT, BAZ_SUPER_METHOD, 2}), mergedGraph.externalNodes());

        assertEquals(mergedGraph.successors(MAIN_INIT), LongArrayList.wrap(new long[]{MAIN_INIT, 2}));

        assertEquals(new HashSet<>(mergedGraph.successors(MAIN_MAIN_METHOD)),
                Set.of(FOO_CLINIT, FOO_INIT, FOO_STATIC_METHOD, BAR_INIT, BAR_SUPER_METHOD,
                        BAZ_INIT, BAZ_SUPER_METHOD));
    }

    @Test
    public void recursiveCallsTest() throws RocksDBException {
        var connection = new MockConnection(new MockProvider());
        var context = DSL.using(connection, SQLDialect.POSTGRES);

        // random internal node with recursive call
        final int nodeWithRecursiveCall = 3;

        var directedGraph = new ArrayImmutableDirectedGraph.Builder();
        directedGraph.addInternalNode(nodeWithRecursiveCall);
        directedGraph.addArc(nodeWithRecursiveCall, nodeWithRecursiveCall);

        var rocksDao = Mockito.mock(RocksDao.class);
        Mockito.when(rocksDao.getGraphData(42)).thenReturn(directedGraph.build());

        var merger = new DatabaseMerger(List.of("group1:art1:ver1", "group2:art2:ver2"),
                context, rocksDao);

        var mergedGraph = merger.mergeWithCHA(42);

        assertNotNull(mergedGraph);

        assertTrue(mergedGraph.nodes().contains(nodeWithRecursiveCall));
        assertEquals(LongArrayList.wrap(new long[]{nodeWithRecursiveCall}),
                mergedGraph.successors(nodeWithRecursiveCall));
    }

    private DirectedGraph createMockDirectedGraph() {
        var directedGraph = new ArrayImmutableDirectedGraph.Builder();
        directedGraph.addInternalNode(MAIN_INIT);
        directedGraph.addInternalNode(MAIN_MAIN_METHOD);
        typeMap.keySet().stream()
                .filter(n -> n != MAIN_INIT && n != MAIN_MAIN_METHOD)
                .forEach(directedGraph::addExternalNode);

        directedGraph.addArc(MAIN_INIT, MAIN_INIT);
        directedGraph.addArc(MAIN_INIT, 2);
        typeMap.keySet().stream()
                .filter(n -> n != MAIN_INIT && n != MAIN_MAIN_METHOD)
                .forEach(n -> directedGraph.addArc(MAIN_MAIN_METHOD, n));

        return directedGraph.build();
    }

    private static class MockProvider implements MockDataProvider {

        private final DSLContext context;

        private final String modulesIdsQuery;
        private final String universalCHAQuery;
        private final String arcsQuery;
        private final String typeDictionaryQuery;
        private final String typeMapQuery;
        private final String dependenciesQuery;

        public MockProvider() {
            this.context = DSL.using(SQLDialect.POSTGRES);

            this.modulesIdsQuery = context
                    .select(Callables.CALLABLES.MODULE_ID)
                    .from(Callables.CALLABLES)
                    .getSQL();
            this.universalCHAQuery = context
                    .select(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA)
                    .from(Modules.MODULES)
                    .getSQL();
            this.arcsQuery = context
                    .select(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                    .from(Edges.EDGES)
                    .getSQL();
            this.typeDictionaryQuery = context
                    .select(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                    .from(Callables.CALLABLES)
                    .getSQL();
            this.typeMapQuery = context
                    .select(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                    .from(Callables.CALLABLES)
                    .getSQL();
            this.dependenciesQuery = context
                    .select(PackageVersions.PACKAGE_VERSIONS.ID)
                    .from(PackageVersions.PACKAGE_VERSIONS)
                    .getSQL();
        }

        @Override
        public MockResult[] execute(MockExecuteContext ctx) {
            MockResult[] mock = new MockResult[1];

            var sql = ctx.sql();

            if (sql.startsWith(modulesIdsQuery)) {
                mock[0] = new MockResult(0, context.newResult(Callables.CALLABLES.MODULE_ID));

            } else if (sql.startsWith(dependenciesQuery)) {
                mock[0] = new MockResult(0, context.newResult(PackageVersions.PACKAGE_VERSIONS.ID));

            } else if (sql.startsWith(universalCHAQuery)) {
                mock[0] = createUniversalCHA();

            } else if (sql.startsWith(arcsQuery)) {
                mock[0] = createArcs();

            } else if (sql.startsWith(typeDictionaryQuery)) {
                mock[0] = createTypeDictionary();

            } else if (sql.startsWith(typeMapQuery)) {
                mock[0] = createTypeMap();
            }

            return mock;
        }

        private MockResult createUniversalCHA() {
            Result<Record2<String, JSONB>> result = context.newResult(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA);
            for (var type : universalCHA.entrySet()) {
                result.add(context
                        .newRecord(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA)
                        .values(type.getKey(), JSONB.valueOf(type.getValue())));
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createArcs() {
            Result<Record3<Long, Long, ReceiverRecord[]>> result = context.newResult(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS);
            for (var arc : arcs.entrySet()) {
                result.add(context
                        .newRecord(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                        .values(arc.getKey().getLeft(), arc.getKey().getRight(), arc.getValue()));
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createTypeDictionary() {
            Result<Record2<String, Long>> result = context.newResult(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID);
            for (var node : typeDictionary.entrySet()) {
                result.add(context
                        .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                        .values(node.getValue(), node.getKey()));
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createTypeMap() {
            Result<Record2<Long, String>> result = context.newResult(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI);
            for (var node : typeMap.entrySet()) {
                result.add(context
                        .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                        .values(node.getKey(), node.getValue()));
            }
            return new MockResult(result.size(), result);
        }
    }
}
