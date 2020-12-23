package eu.fasten.analyzer.restapiplugin.mvn.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StitchingApiTest {

    private StitchingApiService service;
    private StitchingApi api;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(StitchingApiService.class);
        api = new StitchingApi(service);
    }

    @Test
    void resolveCallablesTest() {
        var gids = List.of(1L, 2L, 3L);
        var response = new ResponseEntity<>("callable uri map", HttpStatus.OK);
        Mockito.when(service.resolveCallablesToUris(gids)).thenReturn(response);
        var result = api.resolveCallables(gids);
        assertEquals(response, result);
        Mockito.verify(service).resolveCallablesToUris(gids);
    }

    @Test
    void getCallablesMetadataTest() {
        var uris = List.of("uri1", "uri2", "uri3");
        var response = new ResponseEntity<>("callables metadata map", HttpStatus.OK);
        Mockito.when(service.getCallablesMetadata(uris, true, null)).thenReturn(response);
        var result = api.getCallablesMetadata(uris, true, null);
        assertEquals(response, result);
        Mockito.verify(service).getCallablesMetadata(uris, true, null);
    }

    @Test
    void resolveMultipleDependenciesTest() {
        var deps = List.of("dep1", "dep2", "dep3");
        var response = new ResponseEntity<>("transitive depset", HttpStatus.OK);
        Mockito.when(service.resolveMultipleDependencies(deps)).thenReturn(response);
        var result = api.resolveMultipleDependencies(deps);
        assertEquals(response, result);
        Mockito.verify(service).resolveMultipleDependencies(deps);
    }

    @Test
    void getDirectedGraphTest() {
        var id = 42L;
        var stitch = true;
        var timestamp = -1L;
        var response = new ResponseEntity<>("directed graph", HttpStatus.OK);
        Mockito.when(service.getDirectedGraph(id, stitch, timestamp)).thenReturn(response);
        var result = api.getDirectedGraph(id, stitch, timestamp);
        assertEquals(response, result);
        Mockito.verify(service).getDirectedGraph(id, stitch, timestamp);
    }
}
