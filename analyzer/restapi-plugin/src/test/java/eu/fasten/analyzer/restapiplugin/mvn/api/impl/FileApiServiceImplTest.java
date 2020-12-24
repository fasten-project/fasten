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

public class FileApiServiceImplTest {

    private FileApiServiceImpl service;
    private MetadataDao kbDao;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = new FileApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
    }

    @Test
    void getPackageFilesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var response = "files";
        Mockito.when(kbDao.getPackageFiles(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageFiles(packageName, version, offset, limit);
        assertEquals(expected, result);
    }

}
