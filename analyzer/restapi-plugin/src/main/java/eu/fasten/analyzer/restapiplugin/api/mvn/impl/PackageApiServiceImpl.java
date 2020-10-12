package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.mvn.PackageApiService;

import javax.ws.rs.core.Response;

public class PackageApiServiceImpl implements PackageApiService {

    @Override
    public Response getPackageVersions(String package_name, String package_version) {

        // TODO Implement
        String result = "Package: " + package_name + ", package version: " + package_version;
        return Response.status(200).entity(result).build();
    }
}
