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
    @Path("/{pkg}/versions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageVersions(@PathParam("pkg") String package_name) {
        return service.getPackageVersions(package_name);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackage(@PathParam("pkg") String package_name,
                               @PathParam("pkg_ver") String package_version) {
        return service.getPackage(package_name, package_version);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageMetadata(@PathParam("pkg") String package_name,
                                       @PathParam("pkg_ver") String package_version) {
        return service.getPackageMetadata(package_name, package_version);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/callgraph")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageCallgraph(@PathParam("pkg") String package_name,
                                        @PathParam("pkg_ver") String package_version) {
        return service.getPackageCallgraph(package_name, package_version);
    }
}

