package eu.fasten.analyzer.restapiplugin.mvn.api;

import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CallableApiTest {

    private CallableApiService service;
    private CallableApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(CallableApiService.class);
        api = new CallableApi(service);
    }

    @Test
    public void getPackageCallablesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package binary callables", HttpStatus.OK);
        Mockito.when(service.getPackageCallables(packageName, version, offset, limit)).thenReturn(response);
        var result = api.getPackageCallables(packageName, version, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getPackageCallables(packageName, version, offset, limit);
    }

    @Test
    public void getCallableMetadataTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var callable = "callable";
        var response = new ResponseEntity<>("callable metadata", HttpStatus.OK);
        Mockito.when(service.getCallableMetadata(packageName, version, callable)).thenReturn(response);
        var result = api.getCallableMetadata(packageName, version, callable);
        assertEquals(response, result);
        Mockito.verify(service).getCallableMetadata(packageName, version, callable);
    }

    @Test
    public void getCallablesTest() {
        var ids = List.of(1L, 2L, 3L);
        var response = new ResponseEntity<>("callables metadata map", HttpStatus.OK);
        Mockito.when(service.getCallables(ids)).thenReturn(response);
        var result = api.getCallables(ids);
        assertEquals(response, result);
        Mockito.verify(service).getCallables(ids);
    }
}
