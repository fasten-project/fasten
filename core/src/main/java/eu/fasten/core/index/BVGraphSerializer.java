package eu.fasten.core.index;

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