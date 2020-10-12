package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.mvn.impl.CallableApiServiceImpl;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
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
                                        @PathParam("pkg_ver") String package_version) {
        return service.getPackageCallables(package_name, package_version);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/{fasten_uri}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCallableMetadata(@PathParam("pkg") String package_name,
                                        @PathParam("pkg_ver") String package_version,
                                        @PathParam("fasten_uri") String fasten_uri) {
        return service.getCallableMetadata(package_name, package_version, fasten_uri);
    }
}
