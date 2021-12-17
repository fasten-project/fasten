package eu.fasten.core.maven;

import eu.fasten.core.maven.data.Revision;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;

public class ShrinkWrapResolution implements ResolutionStrategy{

    @Override
    public Set<Revision> resolve(Revision revision, boolean withTransitivity) {

        Set<Revision> result = new HashSet<>();
        List<MavenCoordinate> depSet;
        try {
            depSet = Maven.resolver().resolve(revision.toCoordinate()).withTransitivity()
                .asList(org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate.class);
        } catch (Exception e){
            return result;
        }
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
