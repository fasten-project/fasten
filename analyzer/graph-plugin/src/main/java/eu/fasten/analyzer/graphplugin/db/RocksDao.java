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

package eu.fasten.analyzer.graphplugin.db;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import eu.fasten.core.index.BVGraphSerializer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.NullInputStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.webgraph.BVGraph;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

public class RocksDao implements Closeable {

    private final RocksDB rocksDb;
    private Kryo kryo;

    /**
     * Constructor of RocksDao (Database Access Object).
     *
     * @param dbDir Directory where RocksDB data will be stored
     * @throws RocksDBException if there is an error loading or opening RocksDB instance
     */
    public RocksDao(final String dbDir) throws RocksDBException {
        RocksDB.loadLibrary();
        final ColumnFamilyOptions cfOptions = new ColumnFamilyOptions()
                .setCompressionType(CompressionType.LZ4_COMPRESSION);
        final DBOptions dbOptions = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true);
        final List<ColumnFamilyDescriptor> cfDescriptors = Collections.singletonList(
                new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, cfOptions));
        final List<ColumnFamilyHandle> columnFamilyHandles = new ArrayList<>();
        this.rocksDb = RocksDB.open(dbOptions, dbDir, cfDescriptors, columnFamilyHandles);
        initKryo();
    }

    private void initKryo() {
        kryo = new Kryo();
        kryo.register(BVGraph.class, new BVGraphSerializer(kryo));
        kryo.register(byte[].class);
        kryo.register(InputBitStream.class);
        kryo.register(NullInputStream.class);
        kryo.register(EliasFanoMonotoneLongBigList.class, new JavaSerializer());
        kryo.register(MutableString.class, new FieldSerializer<>(kryo, MutableString.class));
        kryo.register(Properties.class);
        kryo.register(long[].class);
        kryo.register(Long2IntOpenHashMap.class);
    }

    /**
     * Inserts graph (nodes and edges) into RocksDB database.
     *
     * @param nodes       List of GID nodes (first internal nodes, then external nodes)
     * @param numInternal Number of internal nodes in nodes list
     * @param edges       List of edges (pairs of GIDs)
     */
    public void saveToRocksDb(List<Long> nodes, int numInternal, List<List<Long>> edges) {
        // TODO: Implement similar to KnowledgeBase
    }

    @Override
    public void close() {
        if (rocksDb != null) {
            rocksDb.close();
        }
    }
}
