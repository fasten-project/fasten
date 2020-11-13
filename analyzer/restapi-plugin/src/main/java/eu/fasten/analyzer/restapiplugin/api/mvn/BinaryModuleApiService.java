package eu.fasten.analyzer.restapiplugin.api.mvn;

import org.springframework.http.ResponseEntity;

public interface BinaryModuleApiService {

    ResponseEntity<String> getPackageBinaryModules(String package_name,
                                                   String package_version,
                                                   short offset,
                                                   short limit);

    ResponseEntity<String> getBinaryModuleMetadata(String package_name,
                                                   String package_version,
                                                   String binary_module,
                                                   short offset,
                                                   short limit);

    ResponseEntity<String> getBinaryModuleFiles(String package_name,
                                                String package_version,
                                                String binary_module,
                                                short offset,
                                                short limit);
}
