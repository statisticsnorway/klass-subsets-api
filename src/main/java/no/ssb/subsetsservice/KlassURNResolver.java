package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KlassURNResolver {

    private static final Logger LOG = LoggerFactory.getLogger(KlassURNResolver.class);
    public static final String klassBaseURL = "https://data.ssb.no/api/klass/v1/classifications/";

    public static JsonNode resolveURN(String codeURN, String from, String to){
        LOG.info("Attempting to resolve the KLASS code URN "+codeURN);
        String[] urnSplitColon = codeURN.split(":");
        String classificationID = urnSplitColon[3];
        String code = urnSplitColon[5];
        String url = makeURL(classificationID, from, to, code);
        ResponseEntity<JsonNode> selectCodesRE = getFrom(url);
        JsonNode codes = selectCodesRE.getBody();
        return codes;
    }

    public static ArrayNode resolveURNs(List<String> codeURNs, String from, String to){
        Map<String, String> classificationCodesMap = new HashMap<>();
        for (String codeURN : codeURNs) {
            String[] urnSplitColon = codeURN.split(":");
            String classificationID = urnSplitColon[3];
            String code = urnSplitColon[5];
            classificationCodesMap.merge(classificationID, code, (c1, c2)-> c1+","+c2);
        }
        List<ArrayNode> codesArrayNodeList = new ArrayList<>();
        classificationCodesMap.forEach((classification, codes) -> codesArrayNodeList.add((ArrayNode)(getFrom(makeURL(classification, from, to, codes)).getBody().get(Field.CODES))));
        ArrayNode allCodesArrayNode = new ObjectMapper().createArrayNode();
        for (ArrayNode codes : codesArrayNodeList) {
            codes.forEach(allCodesArrayNode::add);
        }
        return allCodesArrayNode;
    }

    private static String makeURL(String classificationID, String from, String to, String codes){
        return String.format("%s%s/codes.json?from=%s&to=%s&selectCodes=%s", klassBaseURL, classificationID, from, to, codes);
    }

    static ResponseEntity<JsonNode> getFrom(String url)
    {
        LOG.info("Attempting to GET "+url);
        try {
            return new RestTemplate().getForEntity(url, JsonNode.class);
        } catch (HttpClientErrorException | HttpServerErrorException e){
            return ErrorHandler.newHttpError("could not retrieve "+url+".", e.getStatusCode(), LOG);
        }
    }
}
