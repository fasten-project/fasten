package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.RestApplication;
import eu.fasten.analyzer.restapiplugin.api.mvn.impl.CallableApiServiceImpl;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/mvn/packages")
@Produces(MediaType.TEXT_PLAIN)
public class CallableApi {

    CallableApiService service = new CallableApiServiceImpl();

    @GET
    @Path("/{pkg}/{pkg_ver}/callables")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageCallables(@PathParam("pkg") String package_name,
                                        @PathParam("pkg_ver") String package_version,
                                        @DefaultValue("0")
                                        @QueryParam("offset") short offset,
                                        @DefaultValue(RestApplication.DEFAULT_PAGE_SIZE)
                                        @QueryParam("limit") short limit) {
        return service.getPackageCallables(package_name, package_version, offset, limit);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/{fasten_uri}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCallableMetadata(@PathParam("pkg") String package_name,
                                        @PathParam("pkg_ver") String package_version,
                                        @PathParam("fasten_uri") String fasten_uri,
                                        @DefaultValue("0")
                                        @QueryParam("offset") short offset,
                                        @DefaultValue(RestApplication.DEFAULT_PAGE_SIZE)
                                        @QueryParam("limit") short limit) {
        return service.getCallableMetadata(package_name, package_version, fasten_uri, offset, limit);
    }
}
