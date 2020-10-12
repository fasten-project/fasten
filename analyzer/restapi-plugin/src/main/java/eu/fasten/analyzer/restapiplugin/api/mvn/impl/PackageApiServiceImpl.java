package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.mvn.PackageApiService;

import javax.ws.rs.core.Response;

public class PackageApiServiceImpl implements PackageApiService {

    @Override
    public Response getPackageVersions(String package_name) {

        // TODO Implement
        String result = "Package " + package_name + " versions: ...";
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getPackage(String package_name, String package_version) {

        // TODO Implement
        String result = "Package " + package_name + " (version " + package_version + "): ...";
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getPackageMetadata(String package_name, String package_version) {

        // TODO Implement
        String result = "Package " + package_name + " (version " + package_version + ") metadata: ...";
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getPackageCallgraph(String package_name, String package_version) {

        // TODO Implement
        String result = "Package " + package_name + " (version " + package_version + ") callgraph: ...";
        return Response.status(200).entity(result).build();
    }
}
