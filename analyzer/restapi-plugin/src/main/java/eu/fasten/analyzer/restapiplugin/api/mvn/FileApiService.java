package eu.fasten.analyzer.restapiplugin.api.mvn;

import org.springframework.http.ResponseEntity;

public interface FileApiService {

    ResponseEntity<String> getPackageFiles(String package_name,
                                           String package_version,
                                           short offset,
                                           short limit);
}
