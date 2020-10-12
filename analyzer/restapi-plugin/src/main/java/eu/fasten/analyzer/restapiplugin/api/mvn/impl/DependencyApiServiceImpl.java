package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.mvn.DependencyApiService;

import javax.ws.rs.core.Response;

public class DependencyApiServiceImpl implements DependencyApiService {

    @Override
    public Response getPackageDependencies(String package_name, String package_version) {

        // TODO Implement
        String result = "Package " + package_name + " (version " + package_version + ") dependencies: ...";
        return Response.status(200).entity(result).build();
    }
}
