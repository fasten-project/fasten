package eu.fasten.analyzer.restapiplugin.mvn.api.impl;

import eu.fasten.analyzer.restapiplugin.mvn.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.mvn.api.EdgeApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class EdgeApiServiceImpl implements EdgeApiService {

    @Override
    public ResponseEntity<String> getPackageEdges(String package_name,
                                                  String package_version,
                                                  short offset,
                                                  short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageEdges(
                package_name, package_version, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
