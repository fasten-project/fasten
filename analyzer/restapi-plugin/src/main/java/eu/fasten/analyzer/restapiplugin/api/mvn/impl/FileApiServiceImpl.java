package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.mvn.FileApiService;

import javax.ws.rs.core.Response;

public class FileApiServiceImpl implements FileApiService {

    @Override
    public Response getPackageFiles(String package_name, String package_version) {

        // TODO Implement
        String result = "Package " + package_name + " (version " + package_version + ") files: ...";
        return Response.status(200).entity(result).build();
    }
}
