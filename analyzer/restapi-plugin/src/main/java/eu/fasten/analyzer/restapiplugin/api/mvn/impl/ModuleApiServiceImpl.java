package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.api.mvn.ModuleApiService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class ModuleApiServiceImpl implements ModuleApiService {

    @Override
    public ResponseEntity<String> getPackageModules(String package_name,
                                                    String package_version,
                                                    short offset,
                                                    short limit) {
        String result = KnowledgeBaseConnector.kbDao.getPackageModules(
                package_name, package_version, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getModuleMetadata(String package_name,
                                                    String package_version,
                                                    String module_namespace,
                                                    short offset,
                                                    short limit) {
        String result = KnowledgeBaseConnector.kbDao.getModuleMetadata(
                package_name, package_version, module_namespace, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> getModuleFiles(String package_name,
                                                 String package_version,
                                                 String module_namespace,
                                                 short offset,
                                                 short limit) {
        String result = KnowledgeBaseConnector.kbDao.getModuleFiles(
                package_name, package_version, module_namespace, offset, limit);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
