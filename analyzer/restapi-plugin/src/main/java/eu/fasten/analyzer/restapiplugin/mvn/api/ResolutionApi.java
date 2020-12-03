package eu.fasten.analyzer.restapiplugin.mvn.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResolutionApi {

    @Autowired
    ResolutionApiService service;

    @GetMapping(value = "/{pkg}/{pkg_ver}/resolve/dependencies", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveDependencies(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @RequestParam(required = false, defaultValue = "true") boolean transitive,
                                               @RequestParam(required = false, defaultValue = "-1") long timestamp) {
        return service.resolveDependencies(package_name, package_version, transitive, timestamp);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/resolve/dependents", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> resolveDependents(@PathVariable("pkg") String package_name,
                                             @PathVariable("pkg_ver") String package_version,
                                             @RequestParam(required = false, defaultValue = "true") boolean transitive,
                                             @RequestParam(required = false, defaultValue = "-1") long timestamp) {
        return service.resolveDependents(package_name, package_version, transitive, timestamp);
    }
}
