package bo.ssb.archetypes;

import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

public interface LinkedDataStorageService {
    @GET
    JsonObject getAllSubsets();

    @GET
    @Path("/{id}")
    JsonObject getSubset( @PathParam("id") String id );

    @PUT
    @Path("/{id}")
    JsonObject updateSubset( @PathParam("id") String id, JsonObject subset );
}
