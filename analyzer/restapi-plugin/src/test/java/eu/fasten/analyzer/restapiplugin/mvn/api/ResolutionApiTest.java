package eu.fasten.analyzer.restapiplugin.mvn.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResolutionApiTest {

    private ResolutionApiService service;
    private ResolutionApi api;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ResolutionApiService.class);
        api = new ResolutionApi(service);
    }

    @Test
    public void resolveDependenciesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var transitive = true;
        var timestamp = -1L;
        var response = new ResponseEntity<>("dependencies", HttpStatus.OK);
        Mockito.when(service.resolveDependencies(packageName, version, transitive, timestamp)).thenReturn(response);
        var result = api.resolveDependencies(packageName, version, transitive, timestamp);
        assertEquals(response, result);
        Mockito.verify(service).resolveDependencies(packageName, version, transitive, timestamp);
    }

    @Test
    public void resolveDependentsTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var transitive = true;
        var timestamp = -1L;
        var response = new ResponseEntity<>("dependents", HttpStatus.OK);
        Mockito.when(service.resolveDependents(packageName, version, transitive, timestamp)).thenReturn(response);
        var result = api.resolveDependents(packageName, version, transitive, timestamp);
        assertEquals(response, result);
        Mockito.verify(service).resolveDependents(packageName, version, transitive, timestamp);
    }

    @Test
    void enrichArtifactTest() {
        var artifacts = List.of("dep1", "dep2", "dep3");
        var stitch = false;
        var enrichEdges = true;
        var response = new ResponseEntity<>("enriched artifact", HttpStatus.OK);
        Mockito.when(service.enrichArtifacts(artifacts, enrichEdges, stitch)).thenReturn(response);
        var result = api.enrichArtifacts(artifacts, enrichEdges, stitch);
        assertEquals(response, result);
        Mockito.verify(service).enrichArtifacts(artifacts, enrichEdges, stitch);
    }
}
