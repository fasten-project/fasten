package eu.fasten.analyzer.restapiplugin.api.mvn;

import javax.ws.rs.core.Response;

public interface ModuleApiService {

    Response getPackageModules(String package_name,
                               String package_version,
                               short offset,
                               short limit);

    Response getModuleMetadata(String package_name,
                               String package_version,
                               String module_namespace,
                               short offset,
                               short limit);

    Response getModuleFiles(String package_name,
                            String package_version,
                            String module_namespace,
                            short offset,
                            short limit);
}
