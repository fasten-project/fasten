package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.RestAPIPlugin;
import eu.fasten.analyzer.restapiplugin.api.mvn.PackageApiService;

import javax.ws.rs.core.Response;

public class PackageApiServiceImpl implements PackageApiService {

    @Override
    public Response getPackageVersions(String package_name,
                                       short offset,
                                       short limit) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getPackageVersions(package_name, offset, limit);
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getPackageVersion(String package_name,
                                      String package_version,
                                      short offset,
                                      short limit) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getPackageVersion(
                package_name, package_version, offset, limit);
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getPackageMetadata(String package_name,
                                       String package_version,
                                       short offset,
                                       short limit) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getPackageMetadata(
                package_name, package_version, offset, limit);
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getPackageCallgraph(String package_name,
                                        String package_version,
                                        short offset,
                                        short limit) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getPackageCallgraph(
                package_name, package_version, offset, limit);
        return Response.status(200).entity(result).build();
    }
}
