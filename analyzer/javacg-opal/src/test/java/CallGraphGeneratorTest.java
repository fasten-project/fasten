import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertNotNull;

class CallGraphGeneratorTest {

    @Test
    void generatePartialCallGraph() {

        assertNotNull(CallGraphGenerator.generatePartialCallGraph(MavenResolver.downloadArtifact("com.google.guava:guava:jar:28.1-jre")));
    }

}