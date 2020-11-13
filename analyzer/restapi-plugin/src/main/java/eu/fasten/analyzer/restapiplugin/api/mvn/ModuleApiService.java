package eu.fasten.analyzer.restapiplugin.api.mvn;

import org.springframework.http.ResponseEntity;

public interface ModuleApiService {

    ResponseEntity<String> getPackageModules(String package_name,
                                             String package_version,
                                             short offset,
                                             short limit);

    ResponseEntity<String> getModuleMetadata(String package_name,
                                             String package_version,
                                             String module_namespace,
                                             short offset,
                                             short limit);

    ResponseEntity<String> getModuleFiles(String package_name,
                                          String package_version,
                                          String module_namespace,
                                          short offset,
                                          short limit);
}
