package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;


import java.util.HashMap;
import java.util.Map;

public class KlassURNResolver {

    private static final Logger LOG = LoggerFactory.getLogger(KlassURNResolver.class);
    public static String KLASS_BASE_URL = "https://data.ssb.no/api/klass";
    public static final String CLASSIFICATIONS_API = "/v1/classifications";

    public boolean pingKLASSClassifications(){
        ResponseEntity<String> re = getStringResponseFrom(String.format("%s/ping/", KLASS_BASE_URL));
        return re.getStatusCode().equals(HttpStatus.OK);
    }

    public static String getURL(){
        return System.getenv().getOrDefault("API_KLASS", KLASS_BASE_URL);
    }

    /**
     * Get the Code list as an ArrayNode, containing KLASS information, URN, URL and Rank.
     * @param subset
     * @param to
     * @return
     */
    public ArrayNode resolveURNs(JsonNode subset, String to) {
        LOG.info("Resolving all code URNs in a subset");

        ArrayNode codes = (ArrayNode)subset.get(Field.CODES);
        String from = null;
        if (subset.has(Field.VALID_FROM))
            from = subset.get(Field.VALID_FROM).asText();
        else if (subset.has(Field.VERSION_VALID_FROM))
            from = subset.get(Field.VERSION_VALID_FROM).asText();
        else
            throw new IllegalArgumentException("subset did not contain validFrom or versionValidFrom");
        String fromDate = from.split("T")[0];
        String toDate = to.split("T")[0];

        Map<String, String> classificationCodesMap = new HashMap<>();
        Map<String, ObjectNode> urnCodeMap = new HashMap<>();

        for (int i = 0; i < codes.size(); i++) {
            ObjectNode code = codes.get(i).deepCopy();
            String URN = code.get(Field.URN).asText();
            String[] urnSplitColon = URN.split(":");
            String classificationID = "";
            String codeString = "";
            for (int i1 = 0; i1 < urnSplitColon.length; i1++) {
                String value = urnSplitColon[i1];
                if (value.equals("code")){
                    if (urnSplitColon.length > i1+1)
                        codeString = urnSplitColon[i1+1];
                } else if (value.equals("classifications")){
                    if (urnSplitColon.length > i1+1)
                        classificationID = urnSplitColon[i1+1];
                }
            }
            code.put(Field.CLASSIFICATION_ID, classificationID);
            code.put(Field.CODE, codeString);
            String selfURL = makeURL(classificationID, fromDate, toDate, codeString);
            ObjectMapper om = new ObjectMapper();
            ObjectNode self = om.createObjectNode().put("href", selfURL);
            ObjectNode links = om.createObjectNode().set(Field.SELF, self);
            code.set(Field._LINKS, links);
            classificationCodesMap.merge(classificationID, codeString, (c1, c2)-> c1+","+c2);
            urnCodeMap.put(URN, code);
        }

        if (fromDate.compareTo(toDate) >= 0 && !toDate.equals(""))
            throw new IllegalArgumentException("fromDate "+fromDate+" must be before toDate "+toDate+", but was the same as or after the toDate. ");

        for (Map.Entry<String, String> classificationCodesEntry : classificationCodesMap.entrySet()) {
            String classification = classificationCodesEntry.getKey();
            String codesString = classificationCodesEntry.getValue();
            String URL = makeURL(classification, fromDate, toDate, codesString);
            ArrayNode codesFromClassification = (ArrayNode)(getFrom(URL).getBody().get(Field.CODES));
            for (int i = 0; i < codesFromClassification.size(); i++) {
                JsonNode codeNode = codesFromClassification.get(i);
                String URN = Utils.generateURN(classification, codeNode.get(Field.CODE).asText());
                ObjectNode editableCode = urnCodeMap.get(URN);
                codeNode.fields().forEachRemaining(e -> editableCode.set(e.getKey(), e.getValue()));
                codesFromClassification.set(i, editableCode);
                urnCodeMap.put(URN, editableCode);
            }
        }
        
        ArrayNode allCodesArrayNode = new ObjectMapper().createArrayNode();
        allCodesArrayNode.addAll(urnCodeMap.values());
        return allCodesArrayNode;
    }

    private String makeURL(String classificationID, String from, String to, String codes){
        KLASS_BASE_URL = getURL();
        return String.format("%s%s/%s/codes.json?from=%s&to=%s&selectCodes=%s", KLASS_BASE_URL, CLASSIFICATIONS_API, classificationID, from, to, codes);
    }

    private ResponseEntity<JsonNode> getFrom(String url)
    {
        LOG.info("Attempting to GET "+url);
        try {
            return new RestTemplate().getForEntity(url, JsonNode.class);
        } catch (HttpClientErrorException | HttpServerErrorException e){
            return ErrorHandler.newHttpError("could not retrieve "+url+".", e.getStatusCode(), LOG);
        }
    }

    private ResponseEntity<String> getStringResponseFrom(String url)
    {
        LOG.info("Attempting to GET "+url);
        try {
            return new RestTemplate().getForEntity(url, String.class);
        } catch (HttpClientErrorException | HttpServerErrorException e){
            return new ResponseEntity<>(e.toString(), e.getStatusCode());
        }
    }
}
