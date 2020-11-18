package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.api.DependencyApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class DependencyApiServiceImpl implements DependencyApiService {

    @Override
    public ResponseEntity<String> getPackageDependencies(String package_name,
                                                         String package_version,
                                                         short offset,
                                                         short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageDependencies(
                package_name, package_version, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
