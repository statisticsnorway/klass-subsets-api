package no.ssb.subsetsservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class SubsetsController {

    private static final String LDS_PROD = "http://lds-klass.klass.svc.cluster.local/ns/ClassificationSubset";
    private static final String LDS_LOCAL = "http://localhost:9090/ns/ClassificationSubset";
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

    @RequestMapping("/v1/subsets")
    public ResponseEntity<String> getSubsets() {
        ResponseEntity<String> responseEntity = getFrom(LDS_SUBSET_API, "");
        return responseEntity;
    }

    @RequestMapping("/v1/subsets/{id}")
    public ResponseEntity<String> getSubset(@PathVariable("id") String id) {
        ResponseEntity<String> responseEntity = getFrom(LDS_SUBSET_API, "/"+id);
        return responseEntity;
    }

    @RequestMapping("/v1/subsets?schema")
    public ResponseEntity<String> getSchema(){
        ResponseEntity<String> responseEntity = getFrom(LDS_SUBSET_API,"/?schema");
        return responseEntity;
    }

    @RequestMapping("/v1/codes")
    public ResponseEntity<String> getCodes(){
        ResponseEntity<String> responseEntity = getFrom(KLASS_CODES_API, ".json");
        return responseEntity;
    }

    @RequestMapping("/v1/codes/{id}")
    public ResponseEntity<String> getCode(@PathVariable("id") String id){
        ResponseEntity<String> responseEntity = getFrom(KLASS_CODES_API, "/"+id+".json");
        return responseEntity;
    }

    private static ResponseEntity<String> getFrom(String apiBase, String additional)
    {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> responseEntity = restTemplate.getForEntity(apiBase + additional, String.class);
        return responseEntity;
        //return restTemplate.getForObject(apiBase + additional, String.class);
    }
}
