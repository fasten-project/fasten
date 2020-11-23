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
public class PackageApi {

    @Autowired
    PackageApiService service;

    @GetMapping(value = "/{pkg}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageLastVersion(@PathVariable("pkg") String package_name) {
        return service.getPackageLastVersion(package_name);
    }

    @GetMapping(value = "/{pkg}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageVersions(@PathVariable("pkg") String package_name,
                                              @RequestParam(required = false, defaultValue = "0") short offset,
                                              @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getPackageVersions(package_name, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageVersion(@PathVariable("pkg") String package_name,
                                             @PathVariable("pkg_ver") String package_version,
                                             @RequestParam(required = false, defaultValue = "0") short offset,
                                             @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getPackageVersion(package_name, package_version, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageMetadata(@PathVariable("pkg") String package_name,
                                              @PathVariable("pkg_ver") String package_version,
                                              @RequestParam(required = false, defaultValue = "0") short offset,
                                              @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getPackageMetadata(package_name, package_version, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/callgraph", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageCallgraph(@PathVariable("pkg") String package_name,
                                               @PathVariable("pkg_ver") String package_version,
                                               @RequestParam(required = false, defaultValue = "0") short offset,
                                               @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getPackageCallgraph(package_name, package_version, offset, limit);
    }
}

