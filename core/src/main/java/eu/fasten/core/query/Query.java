package eu.fasten.core.query;

import java.util.Collection;

import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.KnowledgeBase;

public interface Query {
	public Collection<FastenURI> execute(final KnowledgeBase kb);
}

