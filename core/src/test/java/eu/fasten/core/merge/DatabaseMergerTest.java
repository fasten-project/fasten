package eu.fasten.core.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.enums.ReceiverType;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Edges;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.HashSet;
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
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rocksdb.RocksDBException;

public class DatabaseMergerTest {

    static long constructorSource = 101;
    static long constructorTargetInit = 201;
    static long constructorTargetClinit = 301;

    @Test
    public void mergeWithCHATest() throws RocksDBException {
        var connection = new MockConnection(new MockProvider());
        var context = DSL.using(connection, SQLDialect.POSTGRES);

        var directedGraph = createMockDirectedGraph();

        var rocksDao = Mockito.mock(RocksDao.class);
        Mockito.when(rocksDao.getGraphData(42)).thenReturn(directedGraph);

        var merger = new DatabaseMerger(new HashSet<>(), context, rocksDao);

        var mergedGraph = merger.mergeWithCHA(42);

        for (var source : mergedGraph.nodes()) {
            for (var target : mergedGraph.successors(source)) {
                System.out.println(source + " -> " + target);
            }
        }

        assertNotNull(mergedGraph);

        assertEquals(mergedGraph.successors(constructorSource),
                LongArrayList.wrap(new long[]{constructorTargetInit, constructorTargetClinit}));
    }

    private DirectedGraph createMockDirectedGraph() {
        var directedGraph = new ArrayImmutableDirectedGraph.Builder();
        directedGraph.addInternalNode(constructorSource);
        directedGraph.addInternalNode(102);
        directedGraph.addInternalNode(103);
        directedGraph.addInternalNode(104);
        directedGraph.addExternalNode(105);
        directedGraph.addExternalNode(106);

        // Constructor call
        directedGraph.addArc(constructorSource, constructorSource);
        // Internal call that needs to be discarded
        directedGraph.addArc(102, 103);
        // Standard external call
        directedGraph.addArc(103, 105);
        // Callback
        directedGraph.addArc(106, 104);

        return directedGraph.build();
    }

    private static class MockProvider implements MockDataProvider {

        private final DSLContext context;

        private final String modulesIdsQuery;
        private final String universalCHAQuery;
        private final String arcsQuery;
        private final String typeDictionaryQuery;
        private final String typeMapQuery;

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
        }

        @Override
        public MockResult[] execute(MockExecuteContext ctx) {
            MockResult[] mock = new MockResult[1];

            var sql = ctx.sql();

            if (sql.startsWith(modulesIdsQuery)) {
                mock[0] = new MockResult(0, context.newResult(Callables.CALLABLES.MODULE_ID));

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
            result.add(context
                    .newRecord(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA)
                    .values("/name.space/ClassName", JSONB.valueOf("{\"final\": false, " +
                            "\"access\": \"public\", " +
                            "\"superClasses\": [\"/name.space/SuperClassName\", \"/java.lang/Object\"], " +
                            "\"superInterfaces\": [\"/java.io/Serializable\"]}")));
            return new MockResult(result.size(), result);
        }

        private MockResult createArcs() {
            Result<Record3<Long, Long, ReceiverRecord[]>> result = context.newResult(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS);
            result.add(context
                    .newRecord(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                    .values(constructorSource, constructorSource, new ReceiverRecord[]{}));
            result.add(context
                    .newRecord(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                    .values((long) 103, (long) 105, new ReceiverRecord[]{new ReceiverRecord(100, ReceiverType.static_, "/java.util/Collections")}));
            result.add(context
                    .newRecord(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                    .values((long) 106, (long) 104, new ReceiverRecord[]{new ReceiverRecord(100, ReceiverType.static_, "/java.util/Collections")}));
            return new MockResult(result.size(), result);
        }

        private MockResult createTypeDictionary() {
            Result<Record2<String, Long>> result = context.newResult(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID);
            result.add(context
                    .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                    .values("/name.space/ClassName.%3Cclinit%3E()%2Fjava.lang%2FVoidType", constructorTargetClinit));
            result.add(context
                    .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                    .values("/name.space/ClassName.%3Cinit%3E()%2Fjava.lang%2FVoidType", constructorTargetInit));
            result.add(context
                    .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                    .values("/java.util/Collections.unmodifiableMap(Map)Map", (long) 202));
            result.add(context
                    .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                    .values("/java.util/Collections.unmodifiableMap(Map)Map", (long) 203));
            result.add(context
                    .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                    .values("/java.util/Collections.unmodifiableMap(Map)Map", (long) 204));
            result.add(context
                    .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                    .values("/java.util/Collections.unmodifiableMap(Map)Map", (long) 205));
            return new MockResult(result.size(), result);
        }

        private MockResult createTypeMap() {
            Result<Record2<Long, String>> result = context.newResult(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI);
            result.add(context
                    .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                    .values((long) 101, "/name.space/ClassName.%3Cinit%3E()%2Fjava.lang%2FVoidType"));
            result.add(context
                    .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                    .values((long) 102, "/java.util/Collections.unmodifiableMap(Map)Map"));
            result.add(context
                    .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                    .values((long) 103, "/java.util/Collections.unmodifiableMap(Map)Map"));
            result.add(context
                    .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                    .values((long) 104, "/java.util/Collections.unmodifiableMap(Map)Map"));
            result.add(context
                    .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                    .values((long) 105, "/java.util/Collections.unmodifiableMap(Map)Map"));
            result.add(context
                    .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                    .values((long) 106, "/java.util/Collections.unmodifiableMap(Map)Map"));
            return new MockResult(result.size(), result);
        }
    }
}
