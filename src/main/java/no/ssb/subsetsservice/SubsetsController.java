package no.ssb.subsetsservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@RestController
public class SubsetsController {

    static final String LDS_PROD = "http://lds-klass.klass.svc.cluster.local/ns/ClassificationSubset";
    static final String LDS_LOCAL = "http://localhost:9090/ns/ClassificationSubset";
    private static String LDS_SUBSET_API = "";

    private static final String KLASS_CLASSIFICATIONS_API = "https://data.ssb.no/api/klass/v1/classifications";

    private static final boolean prod = false;

    public SubsetsController(){
        if (prod){
            LDS_SUBSET_API = LDS_PROD;
        } else {
            LDS_SUBSET_API = LDS_LOCAL;
        }
    }

    @GetMapping("/v1/subsets")
    public ResponseEntity<String> getSubsets() {
        return getFrom(LDS_SUBSET_API, "");
    }

    /**
     * This method SHOULD figure out what the 'id' of the subset is from the value inside the JSON
     * and then post to subsets/{id} //TODO: Make it so
     * @param subsetsJson
     * @return
     */
    @PostMapping("/v1/subsets")
    public ResponseEntity<String> postSubset(@RequestBody String subsetsJson) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode actualObj = mapper.readTree(subsetsJson);
            if (actualObj != null){
                JsonNode idJN = actualObj.get("id");
                String id = idJN.asText();
                // TODO: check if subset already exists. Do not overwrite. new version instead.
                return postTo(LDS_SUBSET_API, "/"+id, subsetsJson);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/v1/subsets/{id}")
    public ResponseEntity<String> getSubset(@PathVariable("id") String id) {
        return getFrom(LDS_SUBSET_API, "/"+id);
    }

    @GetMapping("/v1/versions/{id}")
    public ResponseEntity<String> getVersions(@PathVariable("id") String id) {
        return getFrom(LDS_SUBSET_API, "/"+id+"?timeline");
    }

    @GetMapping("/v1/versions/{id}/{version}")
    public ResponseEntity<JsonNode> getVersion(@PathVariable("id") String id, @PathVariable("version") String version) {
        ResponseEntity<String> ldsRE = getFrom(LDS_SUBSET_API, "/"+id+"?timeline");
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode responseBodyJSON = mapper.readTree(ldsRE.getBody());
            if (responseBodyJSON != null){
                if (responseBodyJSON.isArray()) {
                    ArrayNode arrayNode = (ArrayNode) responseBodyJSON;
                    for (int i = 0; i < arrayNode.size(); i++) {
                        JsonNode arrayEntry = arrayNode.get(i);
                        String entryVersion = arrayEntry.get("document").get("version").asText();
                        if (entryVersion.equals(version)){
                            return new ResponseEntity<>(arrayEntry.get("document"), HttpStatus.OK);
                        }
                    }
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/v1/subsets/{id}/codes")
    public ResponseEntity<String> getSubsetCodes(@PathVariable("id") String id) {
        ResponseEntity<String> ldsRE = getFrom(LDS_SUBSET_API, "/"+id);
        return ldsRE; //TODO: Replace with a new RE containing a list of the codes only
    }

    @GetMapping("/v1/subsets/{id}/codes?from={fromDate}&to={toDate}")
    public ResponseEntity<String> getSubsetCodes(@PathVariable("id") String id, @PathVariable("fromDate") String fromDate, @PathVariable("toDate") String toDate) {
        ResponseEntity<String> ldsRE = getFrom(LDS_SUBSET_API, "/"+id+"?timeline");
        //TODO: find intersection of valid codes for each version that is valid in the range.
        //Step 1: Add all codes from all versions to a Hashmap, incrementing by 1 every time it is added.
        //Step 2: Check each code in the map. If it has a value equal to the total number of versions, add it to the return list.
        //Step 3: Return list of codes.
        return ldsRE;
    }

    @GetMapping("/v1/subsets/{id}/codesAt?date={date}")
    public ResponseEntity<String> getSubsetCodesAt(@PathVariable("id") String id, @PathVariable("date") String date) {
        ResponseEntity<String> ldsRE = getFrom(LDS_SUBSET_API, "/"+id+"?timeline");
        //TODO: find version that is valid at date
        //For each version, descending: if 'date' is at or after the version's 'validFrom', return the version's code list
        return ldsRE;
    }

    @PutMapping(value = "/v1/subsets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> putSubset(@PathVariable("id") String id, @RequestBody String subsetJson) {
        // TODO: check if subset already exists. Do not overwrite. new version instead.
        return postTo(LDS_SUBSET_API, "/"+id, subsetJson);
    }

    @GetMapping("/v1/subsets?schema")
    public ResponseEntity<String> getSchema(){
        return getFrom(LDS_SUBSET_API,"/?schema");
    }

    @GetMapping("/v1/classifications")
    public ResponseEntity<String> getClassifications(){
        return getFrom(KLASS_CLASSIFICATIONS_API, ".json");
    }

    @GetMapping("/v1/classifications/{id}")
    public ResponseEntity<String> getClassification(@PathVariable("id") String id){
        return getFrom(KLASS_CLASSIFICATIONS_API, "/"+id+".json");
    }

    static ResponseEntity<String> getFrom(String apiBase, String additional)
    {
        // TODO: I am not sure if this is the right way of handling 404's from another server.
        try {
            ResponseEntity<String> response = new RestTemplate().getForEntity(apiBase + additional, String.class);
            return response;
        } catch (HttpClientErrorException e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    static ResponseEntity<String> postTo(String apiBase, String additional, String json){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> request = new HttpEntity<>(json, headers);
        ResponseEntity<String> response = new RestTemplate().postForEntity(apiBase+additional, request, String.class);
        System.out.println("POST to "+apiBase+additional+" - Status: "+response.getStatusCodeValue()+" "+response.getStatusCode().name());
        return response;
    }

    static ResponseEntity<String> putTo(String apiBase, String additional, String json){
        return postTo(apiBase, additional, json);
    }
}
