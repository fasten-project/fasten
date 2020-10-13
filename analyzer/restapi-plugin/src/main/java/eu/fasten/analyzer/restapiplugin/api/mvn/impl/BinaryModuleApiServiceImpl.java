package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.RestAPIPlugin;
import eu.fasten.analyzer.restapiplugin.api.mvn.BinaryModuleApiService;

import javax.ws.rs.core.Response;

public class BinaryModuleApiServiceImpl implements BinaryModuleApiService {

    @Override
    public Response getPackageBinaryModules(String package_name, String package_version) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getPackageBinaryModules(package_name, package_version);
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getBinaryModuleMetadata(String package_name, String package_version, String binary_module) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getBinaryModuleMetadata(
                package_name, package_version, binary_module);
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getBinaryModuleFiles(String package_name, String package_version, String binary_module) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getBinaryModuleFiles(
                package_name, package_version, binary_module);
        return Response.status(200).entity(result).build();
    }
}
