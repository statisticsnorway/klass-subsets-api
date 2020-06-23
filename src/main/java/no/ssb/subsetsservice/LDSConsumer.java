package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

public class LDSConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(LDSConsumer.class);
    static String LDS_URL;

    public LDSConsumer(String ldsSubsetApi) {
        LDS_URL = ldsSubsetApi;
    }

    ResponseEntity<JsonNode> getFrom(String additional)
    {
        // TODO: I am not sure if this is the right way of handling 404's from another server.
        try {
            ResponseEntity<JsonNode> response = new RestTemplate().getForEntity(LDS_URL + additional, JsonNode.class);
            return response;
        } catch (HttpClientErrorException e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    ResponseEntity<JsonNode> postTo(String additional, JsonNode json){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<JsonNode> request = new HttpEntity<>(json, headers);
        ResponseEntity<JsonNode> response = new RestTemplate().postForEntity(LDS_URL+additional, request, JsonNode.class);
        LOG.debug("POST to "+LDS_URL+additional+" - Status: "+response.getStatusCodeValue()+" "+response.getStatusCode().name());
        return response;
    }

    ResponseEntity<JsonNode> putTo(String additional, JsonNode json){
        return postTo(additional, json);
    }
}
