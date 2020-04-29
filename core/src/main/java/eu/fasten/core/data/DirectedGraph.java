package eu.fasten.core.data;

import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface DirectedGraph {
	public int numNodes();
	public long numArcs();
	public LongList successors(final long node);
	public LongList predecessors(final long node);
	public LongList nodes();

	public LongSet externalNodes();
	public boolean isInternal(final long node);
	public boolean isExternal(final long node);
}
