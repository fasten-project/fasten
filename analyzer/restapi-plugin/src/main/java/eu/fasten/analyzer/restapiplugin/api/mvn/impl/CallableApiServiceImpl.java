package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.RestAPIPlugin;
import eu.fasten.analyzer.restapiplugin.api.mvn.CallableApiService;

import javax.ws.rs.core.Response;

public class CallableApiServiceImpl implements CallableApiService {

    @Override
    public Response getPackageCallables(String package_name,
                                        String package_version,
                                        short offset,
                                        short limit) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getPackageCallables(
                package_name, package_version, offset, limit);
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getCallableMetadata(String package_name,
                                        String package_version,
                                        String fasten_uri,
                                        short offset,
                                        short limit) {
        String result = RestAPIPlugin.RestAPIExtension.kbDao.getCallableMetadata(
                package_name, package_version, fasten_uri, offset, limit);
        return Response.status(200).entity(result).build();
    }
}
