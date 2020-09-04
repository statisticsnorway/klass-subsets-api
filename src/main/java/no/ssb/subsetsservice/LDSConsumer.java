package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Collections;

public class LDSConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(LDSConsumer.class);
    static String LDS_LOCAL = "http://localhost:9090";
    static String LDS_URL;

    LDSConsumer(){
        LDS_URL = getURLFromEnvOrDefault();
        LOG.debug("LDS URL: "+LDS_URL);
    }


    LDSConsumer(String API_LDS){
        LDS_URL = API_LDS;
        if (LDS_URL.equals(""))
            LDS_URL = getURLFromEnvOrDefault();
        LOG.debug("LDS URL: "+LDS_URL);
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
        // Because RestTemplate fails when the response is too long, I use Apache Commons Http Client instead,
        // and force the response into a ResponseEntity.
        // I could not get Spring WebClient to work, which would have been my first choice
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(LDS_URL + additional);
        CloseableHttpResponse response1 = null;
        try {
            response1 = httpclient.execute(httpGet);
            System.out.println(response1.getStatusLine());
            System.out.println(response1.toString());
            HttpEntity entity1 = response1.getEntity();
            int status = response1.getStatusLine().getStatusCode();
            HttpStatus httpStatus = HttpStatus.resolve(status);
            JsonNode jsonNode = new ObjectMapper().readTree(entity1.getContent());
            ResponseEntity<JsonNode> responseEntity = new ResponseEntity<>(jsonNode, httpStatus);

            EntityUtils.consume(entity1);

            return responseEntity;
        } catch (Exception e) {
            LOG.error(e.toString());
            return ErrorHandler.newHttpError("Could not retrieve "+LDS_URL+additional+" because of an exception: "+e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, LOG);
        } finally {
            try {
                if (response1 != null)
                    response1.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    ResponseEntity<JsonNode> postTo(String additional, JsonNode json){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        org.springframework.http.HttpEntity<JsonNode> request = new org.springframework.http.HttpEntity<>(json, headers);
        ResponseEntity<JsonNode> response = new RestTemplate().postForEntity(LDS_URL+additional, request, JsonNode.class);
        LOG.debug("POST to "+LDS_URL+additional+" - Status: "+response.getStatusCodeValue()+" "+response.getStatusCode().name());
        return response;
    }

    ResponseEntity<JsonNode> putTo(String additional, JsonNode json){
        ResponseEntity<JsonNode> postRE = postTo(additional, json);
        HttpStatus statusCode = postRE.getStatusCode();
        if (postRE.getStatusCode().equals(HttpStatus.CREATED))
            statusCode = HttpStatus.OK;
        return new ResponseEntity<>(postRE.getBody(), statusCode);
    }

    public void delete(String url) {
        LOG.debug("DELETE "+LDS_URL+url);
        new RestTemplate().delete(LDS_URL+url);
    }
}
