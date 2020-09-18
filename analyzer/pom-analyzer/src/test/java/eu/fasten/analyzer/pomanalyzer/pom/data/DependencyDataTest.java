package eu.fasten.analyzer.pomanalyzer.pom.data;

import eu.fasten.core.maven.data.Dependency;
import eu.fasten.core.maven.data.DependencyData;
import eu.fasten.core.maven.data.DependencyManagement;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;

public class DependencyDataTest {

    @Test
    public void dependencyTest() {
        var dependenciesForDependencyManagement = List.of(
                new Dependency("junit", "junit", "4.11"),
                new Dependency("org.json", "json", "20180813")
        );
        var dependencyManagement = new DependencyManagement(dependenciesForDependencyManagement);
        var dependencies = List.of(
                new Dependency("org.jooq", "jooq", "3.12.3")
        );
        var expected = new DependencyData(dependencyManagement, dependencies);
        var json = expected.toJSON();
        var actual = DependencyData.fromJSON(json);
        Assertions.assertEquals(expected, actual);
    }
}
