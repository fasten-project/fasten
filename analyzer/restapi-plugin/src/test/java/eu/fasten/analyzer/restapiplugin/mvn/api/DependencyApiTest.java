package eu.fasten.analyzer.restapiplugin.mvn.api;

import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DependencyApiTest {

    private DependencyApiService service;
    private DependencyApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(DependencyApiService.class);
        api = new DependencyApi(service);
    }

    @Test
    public void getPackageDependenciesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package version dependencies", HttpStatus.OK);
        Mockito.when(service.getPackageDependencies(packageName, version, offset, limit)).thenReturn(response);
        var result = api.getPackageDependencies(packageName, version, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getPackageDependencies(packageName, version, offset, limit);
    }
}
