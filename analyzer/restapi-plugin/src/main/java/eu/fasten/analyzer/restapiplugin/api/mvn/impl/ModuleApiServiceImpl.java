package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.mvn.ModuleApiService;

import javax.ws.rs.core.Response;

public class ModuleApiServiceImpl implements ModuleApiService {

    @Override
    public Response getPackageModules(String package_name, String package_version) {

        // TODO Implement
        String result = "Package " + package_name + " (version " + package_version + ") modules: ...";
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getModuleMetadata(String package_name, String package_version, String module_namespace) {

        // TODO Implement
        String result = "Module " + module_namespace + " metadata: ...";
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getModuleFiles(String package_name, String package_version, String module_namespace) {

        // TODO Implement
        String result = "Module " + module_namespace + " files: ...";
        return Response.status(200).entity(result).build();
    }
}
