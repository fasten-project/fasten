package eu.fasten.analyzer.restapiplugin.mvn.api;

import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EdgeApiTest {

    private EdgeApiService service;
    private EdgeApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(EdgeApiService.class);
        api = new EdgeApi(service);
    }

    @Test
    public void getPackageEdgesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package version dependencies", HttpStatus.OK);
        Mockito.when(service.getPackageEdges(packageName, version, offset, limit)).thenReturn(response);
        var result = api.getPackageEdges(packageName, version, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getPackageEdges(packageName, version, offset, limit);
    }
}
