package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.api.mvn.FileApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class FileApiServiceImpl implements FileApiService {

    @Override
    public ResponseEntity<String> getPackageFiles(String package_name,
                                                  String package_version,
                                                  short offset,
                                                  short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageFiles(
                package_name, package_version, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
