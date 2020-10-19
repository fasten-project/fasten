package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.RestApplication;
import eu.fasten.analyzer.restapiplugin.api.mvn.impl.PackageApiServiceImpl;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/mvn/packages")
@Produces(MediaType.TEXT_PLAIN)
public class PackageApi {

    PackageApiService service = new PackageApiServiceImpl();

    @GET
    @Path("/{pkg}/versions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageVersions(@PathParam("pkg") String package_name,
                                       @DefaultValue("0")
                                       @QueryParam("offset") short offset,
                                       @DefaultValue(RestApplication.DEFAULT_PAGE_SIZE)
                                       @QueryParam("limit") short limit) {
        return service.getPackageVersions(package_name, offset, limit);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageVersion(@PathParam("pkg") String package_name,
                                      @PathParam("pkg_ver") String package_version,
                                      @DefaultValue("0")
                                      @QueryParam("offset") short offset,
                                      @DefaultValue(RestApplication.DEFAULT_PAGE_SIZE)
                                      @QueryParam("limit") short limit) {
        return service.getPackageVersion(package_name, package_version, offset, limit);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageMetadata(@PathParam("pkg") String package_name,
                                       @PathParam("pkg_ver") String package_version,
                                       @DefaultValue("0")
                                       @QueryParam("offset") short offset,
                                       @DefaultValue(RestApplication.DEFAULT_PAGE_SIZE)
                                       @QueryParam("limit") short limit) {
        return service.getPackageMetadata(package_name, package_version, offset, limit);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/callgraph")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageCallgraph(@PathParam("pkg") String package_name,
                                        @PathParam("pkg_ver") String package_version,
                                        @DefaultValue("0")
                                        @QueryParam("offset") short offset,
                                        @DefaultValue(RestApplication.DEFAULT_PAGE_SIZE)
                                        @QueryParam("limit") short limit) {
        return service.getPackageCallgraph(package_name, package_version, offset, limit);
    }
}

