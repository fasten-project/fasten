package eu.fasten.core.index;

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

import org.apache.commons.lang3.reflect.FieldUtils;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.serializers.FieldSerializer;

import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.webgraph.BVGraph;

/** A {@link Kryo} serializer for {@link BVGraph}. */
public class BVGraphSerializer extends FieldSerializer<BVGraph> {

	public BVGraphSerializer(final Kryo kryo) {
		super(kryo, BVGraph.class);
	}

	@Override
	public BVGraph read(final Kryo kryo, final Input input, final Class<? extends BVGraph> type) {
		final BVGraph read = super.read(kryo, input, type);
		try {
			FieldUtils.writeField(read, "outdegreeIbs",
					new InputBitStream((byte[])FieldUtils.readField(read, "graphMemory", true)), true);
		} catch (final IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		return read;
	}
}