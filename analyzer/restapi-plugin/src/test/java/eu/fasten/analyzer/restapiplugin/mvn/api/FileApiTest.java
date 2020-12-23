package eu.fasten.analyzer.restapiplugin.mvn.api;

import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileApiTest {

    private FileApiService service;
    private FileApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(FileApiService.class);
        api = new FileApi(service);
    }

    @Test
    public void getPackageFilesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package version files", HttpStatus.OK);
        Mockito.when(service.getPackageFiles(packageName, version, offset, limit)).thenReturn(response);
        var result = api.getPackageFiles(packageName, version, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getPackageFiles(packageName, version, offset, limit);
    }
}
