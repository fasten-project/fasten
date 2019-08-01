package eu.fasten.analyzer;


import eu.fasten.analyzer.data.callgraph.WalaCallGraph;
import eu.fasten.analyzer.data.type.MavenResolvedCoordinate;
import eu.fasten.analyzer.generator.WalaCallgraphConstructor;
import eu.fasten.analyzer.generator.WalaUFIAdapter;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class CallGraphGenerator {

    private static Logger logger = LoggerFactory.getLogger(CallGraphGenerator.class);


    private static List<MavenResolvedCoordinate> buildClasspath(String mavenCoordinate){
        logger.info("Building analyzedClasspath of {}", mavenCoordinate);
        MavenResolvedArtifact[] artifacts = Maven.resolver().resolve(mavenCoordinate).withTransitivity().asResolvedArtifact();
        List<MavenResolvedArtifact> arts = Arrays.asList(artifacts);
        List<MavenResolvedCoordinate> path = arts.stream().map(MavenResolvedCoordinate::of).collect(Collectors.toList());
        System.out.println(path);
        logger.info("The analyzedClasspath of {} is {} ", mavenCoordinate, path);
        return path;
    }


    public static WalaUFIAdapter generateCallGraph(String coordinate){

        WalaUFIAdapter wrapped_cg = null;
        try {
            List<MavenResolvedCoordinate> path = buildClasspath(coordinate);
            logger.info("Building generator using generator....");
            WalaCallGraph cg = WalaCallgraphConstructor.build(path);
            wrapped_cg = WalaUFIAdapter.wrap(cg);
            logger.info("Call graph construction done!");
        } catch (Exception e) {
            logger.error("An exception occurred for {}", coordinate, e);
        }
        return wrapped_cg;
    }
}

