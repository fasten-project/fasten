package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.RestApplication;
import eu.fasten.analyzer.restapiplugin.api.mvn.impl.ModuleApiServiceImpl;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/mvn/packages")
@Produces(MediaType.TEXT_PLAIN)
public class ModuleApi {

    ModuleApiService service = new ModuleApiServiceImpl();

    @GET
    @Path("/{pkg}/{pkg_ver}/modules")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageModules(@PathParam("pkg") String package_name,
                                      @PathParam("pkg_ver") String package_version,
                                      @DefaultValue("0")
                                      @QueryParam("offset") short offset,
                                      @DefaultValue(RestApplication.DEFAULT_PAGE_SIZE)
                                      @QueryParam("limit") short limit) {
        return service.getPackageModules(package_name, package_version, offset, limit);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/modules/{namespace}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModuleMetadata(@PathParam("pkg") String package_name,
                                      @PathParam("pkg_ver") String package_version,
                                      @PathParam("namespace") String module_namespace,
                                      @DefaultValue("0")
                                      @QueryParam("offset") short offset,
                                      @DefaultValue(RestApplication.DEFAULT_PAGE_SIZE)
                                      @QueryParam("limit") short limit) {
        return service.getModuleMetadata(package_name, package_version, module_namespace, offset, limit);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/modules/{namespace}/files")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModuleFiles(@PathParam("pkg") String package_name,
                                   @PathParam("pkg_ver") String package_version,
                                   @PathParam("namespace") String module_namespace,
                                   @DefaultValue("0")
                                   @QueryParam("offset") short offset,
                                   @DefaultValue(RestApplication.DEFAULT_PAGE_SIZE)
                                   @QueryParam("limit") short limit) {
        return service.getModuleFiles(package_name, package_version, module_namespace, offset, limit);
    }
}
