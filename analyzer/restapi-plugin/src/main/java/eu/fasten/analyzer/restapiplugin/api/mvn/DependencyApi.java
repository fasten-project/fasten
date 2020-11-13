package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.RestApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DependencyApi {

    @Autowired
    DependencyApiService service;

    @GetMapping(value = "/{pkg}/{pkg_ver}/deps", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageDependencies(@PathVariable("pkg") String package_name,
                                                  @PathVariable("pkg_ver") String package_version,
                                                  @RequestParam(required = false, defaultValue = "0") short offset,
                                                  @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getPackageDependencies(package_name, package_version, offset, limit);
    }
}
