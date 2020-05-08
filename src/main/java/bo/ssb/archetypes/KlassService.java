package bo.ssb.archetypes;

import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

public interface KlassService {

    @GET
    @Path("/classifications/68/codesAt.json?date=2020-03-18&selectCodes={code}")
    JsonObject getCode(@PathParam("code") String code );
}
