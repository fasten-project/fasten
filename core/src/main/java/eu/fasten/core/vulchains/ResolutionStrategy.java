package eu.fasten.core.vulchains;

import eu.fasten.core.maven.data.Revision;
import java.util.HashSet;
import java.util.Set;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

public interface ResolutionStrategy {
    public Set<Revision> resolve(Revision revision, boolean withTransitivity);
}

class ShrinkWrapResolution implements ResolutionStrategy{

    @Override
    public Set<Revision> resolve(Revision revision, boolean withTransitivity) {

        Set<Revision> result = new HashSet<>();
        var depSet = Maven.resolver().resolve(revision.toCoordinate()).withTransitivity()
            .asList(org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate.class);
        if (depSet != null && !depSet.isEmpty()) {

            for (MavenCoordinate mavenCoordinate : depSet) {
                 result.add(new Revision(mavenCoordinate.getGroupId(),
                    mavenCoordinate.getArtifactId(),
                    mavenCoordinate.getVersion()));
            }

        }
        return result;
    }
}

class DependencyGraphResolution implements ResolutionStrategy{

    @Override
    public Set<Revision> resolve(Revision revision, boolean withTransitivity) {
        return null;
    }
}

