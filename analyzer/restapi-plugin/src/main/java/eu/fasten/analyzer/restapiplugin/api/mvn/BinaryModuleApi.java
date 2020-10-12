package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.mvn.impl.BinaryModuleApiServiceImpl;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/mvn/packages")
@Produces(MediaType.TEXT_PLAIN)
public class BinaryModuleApi {

    BinaryModuleApiService service = new BinaryModuleApiServiceImpl();

    @GET
    @Path("/{pkg}/{pkg_ver}/binary-modules")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageBinaryModules(@PathParam("pkg") String package_name,
                                            @PathParam("pkg_ver") String package_version) {
        return service.getPackageBinaryModules(package_name, package_version);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/binary-modules/{binary}/metadata")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBinaryModuleMetadata(@PathParam("pkg") String package_name,
                                            @PathParam("pkg_ver") String package_version,
                                            @PathParam("binary") String binary_module) {
        return service.getBinaryModuleMetadata(package_name, package_version, binary_module);
    }

    @GET
    @Path("/{pkg}/{pkg_ver}/binary-modules/{binary}/files")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBinaryModuleFiles(@PathParam("pkg") String package_name,
                                         @PathParam("pkg_ver") String package_version,
                                         @PathParam("binary") String binary_module) {
        return service.getBinaryModuleFiles(package_name, package_version, binary_module);
    }
}
