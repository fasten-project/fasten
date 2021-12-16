package eu.fasten.core.maven;

import eu.fasten.core.maven.data.Revision;
import java.util.HashSet;
import java.util.Set;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jooq.DSLContext;

public interface ResolutionStrategy {
    public Set<Revision> resolve(Revision revision, boolean withTransitivity);
}



