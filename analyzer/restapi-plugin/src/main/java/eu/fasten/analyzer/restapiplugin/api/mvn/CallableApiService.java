package eu.fasten.analyzer.restapiplugin.api.mvn;

import org.springframework.http.ResponseEntity;

public interface CallableApiService {

    ResponseEntity<String> getPackageCallables(String package_name,
                                               String package_version,
                                               short offset,
                                               short limit);

    ResponseEntity<String> getCallableMetadata(String package_name,
                                               String package_version,
                                               String fasten_uri,
                                               short offset,
                                               short limit);
}
