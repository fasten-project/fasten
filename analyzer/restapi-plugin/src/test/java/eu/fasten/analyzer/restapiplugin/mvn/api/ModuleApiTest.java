package eu.fasten.analyzer.restapiplugin.mvn.api;

import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModuleApiTest {

    private ModuleApiService service;
    private ModuleApi api;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ModuleApiService.class);
        api = new ModuleApi(service);
    }

    @Test
    public void getPackageModulesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var response = new ResponseEntity<>("package version modules", HttpStatus.OK);
        Mockito.when(service.getPackageModules(packageName, version, offset, limit)).thenReturn(response);
        var result = api.getPackageModules(packageName, version, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getPackageModules(packageName, version, offset, limit);
    }

    @Test
    public void getModuleMetadataTEst() {
        var packageName = "pkg name";
        var version = "pkg version";
        var module = "module namespace";
        var response = new ResponseEntity<>("module metadata", HttpStatus.OK);
        Mockito.when(service.getModuleMetadata(packageName, version, module)).thenReturn(response);
        var result = api.getModuleMetadata(packageName, version, module);
        assertEquals(response, result);
        Mockito.verify(service).getModuleMetadata(packageName, version, module);
    }

    @Test
    public void getModuleFilesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var module = "module namespace";
        var response = new ResponseEntity<>("module files", HttpStatus.OK);
        Mockito.when(service.getModuleFiles(packageName, version, module, offset, limit)).thenReturn(response);
        var result = api.getModuleFiles(packageName, version, module, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getModuleFiles(packageName, version, module, offset, limit);
    }

    @Test
    public void getModuleCallablesTest() {
        var packageName = "pkg name";
        var version = "pkg version";
        var module = "module namespace";
        var response = new ResponseEntity<>("module callables", HttpStatus.OK);
        Mockito.when(service.getModuleCallables(packageName, version, module, offset, limit)).thenReturn(response);
        var result = api.getModuleCallables(packageName, version, module, offset, limit);
        assertEquals(response, result);
        Mockito.verify(service).getModuleCallables(packageName, version, module, offset, limit);
    }
}
