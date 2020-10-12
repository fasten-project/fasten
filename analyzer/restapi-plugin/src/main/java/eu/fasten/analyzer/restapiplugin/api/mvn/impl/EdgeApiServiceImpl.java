package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.mvn.EdgeApiService;

import javax.ws.rs.core.Response;

public class EdgeApiServiceImpl implements EdgeApiService {

    @Override
    public Response getPackageEdges(String package_name, String package_version) {

        // TODO Implement
        String result = "Package " + package_name + " (version " + package_version + ") edges: ...";
        return Response.status(200).entity(result).build();
    }
}
