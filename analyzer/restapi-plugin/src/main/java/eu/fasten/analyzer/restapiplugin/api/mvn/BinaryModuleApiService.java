package eu.fasten.analyzer.restapiplugin.api.mvn;

import javax.ws.rs.core.Response;

public interface BinaryModuleApiService {

    Response getPackageBinaryModules(String package_name, String package_version);
    Response getBinaryModuleMetadata(String package_name, String package_version, String binary_module);
    Response getBinaryModuleFiles(String package_name, String package_version, String binary_module);
}
