package eu.fasten.core.maven;

import eu.fasten.core.maven.data.Revision;
import java.util.Set;
import org.jooq.DSLContext;

public class DependencyGraphResolution extends GraphMavenResolver implements ResolutionStrategy {


    private final DSLContext dslContext;

    public DependencyGraphResolution(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Override
    public Set<Revision> resolve(Revision revision, boolean withTransitivity) {
        return super.resolveDependencies(revision, this.dslContext, withTransitivity);
    }
}
