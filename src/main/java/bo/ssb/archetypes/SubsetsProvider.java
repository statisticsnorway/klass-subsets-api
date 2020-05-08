/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.net.URI;
import java.net.URISyntaxException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

/**
 * Provider for subsets
 */
@ApplicationScoped
public class SubsetsProvider {

    private final String API_KLASS = "https://data.ssb.no/api/klass/v1/"; // TODO: Get from config
    URI klassApiUri;

    KlassService klassSvc;
    private final String API_LDS = "http://lds-klass.klass.svc.cluster.local"; // TODO: Get from config?
    private final String API_LDS_SUBSETS = API_LDS + "/ns/ClassificationSubset";
    URI ldsApiUri;
    LinkedDataStorageService ldsSvc;

    //private final AtomicReference<String> message = new AtomicReference<>();

    /**
     * Create a new subsets provider, reading the message from configuration.
     *
     * @param message greeting to use
     */
    @Inject
    public SubsetsProvider(@ConfigProperty(name = "app.subsets") String message) {
        //this.message.set(message);

        try {
            ldsApiUri = new URI(API_LDS_SUBSETS);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        ldsSvc = RestClientBuilder.newBuilder()
                .baseUri(ldsApiUri)
                .build(LinkedDataStorageService.class);

        try {
            klassApiUri = new URI(API_KLASS);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        klassSvc = RestClientBuilder.newBuilder()
                .baseUri(klassApiUri)
                .build(KlassService.class);
    }

    /**
     * Get all subsets with a call to LDS
     * @return
     */
    JsonObject getSubsets() {
        return ldsSvc.getAllSubsets();
    }

    /**
     * Get a single subset with id 'id' from a call to LDS
     * @param id
     * @return
     */
    JsonObject getSubset(String id) {
        return ldsSvc.getSubset(id);
    }

    /**
     * Put subset to LDS
     * @param subset
     */
    void updateSubset(JsonObject subset) {
        ldsSvc.updateSubset(subset.getString("id"), subset);
    }

    /**
     * Get a code from KLASS api
     * @param code
     * @return
     */
    JsonObject getCode(String code) {
        return klassSvc.getCode(code);
    }
}
