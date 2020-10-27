package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.StringEntity;
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
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class LDSConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(LDSConsumer.class);
    static String LDS_LOCAL = "http://localhost:9090";
    static String LDS_URL;


    LDSConsumer() {
        LDS_URL = getURLFromEnvOrDefault();
        LOG.debug("LDS URL: "+LDS_URL);
    }


    LDSConsumer(String API_LDS) {
        LDS_URL = API_LDS;
        if (LDS_URL.equals(""))
            LDS_URL = getURLFromEnvOrDefault();
        LOG.debug("LDS URL: "+LDS_URL);
    }

    private static String getURLFromEnvOrDefault() {
        String host = System.getenv().getOrDefault("HOST_ADDRESS", "localhost");
        LOG.debug("Host: "+host);
        LDS_LOCAL = "http://"+host+":9090";
        LOG.debug("LDS_LOCAL: "+LDS_LOCAL);
        return System.getenv().getOrDefault("API_LDS", LDS_LOCAL);
    }

    /**
     * Using the OkHttpClient to get from LDS, with an explicit timeout
     * @param additional
     * @return
     */
    ResponseEntity<JsonNode> getFromOkHttp(String additional) {
        try {
            OkHttpClient client = new OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build();
            Request request = new Request.Builder()
                    .url(LDS_URL + additional)
                    .build();

            Call call = client.newCall(request);
            try (Response response = call.execute()) {
                HttpStatus httpStatus = HttpStatus.resolve(response.code());
                ResponseBody body = response.body();
                assert body != null : "Response body was null";
                InputStream inputStream = body.byteStream();
                JsonNode jsonNode = new ObjectMapper().readTree(inputStream);
                return new ResponseEntity<>(jsonNode, httpStatus);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ErrorHandler.newHttpError("Could not retrieve "+LDS_URL+additional+" because of an IOException: "+e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, LOG);
        }
    }

    ResponseEntity<JsonNode> getFrom(String additional) {
        // Because Spring RestTemplate fails when the response is too long, I use Apache Commons Http Client instead,
        // and pack the response into a ResponseEntity, so it feels like Spring to the user.
        // I could not get Spring WebClient to work, which would have been my first choice.
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(LDS_URL + additional);
        CloseableHttpResponse response1 = null;
        try {
            try {
                response1 = httpclient.execute(httpGet);
            } catch (ConnectException e){
                return ErrorHandler.newHttpError("Could not retrieve "+LDS_URL+additional+" because of an exception: "+e.toString(), HttpStatus.INTERNAL_SERVER_ERROR, LOG);
            }
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
            e.printStackTrace();
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
        String fullURLString = LDS_URL+additional;
        LOG.debug("Building request for POSTing to "+fullURLString);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        org.springframework.http.HttpEntity<JsonNode> request = new org.springframework.http.HttpEntity<>(json, headers);
        LOG.debug("POSTing to "+fullURLString);
        ResponseEntity<JsonNode> response = null;
        try {
            response = new RestTemplate().postForEntity(fullURLString, request, JsonNode.class);
        } catch (HttpClientErrorException e){
            e.printStackTrace();
            return ErrorHandler.newHttpError("LDS: Could not POST to "+fullURLString+" because of exception "+e.toString(), e.getStatusCode(), LOG);
        }
        LOG.debug("POST to "+fullURLString+" - Status: "+response.getStatusCodeValue()+" "+response.getStatusCode().name());
        return response;
    }

    /**
     * Using a true HTTP PUT request to the LDS will create or overwrite a managed resource by id,
     * or an embedded resource (property) within a managed resource.
     * In the LDS API docs, POST is never mentioned. But I've used POST until now and it worked.
     * @param additional
     * @param json
     * @return
     */
    ResponseEntity<JsonNode> putTo(String additional, JsonNode json){
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(LDS_URL+additional);
        httpPut.setHeader("Accept", "application/json");
        httpPut.setHeader("Content-type", "application/json");
        StringEntity stringEntity = null;
        try {
            stringEntity = new StringEntity(json.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return ErrorHandler.newHttpError(
                    "Could not parse jsonNode.toString() into a StringEntity. Exception "+e.toString(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    LOG);
        }
        httpPut.setEntity(stringEntity);
        try {
            CloseableHttpResponse response = httpclient.execute(httpPut);
            HttpEntity entity = response.getEntity();
            int status = response.getStatusLine().getStatusCode();
            HttpStatus httpStatus = HttpStatus.resolve(status);
            LOG.debug("PUT to "+LDS_URL+additional+" - Status: "+httpStatus.toString());
            if (!httpStatus.equals(HttpStatus.CREATED) && !httpStatus.equals(HttpStatus.OK)){
                String responseBodyString = EntityUtils.toString(entity);
                return ErrorHandler.newHttpError(
                        "LDS returned code "+httpStatus+" and body "+responseBodyString,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        LOG);
            }
            return new ResponseEntity<>(json, httpStatus);
        } catch (IOException e) {
            e.printStackTrace();
            return ErrorHandler.newHttpError(
                    "Could not PUT because of an exception "+e.toString(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    LOG);
        }
    }

    public void delete(String url) {
        LOG.debug("DELETE "+LDS_URL+url);
        new RestTemplate().delete(LDS_URL+url);
    }
}
