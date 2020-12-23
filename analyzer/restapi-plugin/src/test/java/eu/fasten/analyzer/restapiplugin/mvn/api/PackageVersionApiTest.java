package eu.fasten.analyzer.restapiplugin.mvn.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PackageVersionApiTest {

    private PackageVersionApiService service;
    private PackageVersionApi api;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(PackageVersionApiService.class);
        api = new PackageVersionApi(service);
    }

    @Test
    public void getERCGLinkTest() {
        var id = 42L;
        var response = new ResponseEntity<>("ercg link", HttpStatus.OK);
        Mockito.when(service.getERCGLink(id)).thenReturn(response);
        var result = api.getERCGLink(id);
        assertEquals(response, result);
        Mockito.verify(service).getERCGLink(id);
    }
}
