package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.RestAPIPlugin;
import eu.fasten.analyzer.restapiplugin.api.mvn.FileApiService;

import javax.ws.rs.core.Response;

public class FileApiServiceImpl implements FileApiService {

    @Override
    public Response getPackageFiles(String package_name, String package_version) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getPackageFiles(package_name, package_version);
        return Response.status(200).entity(result).build();
    }
}
