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
public class ModuleApi {

    @Autowired
    ModuleApiService service;

    @GetMapping(value = "/{pkg}/{pkg_ver}/modules", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageModules(@PathVariable("pkg") String package_name,
                                             @PathVariable("pkg_ver") String package_version,
                                             @RequestParam(required = false, defaultValue = "0") short offset,
                                             @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getPackageModules(package_name, package_version, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/modules/{namespace}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getModuleMetadata(@PathVariable("pkg") String package_name,
                                             @PathVariable("pkg_ver") String package_version,
                                             @PathVariable("namespace") String module_namespace,
                                             @RequestParam(required = false, defaultValue = "0") short offset,
                                             @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getModuleMetadata(package_name, package_version, module_namespace, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/modules/{namespace}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getModuleFiles(@PathVariable("pkg") String package_name,
                                          @PathVariable("pkg_ver") String package_version,
                                          @PathVariable("namespace") String module_namespace,
                                          @RequestParam(required = false, defaultValue = "0") short offset,
                                          @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getModuleFiles(package_name, package_version, module_namespace, offset, limit);
    }
}
