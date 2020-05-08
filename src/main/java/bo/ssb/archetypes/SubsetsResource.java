/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bo.ssb.archetypes;

import java.util.Collections;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;

/**
 * A simple JAX-RS resource.
 *
 * The message is returned as a JSON object.
 */
@Path("/subsets")
@RequestScoped
public class SubsetsResource {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

    /**
     * The greeting message provider.
     */
    private final SubsetsProvider subsetsProvider;

    /**
     * Using constructor injection to get a configuration property.
     * By default this gets the value from META-INF/microprofile-config
     *
     * @param subsetsConfig the configured subsetsProvider
     */
    @Inject
    public SubsetsResource(SubsetsProvider subsetsConfig) {
        this.subsetsProvider = subsetsConfig;
    }

    /**
     * Return a wordly greeting message.
     *
     * @return {@link JsonObject}
     */
    @SuppressWarnings("checkstyle:designforextension")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getDefaultMessage() {
        return JSON.createObjectBuilder()
                .add("subsets", subsetsProvider.getSubsets())
                .build();
    }

    /**
     * Return a greeting message using the name that was provided.
     *
     * @param id the id to get
     * @return {@link JsonObject}
     */
    @SuppressWarnings("checkstyle:designforextension")
    @Path("/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getMessage(@PathParam("id") String id) {
        return JSON.createObjectBuilder()
                .add(id, subsetsProvider.getSubset(id))
                .build();
    }

    /**
     * Post a new subset to the service
     * @param jsonObject
     * @return
     */
    @SuppressWarnings("checkstyle:designforextension")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RequestBody(name = "subset",
            required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(type = SchemaType.STRING, example = "{\"subset\" : {subset goes here}}")))
    @APIResponses({
            @APIResponse(name = "normal", responseCode = "204", description = "Subset posted"),
            @APIResponse(name = "missing 'subset'", responseCode = "400",
                    description = "JSON did not contain setting for 'subset'")})
    public Response postSubset(JsonObject jsonObject) {

        if (!jsonObject.containsKey("subset")) {
            JsonObject entity = JSON.createObjectBuilder()
                    .add("error", "No subset provided")
                    .build();
            return Response.status(Response.Status.BAD_REQUEST).entity(entity).build();
        }

        JsonObject newSubset = jsonObject.getJsonObject("subset");

        subsetsProvider.updateSubset(newSubset);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    /**
     * Update an existing subset to the service
     * @param jsonObject
     * @return
     */
    @Path("/{id}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RequestBody(name = "subset",
            required = true,
            content = @Content(mediaType = "application/json",
                    schema = @Schema(type = SchemaType.STRING, example = "{\"subset\" : {subset goes here}}")))
    @APIResponses({
            @APIResponse(name = "normal", responseCode = "204", description = "Subset updated"),
            @APIResponse(name = "missing 'subset'", responseCode = "400",
                    description = "JSON did not contain setting for 'subset'")})
    public Response updateSubset(JsonObject jsonObject) {

        if (!jsonObject.containsKey("subset")) {
            JsonObject entity = JSON.createObjectBuilder()
                    .add("error", "No subset provided")
                    .build();
            return Response.status(Response.Status.BAD_REQUEST).entity(entity).build();
        }

        JsonObject newSubset = jsonObject.getJsonObject("subset");

        subsetsProvider.updateSubset(newSubset);
        return Response.status(Response.Status.NO_CONTENT).build();
    }
}
