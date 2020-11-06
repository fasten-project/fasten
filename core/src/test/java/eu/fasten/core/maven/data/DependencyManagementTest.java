package eu.fasten.core.maven.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import java.util.List;

public class DependencyManagementTest {

    @Test
    public void dependencyTest() {
        var dependencies = List.of(
                new Dependency("junit", "junit", "4.11"),
                new Dependency("org.json", "json", "20180813")
        );
        var expected = new DependencyManagement(dependencies);
        var json = expected.toJSON();
        var actual = DependencyManagement.fromJSON(json);
        Assertions.assertEquals(expected, actual);
    }
}
