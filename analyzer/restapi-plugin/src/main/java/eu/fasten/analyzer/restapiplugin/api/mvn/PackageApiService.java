package eu.fasten.analyzer.restapiplugin.api.mvn;

import org.springframework.http.ResponseEntity;

public interface PackageApiService {

    ResponseEntity<String> getPackageVersions(String package_name,
                                              short offset,
                                              short limit);

    ResponseEntity<String> getPackageVersion(String package_name,
                                             String package_version,
                                             short offset,
                                             short limit);

    ResponseEntity<String> getPackageMetadata(String package_name,
                                              String package_version,
                                              short offset,
                                              short limit);

    ResponseEntity<String> getPackageCallgraph(String package_name,
                                               String package_version,
                                               short offset,
                                               short limit);
}
