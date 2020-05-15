package no.ssb.subsetsservice;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@RestController
public class SubsetsController {

    static final String LDS_PROD = "http://lds-klass.klass.svc.cluster.local/ns/ClassificationSubset";
    static final String LDS_LOCAL = "http://localhost:9090/ns/ClassificationSubset";
    private static String LDS_SUBSET_API = "";

    private static final String KLASS_CODES_API = "https://data.ssb.no/api/klass/v1/classifications";

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
        return postTo(LDS_SUBSET_API, "", subsetsJson);
    }

    @GetMapping("/v1/subsets/{id}")
    public ResponseEntity<String> getSubset(@PathVariable("id") String id) {
        return getFrom(LDS_SUBSET_API, "/"+id);
    }

    @PutMapping(value = "/v1/subsets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> putSubset(@PathVariable("id") String id, @RequestBody String subsetJson) {
        return postTo(LDS_SUBSET_API, "/"+id, subsetJson);
    }

    @GetMapping("/v1/subsets?schema")
    public ResponseEntity<String> getSchema(){
        return getFrom(LDS_SUBSET_API,"/?schema");
    }

    @GetMapping("/v1/codes")
    public ResponseEntity<String> getCodes(){
        return getFrom(KLASS_CODES_API, ".json");
    }

    @GetMapping("/v1/codes/{id}")
    public ResponseEntity<String> getCode(@PathVariable("id") String id){
        return getFrom(KLASS_CODES_API, "/"+id+".json");
    }

    static ResponseEntity<String> getFrom(String apiBase, String additional)
    {
        return new RestTemplate().getForEntity(apiBase + additional, String.class);
    }

    static ResponseEntity<String> postTo(String apiBase, String additional, String json){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<String> request = new HttpEntity<>(json, headers);
        ResponseEntity<String> response = new RestTemplate().postForEntity(apiBase+additional, request, String.class);
        System.out.println("POST to "+apiBase+additional+" - Status: "+response.getStatusCodeValue()+" "+response.getStatusCode().name()+" "+response.getStatusCode().getReasonPhrase());
        return response;
    }

    static ResponseEntity<String> putTo(String apiBase, String additional, String json){
        return postTo(apiBase, additional, json);
    }
}
