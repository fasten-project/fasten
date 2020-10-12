package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.mvn.impl.PackageApiServiceImpl;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/mvn/packages")
@Produces(MediaType.TEXT_PLAIN)
public class PackageApi {

    PackageApiService service = new PackageApiServiceImpl();

    @GET
    @Path("/{pkg}/{pkg_ver}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageVersions(@PathParam("pkg") String package_name,
                                       @PathParam("pkg_ver") String package_version) {
        return service.getPackageVersions(package_name, package_version);
    }
}

