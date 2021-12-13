package no.ssb.subsetsservice.util;

import com.fasterxml.jackson.databind.JsonNode;
import no.ssb.subsetsservice.controller.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

public class KlassURNResolver {

    private static final Logger LOG = LoggerFactory.getLogger(KlassURNResolver.class);
    public static String KLASS_BASE_URL = "https://data.ssb.no/api/klass";
    public static final String CLASSIFICATIONS_API = "/v1/classifications";

    public boolean pingKLASSClassifications() {
        ResponseEntity<String> re = getStringResponseFrom(String.format("%s/ping/", KLASS_BASE_URL));
        return re.getStatusCode().equals(HttpStatus.OK);
    }

    public static String getURL(){
        return System.getenv().getOrDefault("API_KLASS", KLASS_BASE_URL);
    }

    public static String makeKLASSCodesFromToURL(String classificationID, String from, String to, String codes, String language) {
        KLASS_BASE_URL = getURL();
        return String.format("%s%s/%s/codes.json?from=%s&to=%s&selectCodes=%s&language=%s", KLASS_BASE_URL, CLASSIFICATIONS_API, classificationID, from, to, codes, language);
    }

    public static String makeKLASSClassificationURL(String classificationID) {
        KLASS_BASE_URL = getURL();
        return String.format("%s%s/%s.json", KLASS_BASE_URL, CLASSIFICATIONS_API, classificationID);
    }

    public static ResponseEntity<JsonNode> getFrom(String url) {
        LOG.info("KLASS Attempting to GET JsonNode from "+url);
        try {
            ResponseEntity<JsonNode> re = new RestTemplate().getForEntity(url, JsonNode.class);
            LOG.debug("KLASS returned a response entity.");
            if (!re.getStatusCode().is2xxSuccessful())
                LOG.debug("GET call to "+url+" did NOT return 2xx successful");
            else
                LOG.debug("GET to "+url+" was 2xx successful. Returning . . .");
            return re;
        } catch (HttpClientErrorException | HttpServerErrorException e){
            LOG.debug("KLASS Get threw a client or server error exception. Message: "+e.getMessage());
            return ErrorHandler.newHttpError("could not retrieve "+url+".", e.getStatusCode(), LOG);
        } catch (Exception | Error e){
            LOG.debug("KLASS Get threw an unexpected exception/error. Message: "+e.getMessage());
            return ErrorHandler.newHttpError("Unexpected error GETing against KLASS", HttpStatus.INTERNAL_SERVER_ERROR, LOG);
        }
    }

    private ResponseEntity<String> getStringResponseFrom(String url) {
        LOG.info("Attempting to GET "+url);
        try {
            return new RestTemplate().getForEntity(url, String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e){
            return new ResponseEntity<>(e.toString(), e.getStatusCode());
        }
    }
}
