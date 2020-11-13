package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.api.mvn.PackageApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class PackageApiServiceImpl implements PackageApiService {

    @Override
    public ResponseEntity<String> getPackageVersions(String package_name,
                                                     short offset,
                                                     short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageVersions(package_name, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getPackageVersion(String package_name,
                                                    String package_version,
                                                    short offset,
                                                    short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageVersion(
                package_name, package_version, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getPackageMetadata(String package_name,
                                                     String package_version,
                                                     short offset,
                                                     short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageMetadata(
                package_name, package_version, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getPackageCallgraph(String package_name,
                                                      String package_version,
                                                      short offset,
                                                      short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageCallgraph(
                package_name, package_version, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
