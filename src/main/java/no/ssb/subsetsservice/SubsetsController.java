package no.ssb.subsetsservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

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

    @PostMapping("/v1/subsets")
    public ResponseEntity<String> postSubsets(@RequestBody String subsetsJson) {
        return postTo(LDS_SUBSET_API, "", subsetsJson);
    }

    @GetMapping("/v1/subsets/{id}")
    public ResponseEntity<String> getSubset(@PathVariable("id") String id) {
        return getFrom(LDS_SUBSET_API, "/"+id);
    }

    @PutMapping("/v1/subsets/{id}")
    public ResponseEntity<String> putSubset(@PathVariable("id") String id, @RequestBody String subsetJson) {
        return putTo(LDS_SUBSET_API, "/"+id, subsetJson);
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
        //TODO: For each subset in json of subsets, put indnividually to LDS ?
        return new RestTemplate().postForEntity(apiBase+additional, json, String.class);
    }

    static ResponseEntity<String> putTo(String apiBase, String additional, String json){
        return new RestTemplate().postForEntity(apiBase+additional, json, String.class);
    }
}
