package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.api.mvn.BinaryModuleApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class BinaryModuleApiServiceImpl implements BinaryModuleApiService {

    @Override
    public ResponseEntity<String> getPackageBinaryModules(String package_name,
                                                          String package_version,
                                                          short offset,
                                                          short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageBinaryModules(
                package_name, package_version, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getBinaryModuleMetadata(String package_name,
                                                          String package_version,
                                                          String binary_module,
                                                          short offset,
                                                          short limit) {
        String result = KnowledgeBaseConnector.kbDao.getBinaryModuleMetadata(
                package_name, package_version, binary_module, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getBinaryModuleFiles(String package_name,
                                                       String package_version,
                                                       String binary_module,
                                                       short offset,
                                                       short limit) {
        String result = KnowledgeBaseConnector.kbDao.getBinaryModuleFiles(
                package_name, package_version, binary_module, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
