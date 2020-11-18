package eu.fasten.analyzer.restapiplugin.mvn.api;

import org.springframework.http.ResponseEntity;

public interface EdgeApiService {

    ResponseEntity<String> getPackageEdges(String package_name,
                                           String package_version,
                                           short offset,
                                           short limit);
}
