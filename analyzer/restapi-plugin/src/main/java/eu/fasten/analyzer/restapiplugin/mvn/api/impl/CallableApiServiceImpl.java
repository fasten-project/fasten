package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.api.CallableApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class CallableApiServiceImpl implements CallableApiService {

    @Override
    public ResponseEntity<String> getPackageCallables(String package_name,
                                                      String package_version,
                                                      short offset,
                                                      short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageCallables(
                package_name, package_version, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getCallableMetadata(String package_name,
                                                      String package_version,
                                                      String fasten_uri,
                                                      short offset,
                                                      short limit) {
        String result = KnowledgeBaseConnector.kbDao.getCallableMetadata(
                package_name, package_version, fasten_uri, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
