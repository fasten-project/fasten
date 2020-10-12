package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.mvn.impl.DependencyApiServiceImpl;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/mvn/packages")
@Produces(MediaType.TEXT_PLAIN)
public class DependencyApi {

    DependencyApiService service = new DependencyApiServiceImpl();

    @GET
    @Path("/{pkg}/{pkg_ver}/deps")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageDependencies(@PathParam("pkg") String package_name,
                                           @PathParam("pkg_ver") String package_version) {
        return service.getPackageDependencies(package_name, package_version);
    }
}
