package eu.fasten.core.maven;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.maven.data.Dependency;
import org.jooq.DSLContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DependencyGraphBuilder {

    public Map<Dependency, List<Dependency>> buildMavenDependencyGraph(DSLContext dbContext) {
        var result = dbContext
                .select(Packages.PACKAGES.PACKAGE_NAME, PackageVersions.PACKAGE_VERSIONS.VERSION)
                .from(Packages.PACKAGES)
                .join(PackageVersions.PACKAGE_VERSIONS)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .fetch();
        if (result == null || result.isEmpty()) {
            return new HashMap<>();
        }
        var artifacts = result.map(r -> new Dependency(
                r.component1() + Constants.mvnCoordinateSeparator + r.component2()
        ));
        var mavenResolver = new MavenResolver();
        var graph = new HashMap<Dependency, List<Dependency>>(artifacts.size());
        for (var artifact : artifacts) {
            var dependencies = mavenResolver.getArtifactDependenciesFromDatabase(
                    artifact.groupId, artifact.artifactId, artifact.getVersion(), dbContext);
            graph.put(artifact, dependencies);
        }
        return graph;
    }
}
