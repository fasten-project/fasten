package eu.fasten.analyzer.restapiplugin.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Dummy resource for testing purposes.
 * FIXME delete.
 */
@Path("/hello")
@Produces(MediaType.TEXT_PLAIN)
public class MessageResource {

    /**
     * Dummy endpoint for testing purposes.
     *
     * @param msg
     * @return
     */
    @GET
    @Path("/{param}")
    public Response printMessage(@PathParam("param") String msg) {
        String result = "Hello " + msg + "!";
        return Response.status(200).entity(result).build();
    }
}
