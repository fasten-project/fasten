package eu.fasten.analyzer.restapiplugin.api.mvn;

import javax.ws.rs.core.Response;

public interface BinaryModuleApiService {

    Response getPackageBinaryModules(String package_name,
                                     String package_version,
                                     short offset,
                                     short limit);

    Response getBinaryModuleMetadata(String package_name,
                                     String package_version,
                                     String binary_module,
                                     short offset,
                                     short limit);

    Response getBinaryModuleFiles(String package_name,
                                  String package_version,
                                  String binary_module,
                                  short offset,
                                  short limit);
}
