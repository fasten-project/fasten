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

package eu.fasten.core.data.graphdb;

import java.util.List;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * This class contains the metadata associated with the nodes of a call graph.
 * Such metadata is stored by the {@link RocksDao} class in a suitable column family of the RocksDB
 * database, and can be recovered after reading the graph using
 * {@link RocksDao#getGraphMetadata(long, eu.fasten.core.data.DirectedGraph)}, if needed.
 */

public class GraphMetadata {
    /**
     * This class represent the metadata associated with a node. The FASTEN Java URI is split into the
     * type part, and the signature part.
     * 
     * <p>
     * Since this class is intended for internal use only, and all fields are public, final and
     * immutable, no getters/setters are provided.
     */
    public static final class NodeMetadata {
        /** The type part of the FASTEN Java URI of the node (up to the dot). */
        public final String type;
        /** The signature part of the FASTEN Java URI of the node (after the dot). */
        public final String signature;
        /** The list of receivers. */
        public final List<ReceiverRecord> receiverRecords;

        public NodeMetadata(String type, String signature, List<ReceiverRecord> receiverRecords) {
            this.type = type;
            this.signature = signature;
            this.receiverRecords = receiverRecords;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            NodeMetadata other = (NodeMetadata)obj;
            if (!type.equals(other.type)) return false;
            if (!signature.equals(other.signature)) return false;
            if (!receiverRecords.equals(other.receiverRecords)) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return type.hashCode() ^ signature.hashCode() ^ receiverRecords.hashCode();
        }
        
        @Override
        public String toString() {
            return "[" + type + ", " + signature + ", " + receiverRecords + "]";
        }
    }

    /**
     * This class represent compactly a receiver record. The {@link Type} enum matches closely the
     * jOOQ-generated one in {@link eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord}.
     * 
     * <p>
     * Since this class is intended for internal use only, and all fields are public, final and
     * immutable, no getters/setters are provided.
     */
    public static final class ReceiverRecord {
        public static enum Type {
            STATIC,
            DYNAMIC,
            VIRTUAL,
            INTERFACE,
            SPECIAL
        }

        public ReceiverRecord(int line, Type type, String receiverUri) {
            this.line = line;
            this.type = type;
            this.receiverUri = receiverUri;
        }
        
        public ReceiverRecord(eu.fasten.core.data.metadatadb.codegen.udt.records.ReceiverRecord record) {
            this.line = record.getLine().intValue();
            this.type = Type.valueOf(record.getType().getLiteral().toUpperCase());
            this.receiverUri = record.getReceiverUri();
        }
        
        public final int line;
        public final Type type;
        public final String receiverUri;
        
        @Override
        public String toString() {
            return "{line: " + line + ", type: " + type + ", uri: " + receiverUri + "}";
        }

        @Override
        public int hashCode() {
            return HashCommon.mix(line + type.ordinal() + receiverUri.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            ReceiverRecord other = (ReceiverRecord)obj;
            if (line != other.line) return false;
            if (type != other.type) return false;
            if (! receiverUri.equals(other.receiverUri)) return false;
            return true;
        }
        
    }

    /** For each node, the associated metadata. */
    public final Long2ObjectOpenHashMap<NodeMetadata> gid2NodeMetadata;

    public GraphMetadata(Long2ObjectOpenHashMap<NodeMetadata> gid2NodeData) {
        this.gid2NodeMetadata = gid2NodeData;
    }
}
