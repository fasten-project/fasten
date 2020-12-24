package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import eu.fasten.core.data.metadatadb.MetadataDao;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CallableApiServiceImplTest {

    private CallableApiServiceImpl service;
    private MetadataDao kbDao;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = new CallableApiServiceImpl();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
    }

    @Test
    void getPackageCallablesTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var response = "callables";
        Mockito.when(kbDao.getPackageCallables(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageCallables(packageName, version, offset, limit);
        assertEquals(expected, result);
    }

    @Test
    void getCallableMetadataTest() {
        var packageName = "pkg";
        var version = "pkg ver";
        var callable = "callable uri";
        var response = "callable metadata";
        Mockito.when(kbDao.getCallableMetadata(packageName, version, callable)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getCallableMetadata(packageName, version, callable);
        assertEquals(expected, result);

        Mockito.when(kbDao.getCallableMetadata(packageName, version, callable)).thenReturn(null);
        result = service.getCallableMetadata(packageName, version, callable);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void getCallablesTest() {
        var ids = List.of(1L, 2L, 3L);
        var map = new HashMap<Long, JSONObject>(3);
        map.put(1L, new JSONObject("{\"foo\":\"bar\"}"));
        map.put(2L, new JSONObject("{\"hello\":\"world\"}"));
        map.put(3L, new JSONObject("{\"baz\":42}"));
        Mockito.when(kbDao.getCallables(ids)).thenReturn(map);
        var json = new JSONObject();
        for (var id : ids) {
            json.put(String.valueOf(id), map.get(id));
        }
        var expected = new ResponseEntity<>(json.toString(), HttpStatus.OK);
        var result = service.getCallables(ids);
        assertEquals(expected, result);
    }
}
