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

public class BinaryModuleApiServiceImplTest {

    private BinaryModuleApiServiceImpl service;
    private MetadataDao kbDao;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = new BinaryModuleApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
    }

    @Test
    void getPackageBinaryModulesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var response = "modules";
        Mockito.when(kbDao.getPackageBinaryModules(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageBinaryModules(packageName, version, offset, limit);
        assertEquals(expected, result);
    }

    @Test
    void getBinaryModuleMetadataTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var module = "bin module";
        var response = "module metadata";
        Mockito.when(kbDao.getBinaryModuleMetadata(packageName, version, module)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getBinaryModuleMetadata(packageName, version, module);
        assertEquals(expected, result);

        Mockito.when(kbDao.getBinaryModuleMetadata(packageName, version, module)).thenReturn(null);
        result = service.getBinaryModuleMetadata(packageName, version, module);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void getBinaryModuleFilesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var module = "bin module";
        var response = "module files";
        Mockito.when(kbDao.getBinaryModuleFiles(packageName, version, module, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getBinaryModuleFiles(packageName, version, module, offset, limit);
        assertEquals(expected, result);
    }
}
