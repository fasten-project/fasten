package eu.fasten.analyzer.restapiplugin.api.mvn;

import javax.ws.rs.core.Response;

public interface ModuleApiService {

    Response getPackageModules(String package_name, String package_version);
    Response getModuleMetadata(String package_name, String package_version, String module_namespace);
    Response getModuleFiles(String package_name, String package_version, String module_namespace);
}
