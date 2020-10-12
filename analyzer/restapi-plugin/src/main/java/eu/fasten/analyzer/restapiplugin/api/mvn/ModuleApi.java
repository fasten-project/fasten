package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.mvn.impl.ModuleApiServiceImpl;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
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
                                      @PathParam("pkg_ver") String package_version) {
        return service.getPackageModules(package_name, package_version);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/modules/{namespace}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModuleMetadata(@PathParam("pkg") String package_name,
                                      @PathParam("pkg_ver") String package_version,
                                      @PathParam("namespace") String module_namespace) {
        return service.getModuleMetadata(package_name, package_version, module_namespace);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/modules/{namespace}/files")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModuleFiles(@PathParam("pkg") String package_name,
                                   @PathParam("pkg_ver") String package_version,
                                   @PathParam("namespace") String module_namespace) {
        return service.getModuleFiles(package_name, package_version, module_namespace);
    }
}
