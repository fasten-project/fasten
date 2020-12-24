package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import eu.fasten.core.data.metadatadb.MetadataDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModuleApiServiceImplTest {

    private ModuleApiServiceImpl service;
    private MetadataDao kbDao;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = new ModuleApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
    }

    @Test
    void getPackageModulesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var response = "modules";
        Mockito.when(kbDao.getPackageModules(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageModules(packageName, version, offset, limit);
        assertEquals(expected, result);
    }

    @Test
    void getModuleMetadataTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var module = "module";
        var response = "module metadata";
        Mockito.when(kbDao.getModuleMetadata(packageName, version, module)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getModuleMetadata(packageName, version, module);
        assertEquals(expected, result);

        Mockito.when(kbDao.getModuleMetadata(packageName, version, module)).thenReturn(null);
        result = service.getModuleMetadata(packageName, version, module);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void getModuleFilesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var module = "module";
        var response = "module files";
        Mockito.when(kbDao.getModuleFiles(packageName, version, module, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getModuleFiles(packageName, version, module, offset, limit);
        assertEquals(expected, result);
    }

    @Test
    void getModuleCallablesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var module = "module";
        var response = "module callables";
        Mockito.when(kbDao.getModuleCallables(packageName, version, module, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getModuleCallables(packageName, version, module, offset, limit);
        assertEquals(expected, result);
    }
}
