package eu.fasten.analyzer.restapiplugin.mvn.api;

import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class BinaryModuleApiTest {

    private BinaryModuleApiService service;
    private BinaryModuleApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(BinaryModuleApiService.class);
        api = new BinaryModuleApi(service);
    }

    @Test
    public void getPackageBinaryModulesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package binary modules", HttpStatus.OK);
        Mockito.when(service.getPackageBinaryModules(packageName, version, offset, limit)).thenReturn(response);
        var result = api.getPackageBinaryModules(packageName, version, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getPackageBinaryModules(packageName, version, offset, limit);
    }

    @Test
    public void getBinaryModuleMetadataTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var binModule = "binary module";
        var response = new ResponseEntity<>("binary module metadata", HttpStatus.OK);
        Mockito.when(service.getBinaryModuleMetadata(packageName, version, binModule)).thenReturn(response);
        var result = api.getBinaryModuleMetadata(packageName, version, binModule);
        assertEquals(response, result);
        Mockito.verify(service).getBinaryModuleMetadata(packageName, version, binModule);
    }

    @Test
    public void getBinaryModuleFilesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var binModule = "binary module";
        var response = new ResponseEntity<>("binary module files", HttpStatus.OK);
        Mockito.when(service.getBinaryModuleFiles(packageName, version, binModule, offset, limit)).thenReturn(response);
        var result = api.getBinaryModuleFiles(packageName, version, binModule, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getBinaryModuleFiles(packageName, version, binModule, offset, limit);
    }
}
