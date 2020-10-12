package eu.fasten.analyzer.restapiplugin.api.mvn;

import eu.fasten.analyzer.restapiplugin.api.mvn.impl.FileApiServiceImpl;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/mvn/packages")
@Produces(MediaType.TEXT_PLAIN)
public class FileApi {

    FileApiService service = new FileApiServiceImpl();

    @GET
    @Path("/{pkg}/{pkg_ver}/files")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPackageFiles(@PathParam("pkg") String package_name,
                                    @PathParam("pkg_ver") String package_version) {
        return service.getPackageFiles(package_name, package_version);
    }
}
