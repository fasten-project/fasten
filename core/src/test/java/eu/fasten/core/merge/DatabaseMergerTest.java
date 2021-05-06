package eu.fasten.core.merge;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.graphdb.GraphMetadata;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.ModuleNames;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.jooq.Record2;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

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

    private static Map<Long, String> typeDictionary;
    private static Map<Long, String> typeMap;
    private static Map<String, Pair<Long[], Long[]>> universalCHA;
    private static Map<String, Long> namespacesMap;
    private static GraphMetadata graphMetadata;

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
                "/test.group/Main", Pair.of(new Long[]{}, new Long[]{}),
                "/test.group/Foo", Pair.of(new Long[]{2L}, new Long[]{}),
                "/test.group/Bar", Pair.of(new Long[]{2L}, new Long[]{}),
                "/test.group/Baz", Pair.of(new Long[]{4L}, new Long[]{})
        );

        namespacesMap = Map.of(
                "/test.group/Main", 1L,
                "/java.lang/Object", 2L,
                "/test.group/Baz", 3L,
                "/test.group/Bar", 4L,
                "/test.group/Foo", 5L
        );

        var gid2nodeMap = new Long2ObjectOpenHashMap<GraphMetadata.NodeMetadata>();
        gid2nodeMap.put(MAIN_INIT, new GraphMetadata.NodeMetadata("/test.group/Main", "<init>()" + "/java.lang/VoidType", List.of(new GraphMetadata.ReceiverRecord(6, GraphMetadata.ReceiverRecord.CallType.SPECIAL, "<init>()VoidType", List.of("/java.lang/Object")))));
        gid2nodeMap.put(MAIN_MAIN_METHOD, new GraphMetadata.NodeMetadata("/test.group/Main", "main(/java.lang/String[])/java.lang/VoidType", List.of(
            new GraphMetadata.ReceiverRecord(8, GraphMetadata.ReceiverRecord.CallType.SPECIAL,"<init>(/java.lang/IntegerType,/java.lang/IntegerType,/java" + ".lang/IntegerType)/java.lang/VoidType", List.of("/test.group/Baz")),
            new GraphMetadata.ReceiverRecord(9, GraphMetadata.ReceiverRecord.CallType.VIRTUAL, "superMethod()/java.lang/VoidType", List.of("/test.group/Bar", "/test" + ".group/Bar")),
            new GraphMetadata.ReceiverRecord(11, GraphMetadata.ReceiverRecord.CallType.SPECIAL, "<init>(/java" + ".lang/IntegerType,/java.lang/IntegerType)/java.lang/VoidType", List.of("/test.group/Bar")),
            new GraphMetadata.ReceiverRecord(14, GraphMetadata.ReceiverRecord.CallType.STATIC, "staticMethod()/java.lang/IntegerType", List.of("/test.group/Foo")),
            new GraphMetadata.ReceiverRecord(15, GraphMetadata.ReceiverRecord.CallType.SPECIAL, "<init>(/java" + ".lang/IntegerType)/java.lang/VoidType", List.of("/test.group/Foo"))
        )));
        graphMetadata = new GraphMetadata(gid2nodeMap);
    }

    @Test
    public void mergeWithCHATest() throws RocksDBException {
        var connection = new MockConnection(new MockProvider());
        var context = DSL.using(connection, SQLDialect.POSTGRES);

        var directedGraph = createMockDirectedGraph();

        var rocksDao = Mockito.mock(RocksDao.class);
        Mockito.when(rocksDao.getGraphData(42)).thenReturn(directedGraph);
        Mockito.when(rocksDao.getGraphMetadata(42, directedGraph)).thenReturn(graphMetadata);

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
        Mockito.when(rocksDao.getGraphMetadata(42, directedGraph.build())).thenReturn(graphMetadata);

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
        private final String namespacesQuery;
        private final String typeDictionaryQuery;
        private final String typeMapQuery;
        private final String dependenciesQuery;

        public MockProvider() {
            this.context = DSL.using(SQLDialect.POSTGRES);

            this.modulesIdsQuery = context
                    .select(Callables.CALLABLES.MODULE_ID)
                    .from(Callables.CALLABLES)
                    .getSQL();
            this.namespacesQuery = context
                    .select(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME)
                    .from(ModuleNames.MODULE_NAMES)
                    .getSQL();
            this.universalCHAQuery = context
                    .select(Modules.MODULES.MODULE_NAME_ID, Modules.MODULES.SUPER_CLASSES, Modules.MODULES.SUPER_INTERFACES)
                    .from(Modules.MODULES)
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

            } else if (sql.startsWith(typeDictionaryQuery)) {
                mock[0] = createTypeDictionary();

            } else if (sql.startsWith(typeMapQuery)) {
                mock[0] = createTypeMap();
            } else if (sql.startsWith(namespacesQuery)) {
                mock[0] = createNamespaces();
            }

            return mock;
        }

        private MockResult createUniversalCHA() {
            var result = context.newResult(Modules.MODULES.MODULE_NAME_ID, Modules.MODULES.SUPER_CLASSES, Modules.MODULES.SUPER_INTERFACES);
            for (var type : universalCHA.entrySet()) {
                result.add(context
                        .newRecord(Modules.MODULES.MODULE_NAME_ID, Modules.MODULES.SUPER_CLASSES, Modules.MODULES.SUPER_INTERFACES)
                        .values(namespacesMap.get(type.getKey()), type.getValue().getLeft(), type.getValue().getRight()));
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createNamespaces() {
            Result<Record2<Long, String>> result = context.newResult(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME);
            for (var namespace : namespacesMap.entrySet()) {
                result.add(context
                        .newRecord(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME)
                        .values(namespace.getValue(), namespace.getKey()));
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
