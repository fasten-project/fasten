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
public class BinaryModuleApi {

    @Autowired
    BinaryModuleApiService service;

    @GetMapping(value = "/{pkg}/{pkg_ver}/binary-modules", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getPackageBinaryModules(@PathVariable("pkg") String package_name,
                                                   @PathVariable("pkg_ver") String package_version,
                                                   @RequestParam(required = false, defaultValue = "0") short offset,
                                                   @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getPackageBinaryModules(package_name, package_version, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/binary-modules/{binary}/metadata", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getBinaryModuleMetadata(@PathVariable("pkg") String package_name,
                                                   @PathVariable("pkg_ver") String package_version,
                                                   @PathVariable("binary") String binary_module,
                                                   @RequestParam(required = false, defaultValue = "0") short offset,
                                                   @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getBinaryModuleMetadata(package_name, package_version, binary_module, offset, limit);
    }

    @GetMapping(value = "/{pkg}/{pkg_ver}/binary-modules/{binary}/files", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> getBinaryModuleFiles(@PathVariable("pkg") String package_name,
                                                @PathVariable("pkg_ver") String package_version,
                                                @PathVariable("binary") String binary_module,
                                                @RequestParam(required = false, defaultValue = "0") short offset,
                                                @RequestParam(required = false, defaultValue = RestApplication.DEFAULT_PAGE_SIZE) short limit) {
        return service.getBinaryModuleFiles(package_name, package_version, binary_module, offset, limit);
    }
}
