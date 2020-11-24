package eu.fasten.core.merge;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.enums.ReceiverType;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Edges;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord;
import java.util.HashSet;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record1;
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

    @Test
    public void mergeWithCHATest() throws RocksDBException {
        var connection = new MockConnection(new MockProvider());
        var context = DSL.using(connection, SQLDialect.POSTGRES);

        var rocksDao = Mockito.mock(RocksDao.class);
        var directedGraph = createMockDirectedGraph();
        Mockito.when(rocksDao.getGraphData(42)).thenReturn(directedGraph);

        var merger = new DatabaseMerger(new HashSet<>(), context, rocksDao);
        var mergedGraph = merger.mergeWithCHA(42);

        assertNotNull(mergedGraph);
    }

    private DirectedGraph createMockDirectedGraph() {
        var directedGraph = new ArrayImmutableDirectedGraph.Builder();
        directedGraph.addInternalNode(101);
        directedGraph.addInternalNode(102);
        directedGraph.addInternalNode(103);
        directedGraph.addInternalNode(104);
        directedGraph.addExternalNode(105);
        directedGraph.addExternalNode(106);

        directedGraph.addArc(101, 101);
        directedGraph.addArc(102, 103);
        directedGraph.addArc(101, 105);
        directedGraph.addArc(106, 104);

        return directedGraph.build();
    }

    private static class MockProvider implements MockDataProvider {

        private final DSLContext context;

        private final String modulesIdsQuery;
        private final String modulesNamespacesMetadataQuery;
        private final String edgesQuery;
        private final String internalCallablesQuery;
        private final String externalCallablesQuery;

        public MockProvider() {
            this.context = DSL.using(SQLDialect.POSTGRES);
            this.modulesIdsQuery = context
                    .select(Callables.CALLABLES.MODULE_ID)
                    .from(Callables.CALLABLES)
                    .getSQL();
            this.modulesNamespacesMetadataQuery = context
                    .select(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA)
                    .from(Modules.MODULES)
                    .getSQL();
            this.edgesQuery = context
                    .select(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                    .from(Edges.EDGES)
                    .getSQL();
            this.internalCallablesQuery = context
                    .select(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                    .from(Callables.CALLABLES)
                    .getSQL();
            this.externalCallablesQuery = context
                    .select(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                    .from(Callables.CALLABLES)
                    .getSQL();
        }

        @Override
        public MockResult[] execute(MockExecuteContext ctx) {
            MockResult[] mock = new MockResult[1];

            var sql = ctx.sql();

            if (sql.startsWith(modulesIdsQuery)) {
                Result<Record1<Long>> result = context.newResult(Callables.CALLABLES.MODULE_ID);
                result.add(context
                        .newRecord(Callables.CALLABLES.MODULE_ID)
                        .values((long) 10));
                mock[0] = new MockResult(1, result);
            } else if (sql.startsWith(modulesNamespacesMetadataQuery)) {
                Result<Record2<String, JSONB>> result = context.newResult(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA);
                result.add(context
                        .newRecord(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA)
                        .values("/name.space/ClassName", JSONB.valueOf("{\"final\": false, " +
                                "\"access\": \"public\", " +
                                "\"superClasses\": [\"/name.space/SuperClassName\", \"/java.lang/Object\"], " +
                                "\"superInterfaces\": [\"/java.io/Serializable\"]}")));
                mock[0] = new MockResult(1, result);
            } else if (sql.startsWith(edgesQuery)) {
                Result<Record3<Long, Long, ReceiverRecord[]>> result = context.newResult(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS);
                result.add(context
                        .newRecord(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                        .values((long) 101, (long) 101, new ReceiverRecord[] {new ReceiverRecord(100, ReceiverType.static_, "/java.util/Collections")}));
                result.add(context
                        .newRecord(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                        .values((long) 101, (long) 105, new ReceiverRecord[] {new ReceiverRecord(100, ReceiverType.static_, "/java.util/Collections")}));
                result.add(context
                        .newRecord(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                        .values((long) 106, (long) 104, new ReceiverRecord[] {new ReceiverRecord(100, ReceiverType.static_, "/java.util/Collections")}));
                mock[0] = new MockResult(3, result);
            } else if (sql.startsWith(internalCallablesQuery)) {
                Result<Record2<String, Long>> result = context.newResult(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID);
                result.add(context
                        .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                        .values("/java.util/Collections.unmodifiableMap(Map)Map", (long) 201));
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
                mock[0] = new MockResult(5, result);
            } else if (sql.startsWith(externalCallablesQuery)) {
                Result<Record2<Long, String>> result = context.newResult(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI);
                result.add(context
                        .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                        .values((long) 101, "/java.util/Collections.unmodifiableMap(Map)Map"));
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
                mock[0] = new MockResult(5, result);
            }

            return mock;
        }
    }
}
