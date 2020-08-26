package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

public class LDSConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(LDSConsumer.class);
    static String LDS_LOCAL = "http://localhost:9090";
    static String LDS_URL;

    LDSConsumer(){
        LDS_URL = getURLFromEnvOrDefault();
    }


    LDSConsumer(String API_LDS){
        LDS_URL = API_LDS;
        if (LDS_URL.equals(""))
            LDS_URL = getURLFromEnvOrDefault();
    }

    private static String getURLFromEnvOrDefault(){
        String host = System.getenv().getOrDefault("HOST_ADDRESS", "localhost");
        LOG.debug("Host: "+host);
        LDS_LOCAL = "http://"+host+":9090";
        LOG.debug("LDS_LOCAL: "+LDS_LOCAL);
        return System.getenv().getOrDefault("API_LDS", LDS_LOCAL);
    }

    ResponseEntity<JsonNode> getFrom(String additional)
    {
        try {
            return new RestTemplate().getForEntity(LDS_URL + additional, JsonNode.class);
        } catch (HttpClientErrorException | HttpServerErrorException e){
            return ErrorHandler.newHttpError("could not retrieve "+LDS_URL+additional+".", e.getStatusCode(), LOG);
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
