package eu.fasten.analyzer.restapiplugin.mvn.api;

import eu.fasten.analyzer.restapiplugin.mvn.RestApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CallableApi {

    @Autowired
    CallableApiService service;

    @GetMapping(value = "/{pkg}/{pkg_ver}/callables", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageCallables(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @RequestParam(required = false, defaultValue = "0") short offset,
                                               @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getPackageCallables(package_name, package_version, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/{fasten_uri}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getCallableMetadata(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @PathVariable("fasten_uri") String fasten_uri,
                                               @RequestParam(required = false, defaultValue = "0") short offset,
                                               @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getCallableMetadata(package_name, package_version, fasten_uri, offset, limit);
    }
}
