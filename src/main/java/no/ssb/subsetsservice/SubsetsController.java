package no.ssb.subsetsservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
public class SubsetsController {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsController.class);

    static final String LDS_PROD = "http://lds-klass.klass.svc.cluster.local/ns/ClassificationSubset";
    static final String LDS_LOCAL = "http://localhost:9090/ns/ClassificationSubset";
    private static String LDS_SUBSET_API = "";

    private static String KLASS_CLASSIFICATIONS_API = "https://data.ssb.no/api/klass/v1/classifications";

    private static final boolean prod = false;

    public SubsetsController(){
        if (prod){
            LDS_SUBSET_API = System.getenv().getOrDefault("API_LDS", LDS_PROD);
        } else {
            LDS_SUBSET_API = LDS_LOCAL;
        }
        KLASS_CLASSIFICATIONS_API = System.getenv().getOrDefault("API_KLASS", KLASS_CLASSIFICATIONS_API);
    }

    @GetMapping("/v1/subsets")
    public ResponseEntity<String> getSubsets() {
        return getFrom(LDS_SUBSET_API, "");
    }

    /**
     * This method figures out what the 'id' of the subset is from the value inside the JSON
     * and then post to subsets/{id}
     * @param subsetsJson
     * @return
     */
    @PostMapping("/v1/subsets")
    public ResponseEntity<String> postSubset(@RequestBody JsonNode subsetsJson) {
        if (subsetsJson != null) {
            JsonNode idJN = subsetsJson.get("id");
            String id = idJN.textValue();
            ResponseEntity<String> ldsResponse = getFrom(LDS_SUBSET_API, "/"+id);
            if (ldsResponse.getStatusCodeValue() == 404)
                return postTo(LDS_SUBSET_API, "/" + id, subsetsJson);
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/v1/subsets/{id}")
    public ResponseEntity<String> getSubset(@PathVariable("id") String id) {
        if (Utils.isClean(id))
            return getFrom(LDS_SUBSET_API, "/"+id);
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @PutMapping(value = "/v1/subsets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> putSubset(@PathVariable("id") String id, @RequestBody JsonNode subsetJson) {

        if (Utils.isClean(id)) {
            ObjectMapper mapper = new ObjectMapper();
            ResponseEntity<String> responseEntity = getFrom(LDS_SUBSET_API, "/"+id);

            try {
                if (responseEntity.getStatusCodeValue() != 404){
                    JsonNode responseBodyJSON = mapper.readTree(responseEntity.getBody());
                    String currentAdminStatus = responseBodyJSON.get("administrativeStatus").textValue();
                    String currentVersion = responseBodyJSON.get("version").textValue();
                    String nextVersion = subsetJson.get("version").textValue();
                    if (currentVersion.compareTo(nextVersion) < 0 || !currentAdminStatus.equals("OPEN")){ // Do not overwrite a published patch.
                        return putTo(LDS_SUBSET_API, "/" + id, subsetJson);
                    }
                    return new ResponseEntity<>("trying to overwrite an already published patch of a subset", HttpStatus.BAD_REQUEST);
                } else {
                    return putTo(LDS_SUBSET_API, "/" + id, subsetJson);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        return new ResponseEntity<>("id contains illegal characters", HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/v1/subsets/{id}/versions")
    public ResponseEntity<JsonNode> getVersions(@PathVariable("id") String id) {
        ObjectMapper mapper = new ObjectMapper();
        if (Utils.isClean(id)){
            ResponseEntity<String> ldsRE = getFrom(LDS_SUBSET_API, "/"+id+"?timeline");
            ArrayNode arrayNode = mapper.createArrayNode();
            try {
                JsonNode responseBodyJSON = mapper.readTree(ldsRE.getBody());
                if (responseBodyJSON != null){
                    if (responseBodyJSON.isArray()) {
                        ArrayNode responseBodyArrayNode = (ArrayNode) responseBodyJSON;
                        Map<String, Boolean> versionMap = new HashMap<>(responseBodyArrayNode.size() * 2, 0.51f);
                        for (int i = 0; i < responseBodyArrayNode.size(); i++) {
                            JsonNode arrayEntry = responseBodyArrayNode.get(i).get("document");
                            String subsetVersion = arrayEntry.get("version").textValue();
                            if (!versionMap.containsKey(subsetVersion)){ // Only include the latest update of any patch
                                arrayNode.add(arrayEntry);
                                versionMap.put(subsetVersion, true);
                            }
                        }
                        return new ResponseEntity<>(arrayNode, HttpStatus.OK);
                    } else {
                        return new ResponseEntity<>(mapper.createObjectNode().put("error", "LDS response body was not JSON array"), HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                } else {
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                LOG.error(e.getMessage());
                return new ResponseEntity<>(mapper.createObjectNode().put("error", "JsonProcessingException on response body from LDS timeline api"), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            return new ResponseEntity<>(mapper.createObjectNode().put("error", "id contains illegal characters"), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Get a subset corresponding to a given version string.
     * If {version} is "1.0.1", then that version of the subset will be returned
     * If {version} is "1.0", then the latest patch of 1.0 will be returned.
     * If {version} is "1", then the latest patch of 1 will be returned.
     * @param id
     * @param version
     * @return
     */
    @GetMapping("/v1/subsets/{id}/versions/{version}")
    public ResponseEntity<JsonNode> getVersion(@PathVariable("id") String id, @PathVariable("version") String version) {
        ObjectMapper mapper = new ObjectMapper();
        if (Utils.isClean(id) && Utils.isVersion(version)){
            if (Utils.isVersion(version)){
                ResponseEntity<String> ldsRE = getFrom(LDS_SUBSET_API, "/"+id+"?timeline");
                try {
                    JsonNode responseBodyJSON = mapper.readTree(ldsRE.getBody());
                    if (responseBodyJSON != null){
                        if (responseBodyJSON.isArray()) {
                            ArrayNode responseBodyArrayNode = (ArrayNode) responseBodyJSON;
                            for (int i = 0; i < responseBodyArrayNode.size(); i++) {
                                JsonNode arrayEntry = responseBodyArrayNode.get(i).get("document");
                                String subsetVersion = arrayEntry.get("version").textValue();
                                if (subsetVersion.startsWith(version)){
                                    return new ResponseEntity<>(arrayEntry, HttpStatus.OK);
                                }
                            }
                        }
                    }
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
            return new ResponseEntity<>(mapper.createObjectNode().put("error", "malformed version"), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(mapper.createObjectNode().put("error", "id contains illegal characters"), HttpStatus.BAD_REQUEST);
    }

    /**
     * If no parameters are given, returns valid codes in last version.
     * If from and to parameters are given (DATES),
     * returns a list of codes that are present in all the versions in the interval.
     * In other words, returns the intersection of all the versions.
     * @param id
     * @param from
     * @param to
     * @return
     */
    @GetMapping("/v1/subsets/{id}/codes")
    public ResponseEntity<JsonNode> getSubsetCodes(@PathVariable("id") String id, @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        if (Utils.isClean(id)){
            if (from == null || to == null){
                LOG.debug("getting all codes of the latest/current version of subset "+id);
                ResponseEntity<String> ldsRE = getFrom(LDS_SUBSET_API, "/"+id);
                ObjectMapper mapper = new ObjectMapper();
                try {
                    JsonNode responseBodyJSON = mapper.readTree(ldsRE.getBody());
                    if (responseBodyJSON != null){
                        ArrayNode codes = (ArrayNode) responseBodyJSON.get("codes");
                        ArrayNode urnArray = mapper.createArrayNode();
                        for (int i = 0; i < codes.size(); i++) {
                            urnArray.add(codes.get(i).get("urn").textValue());
                        }
                        return new ResponseEntity<>(urnArray, HttpStatus.OK);
                    }
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            if (Utils.isYearMonthDay(from) && Utils.isYearMonthDay(to)) {
                // If a date interval is specified using 'from' and 'to' query parameters
                ResponseEntity<String> ldsRE = getFrom(LDS_SUBSET_API, "/" + id + "?timeline");
                LOG.debug(String.format("Getting valid codes of subset %s from date %s to date %s", id, from, to));
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Integer> codeMap = new HashMap<>();
                int nrOfVersions;
                try {
                    JsonNode responseBodyJSON = mapper.readTree(ldsRE.getBody());
                    if (responseBodyJSON != null) {
                        if (responseBodyJSON.isArray()) {
                            ArrayNode versionsArrayNode = (ArrayNode) responseBodyJSON;
                            nrOfVersions = versionsArrayNode.size();
                            LOG.debug("Nr of versions: " + nrOfVersions);
                            JsonNode firstVersion = versionsArrayNode.get(versionsArrayNode.size() - 1).get("document");
                            JsonNode lastVersion = versionsArrayNode.get(0).get("document");
                            ArrayNode intersectionValidCodesInIntervalArrayNode = mapper.createArrayNode();
                            // Check if first version includes fromDate, and last version includes toDate. If not, then return an empty list.
                            String firstVersionValidFromString = firstVersion.get("validFrom").textValue().split("T")[0];
                            String lastVersionValidUntilString = lastVersion.get("validUntil").textValue().split("T")[0];
                            LOG.debug("First version valid from: " + firstVersionValidFromString);
                            LOG.debug("Last version valid until: " + lastVersionValidUntilString);
                            boolean isFirstValidAtOrBeforeFromDate = firstVersionValidFromString.compareTo(from) <= 0;
                            LOG.debug("isFirstValidAtOrBeforeFromDate? " + isFirstValidAtOrBeforeFromDate);
                            boolean isLastValidAtOrAfterToDate = lastVersionValidUntilString.compareTo(to) >= 0;
                            LOG.debug("isLastValidAtOrAfterToDate? " + isLastValidAtOrAfterToDate);
                            if (isFirstValidAtOrBeforeFromDate && isLastValidAtOrAfterToDate) {
                                for (int i = 0; i < versionsArrayNode.size(); i++) {
                                    // if this version has any overlap with the valid interval . . .
                                    JsonNode arrayEntry = versionsArrayNode.get(i);
                                    JsonNode subset = arrayEntry.get("document");
                                    String validFromDateString = subset.get("validFrom").textValue().split("T")[0];
                                    String validUntilDateString = subset.get("validUntil").textValue().split("T")[0];
                                    if (validUntilDateString.compareTo(from) > 0 || validFromDateString.compareTo(to) < 0) {
                                        LOG.debug("Version " + subset.get("version") + " is valid in the interval, so codes will be added to map");
                                        // . . . using each code in this version as key, increment corresponding integer value in map
                                        JsonNode codes = arrayEntry.get("document").get("codes");
                                        ArrayNode codesArrayNode = (ArrayNode) codes;
                                        LOG.debug("There are " + codesArrayNode.size() + " codes in this version");
                                        for (int i1 = 0; i1 < codesArrayNode.size(); i1++) {
                                            String codeURN = codesArrayNode.get(i1).get("urn").textValue();
                                            codeMap.merge(codeURN, 1, Integer::sum);
                                        }
                                    }
                                }
                                // Only return codes that were in every version in the interval, (=> they were always valid)
                                for (String key : codeMap.keySet()) {
                                    int value = codeMap.get(key);
                                    LOG.trace("key:" + key + " value:" + value);
                                    if (value == nrOfVersions)
                                        intersectionValidCodesInIntervalArrayNode.add(key);
                                }
                            }
                            LOG.debug("nr of valid codes: " + intersectionValidCodesInIntervalArrayNode.size());
                            return new ResponseEntity<>(intersectionValidCodesInIntervalArrayNode, HttpStatus.OK);
                        }
                    }
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    /**
     * Returns all codes of the subset version that is valid on the given date.
     * Assumes only one subset is valid on any given date, with no overlap of start/end date.
     * @param id
     * @param date
     * @return
     */
    @GetMapping("/v1/subsets/{id}/codesAt")
    public ResponseEntity<JsonNode> getSubsetCodesAt(@PathVariable("id") String id, @RequestParam String date) {
        if (date != null && Utils.isClean(id) && (Utils.isYearMonthDay(date))){
            ResponseEntity<String> ldsRE = getFrom(LDS_SUBSET_API, "/"+id+"?timeline");
            ObjectMapper mapper = new ObjectMapper();
            try {
                JsonNode responseBodyJSON = mapper.readTree(ldsRE.getBody());
                if (responseBodyJSON != null){
                    if (responseBodyJSON.isArray()) {
                        ArrayNode arrayNode = (ArrayNode) responseBodyJSON;
                        for (int i = 0; i < arrayNode.size(); i++) {
                            JsonNode version = arrayNode.get(i).get("document");
                            String entryValidFrom = version.get("validFrom").textValue();
                            String entryValidUntil = version.get("validUntil").textValue();
                            if (entryValidFrom.compareTo(date) <= 0 && entryValidUntil.compareTo(date) >= 0 ){
                                return new ResponseEntity<>(version.get("codes"), HttpStatus.OK);
                            }
                        }
                        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                    }
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/v1/subsets?schema")
    public ResponseEntity<String> getSchema(){
        return getFrom(LDS_SUBSET_API,"/?schema");
    }

    @GetMapping("/v1/classifications")
    public ResponseEntity<String> getClassifications(){
        return getFrom(KLASS_CLASSIFICATIONS_API, ".json");
    }

    @GetMapping("/v1/classifications/{id}")
    public ResponseEntity<String> getClassification(@PathVariable("id") String id){
        if (Utils.isClean(id))
            return getFrom(KLASS_CLASSIFICATIONS_API, "/"+id+".json");
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    static ResponseEntity<String> getFrom(String apiBase, String additional)
    {
        // TODO: I am not sure if this is the right way of handling 404's from another server.
        try {
            ResponseEntity<String> response = new RestTemplate().getForEntity(apiBase + additional, String.class);
            return response;
        } catch (HttpClientErrorException e){
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    static ResponseEntity<String> postTo(String apiBase, String additional, JsonNode json){
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<JsonNode> request = new HttpEntity<>(json, headers);
        ResponseEntity<String> response = new RestTemplate().postForEntity(apiBase+additional, request, String.class);
        LOG.debug("POST to "+apiBase+additional+" - Status: "+response.getStatusCodeValue()+" "+response.getStatusCode().name());
        return response;
    }

    static ResponseEntity<String> putTo(String apiBase, String additional, JsonNode json){
        return postTo(apiBase, additional, json);
    }
}
