package eu.fasten.analyzer.restapiplugin.mvn.api;

import org.springframework.http.ResponseEntity;

public interface ResolutionApiService {

    ResponseEntity<String> resolveDependencies(String package_name, String version, boolean transitive, long timestamp);

    ResponseEntity<String> resolveDependents(String package_name, String version, boolean transitive, long timestamp);

}
