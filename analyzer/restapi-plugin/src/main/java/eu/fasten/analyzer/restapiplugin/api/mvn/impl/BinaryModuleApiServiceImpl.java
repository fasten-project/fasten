package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.mvn.BinaryModuleApiService;

import javax.ws.rs.core.Response;

public class BinaryModuleApiServiceImpl implements BinaryModuleApiService {

    @Override
    public Response getPackageBinaryModules(String package_name, String package_version) {

        // TODO Implement
        String result = "Package " + package_name + " (version " + package_version + ") binary modules: ...";
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getBinaryModuleMetadata(String package_name, String package_version, String binary_module) {

        // TODO Implement
        String result = "Binary module " + binary_module + " metadata: ...";
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getBinaryModuleFiles(String package_name, String package_version, String binary_module) {

        // TODO Implement
        String result = "Binary module " + binary_module + " files: ...";
        return Response.status(200).entity(result).build();
    }
}
