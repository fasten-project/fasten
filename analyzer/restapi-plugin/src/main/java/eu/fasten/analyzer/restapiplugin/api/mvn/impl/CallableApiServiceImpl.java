package eu.fasten.analyzer.restapiplugin.api.mvn.impl;

import eu.fasten.analyzer.restapiplugin.api.mvn.CallableApiService;

import javax.ws.rs.core.Response;

public class CallableApiServiceImpl implements CallableApiService {

    @Override
    public Response getPackageCallables(String package_name, String package_version) {

        // TODO Implement
        String result = "Package " + package_name + " (version " + package_version + ") callables: ...";
        return Response.status(200).entity(result).build();
    }

    @Override
    public Response getCallableMetadata(String package_name, String package_version, String fasten_uri) {

        // TODO Implement
        String result = "Callable " + fasten_uri + " metadata: ...";
        return Response.status(200).entity(result).build();
    }
}
