package eu.fasten.core.merge;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.graphdb.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.enums.ReceiverType;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Edges;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rocksdb.RocksDBException;

public class DatabaseMergerTest {

    @Test
    public void mergeWithCHATest() throws RocksDBException, FileNotFoundException {
        var artifact = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("merge/artifactERCG.json"))
                .getFile());
        var foo = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("merge/FooERCG.json"))
                .getFile());
        var bar = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("merge/BarERCG.json"))
                .getFile());
        var baz = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("merge/BazERCG.json"))
                .getFile());

        var deps = new ArrayList<ExtendedRevisionJavaCallGraph>();
        var tokener = new JSONTokener(new FileReader(artifact));
        var artifactERCG = new ExtendedRevisionJavaCallGraph(new JSONObject(tokener));

        tokener = new JSONTokener(new FileReader(foo));
        deps.add(new ExtendedRevisionJavaCallGraph(new JSONObject(tokener)));
        tokener = new JSONTokener(new FileReader(bar));
        deps.add(new ExtendedRevisionJavaCallGraph(new JSONObject(tokener)));
        tokener = new JSONTokener(new FileReader(baz));
        deps.add(new ExtendedRevisionJavaCallGraph(new JSONObject(tokener)));

        var connection = new MockConnection(new MockProvider(artifactERCG, deps));
        var context = DSL.using(connection, SQLDialect.POSTGRES);

        var directedGraph = createMockDirectedGraph(artifactERCG);

        var rocksDao = Mockito.mock(RocksDao.class);
        Mockito.when(rocksDao.getGraphData(42)).thenReturn(directedGraph);

        var merger = new DatabaseMerger(new HashSet<>(), context, rocksDao);

        var mergedGraph = merger.mergeWithCHA(42);

        var map = new HashMap<>(artifactERCG.mapOfAllMethods());
        for (var dep : deps) {
            map.putAll(dep.mapOfAllMethods());
        }
        for (var source : mergedGraph.nodes()) {
            for (var target : mergedGraph.successors(source)) {
                System.out.println("(" + source + ")" + map.get(source.intValue()).getUri() + " -> " + "(" + target + ")" + map.get(target.intValue()).getUri());
            }
        }

        assertNotNull(mergedGraph);

//        assertEquals(mergedGraph.successors(constructorSource),
//                LongArrayList.wrap(new long[]{constructorTargetInit, constructorTargetClinit}));
    }

    private DirectedGraph createMockDirectedGraph(ExtendedRevisionJavaCallGraph artifact) {
        var directedGraph = new ArrayImmutableDirectedGraph.Builder();
        for (var type : artifact.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
            for (var method : type.getValue().getMethods().keySet()) {
                directedGraph.addInternalNode(method);
            }
        }
        for (var type : artifact.getClassHierarchy().get(JavaScope.externalTypes).entrySet()) {
            for (var method : type.getValue().getMethods().keySet()) {
                directedGraph.addExternalNode(method);
            }
        }

        for (var call : artifact.getGraph().getInternalCalls().keySet()) {
            directedGraph.addArc(call.get(0), call.get(1));
        }
        for (var call : artifact.getGraph().getExternalCalls().keySet()) {
            directedGraph.addArc(call.get(0), call.get(1));
        }

        return directedGraph.build();
    }

    private static class MockProvider implements MockDataProvider {

        private final DSLContext context;
        private final ExtendedRevisionJavaCallGraph artifact;
        private final List<ExtendedRevisionJavaCallGraph> deps;

        private final String modulesIdsQuery;
        private final String universalCHAQuery;
        private final String arcsQuery;
        private final String typeDictionaryQuery;
        private final String typeMapQuery;

        public MockProvider(ExtendedRevisionJavaCallGraph artifact,
                            List<ExtendedRevisionJavaCallGraph> deps) {
            this.context = DSL.using(SQLDialect.POSTGRES);

            this.artifact = artifact;
            this.deps = deps;

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
            var uris = new HashSet<String>();
            for (var node : artifact.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
                if (uris.add(node.getKey().toString())) {
                    result.add(context
                            .newRecord(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA)
                            .values(node.getKey().toString(), JSONB.valueOf(node.getValue().toJSON().toString())));
                }
            }
            for (var dep : deps) {
                for (var node : dep.getClassHierarchy().get(JavaScope.internalTypes).entrySet()) {
                    if (uris.add(node.getKey().toString())) {
                        result.add(context
                                .newRecord(Modules.MODULES.NAMESPACE, Modules.MODULES.METADATA)
                                .values(node.getKey().toString(), JSONB.valueOf(node.getValue().toJSON().toString())));
                    }
                }
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createArcs() {
            Result<Record3<Long, Long, ReceiverRecord[]>> result = context.newResult(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS);
            for (var arc : artifact.getGraph().getExternalCalls().entrySet()) {
                var receivers = new ArrayList<ReceiverRecord>();
                for (var receiver : arc.getValue().entrySet()) {
                    var rcvr = (HashMap<Object, Object>) receiver.getValue();
                    receivers.add(new ReceiverRecord((Integer) rcvr.get("line"), getReceiverType((String) rcvr.get("type")), (String) rcvr.get("receiver")));
                }
                result.add(context
                        .newRecord(Edges.EDGES.SOURCE_ID, Edges.EDGES.TARGET_ID, Edges.EDGES.RECEIVERS)
                        .values(arc.getKey().get(0).longValue(), arc.getKey().get(1).longValue(),
                                receivers.toArray(ReceiverRecord[]::new)));
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createTypeDictionary() {
            Result<Record2<String, Long>> result = context.newResult(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID);
            for (var type : artifact.internalNodeIdToTypeMap().values()) {
                for (var node : type.getMethods().entrySet()) {
                    result.add(context
                            .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                            .values(node.getValue().getUri().toString(), node.getKey().longValue()));
                }
            }
            for (var dep : deps) {
                for (var type : dep.internalNodeIdToTypeMap().values()) {
                    for (var node : type.getMethods().entrySet()) {
                        result.add(context
                                .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                                .values(node.getValue().getUri().toString(), node.getKey().longValue()));
                    }
                }
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createTypeMap() {
            Result<Record2<Long, String>> result = context.newResult(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI);
            for (var node : artifact.mapOfAllMethods().entrySet()) {
                result.add(context
                        .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                        .values(node.getKey().longValue(), node.getValue().getUri().toString()));
            }
            return new MockResult(result.size(), result);
        }

        private ReceiverType getReceiverType(String type) {
            switch (type) {
                case "invokestatic":
                    return ReceiverType.static_;
                case "invokespecial":
                    return ReceiverType.special;
                case "invokevirtual":
                    return ReceiverType.virtual;
                case "invokedynamic":
                    return ReceiverType.dynamic;
                case "invokeinterface":
                    return ReceiverType.interface_;
                default:
                    return null;
            }
        }
    }
}
