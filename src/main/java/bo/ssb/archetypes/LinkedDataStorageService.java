package bo.ssb.archetypes;

import javax.json.JsonObject;
import javax.ws.rs.*;

public interface LinkedDataStorageService {
    @GET
    JsonObject getAllSubsets();

    @GET
    @Path("/{id}")
    JsonObject getSubset( @PathParam("id") String id );

    @POST
    @Path("/{id}")
    JsonObject postSubset( @PathParam("id") String id, String subset );

    @PUT
    @Path("/{id}")
    JsonObject updateSubset( @PathParam("id") String movieId, String subset );
}
