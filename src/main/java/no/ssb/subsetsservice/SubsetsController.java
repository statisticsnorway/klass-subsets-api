package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin
@RestController
public class SubsetsController {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsController.class);

    static final String LDS_PROD = "http://lds-klass.klass.svc.cluster.local/ns/ClassificationSubset";
    static final String LDS_LOCAL = "http://localhost:9090/ns/ClassificationSubset";
    private static String LDS_SUBSET_API = "";

    private static String KLASS_CLASSIFICATIONS_API = "https://data.ssb.no/api/klass/v1/classifications";

    private static final boolean prod = true;

    public SubsetsController(){
        updateLDSURL();
    }

    private void updateLDSURL(){
        if (prod){
            LDS_SUBSET_API = System.getenv().getOrDefault("API_LDS", LDS_PROD);
        } else {
            LDS_SUBSET_API = LDS_LOCAL;
        }
        KLASS_CLASSIFICATIONS_API = System.getenv().getOrDefault("API_KLASS", KLASS_CLASSIFICATIONS_API);
    }

    @GetMapping("/v1/subsets")
    public ResponseEntity<JsonNode> getSubsets() {
        LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
        return consumer.getFrom("");
    }

    /**
     * This method figures out what the 'id' of the subset is from the value inside the JSON
     * and then post to subsets/{id}
     * @param subsetsJson
     * @return
     */
    @PostMapping("/v1/subsets")
    public ResponseEntity<JsonNode> postSubset(@RequestBody JsonNode subsetsJson) {
        ObjectMapper mapper = new ObjectMapper();
        if (subsetsJson != null) {
            JsonNode idJN = subsetsJson.get("id");
            String id = idJN.textValue();
            LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
            ResponseEntity<JsonNode> ldsResponse = consumer.getFrom("/"+id);
            if (ldsResponse.getStatusCodeValue() == 404)
                return consumer.postTo("/" + id, subsetsJson);
        }
        return new ResponseEntity<>(mapper.createObjectNode().put("error","Can not POST subset with id that is already in use. Use PUT to update existing subsets"), HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/v1/subsets/{id}")
    public ResponseEntity<JsonNode> getSubset(@PathVariable("id") String id) {
        if (Utils.isClean(id)){
            LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
            return consumer.getFrom("/"+id);
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @PutMapping(value = "/v1/subsets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> putSubset(@PathVariable("id") String id, @RequestBody JsonNode subsetJson) {

        ObjectMapper mapper = new ObjectMapper();
        if (!Utils.isClean(id)) {
            return new ResponseEntity<>(mapper.createObjectNode().put("error","id contains illegal characters"), HttpStatus.BAD_REQUEST);
        }
        LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
        return consumer.putTo("/" + id, subsetJson);
    }

    @GetMapping("/v1/subsets/{id}/versions")
    public ResponseEntity<JsonNode> getVersions(@PathVariable("id") String id) {
        ObjectMapper mapper = new ObjectMapper();
        if (Utils.isClean(id)){
            LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
            ResponseEntity<JsonNode> ldsRE = consumer.getFrom("/"+id+"?timeline");
            ArrayNode arrayNode = mapper.createArrayNode();
            JsonNode responseBodyJSON = ldsRE.getBody();
            if (responseBodyJSON != null){
                if (responseBodyJSON.isArray()) {
                    ArrayNode responseBodyArrayNode = (ArrayNode) responseBodyJSON;
                    Map<String, Boolean> versionMap = new HashMap<>(responseBodyArrayNode.size() * 2, 0.51f);
                    for (int i = 0; i < responseBodyArrayNode.size(); i++) {
                        ObjectNode arrayEntry = (ObjectNode) responseBodyArrayNode.get(i).get("document");
                        JsonNode self = Utils.getSelfLinkObject(mapper, ServletUriComponentsBuilder.fromCurrentRequestUri(), arrayEntry);
                        arrayEntry.set("_links", self);
                        String subsetVersion = arrayEntry.get("version").textValue().split("\\.")[0];
                        if (!versionMap.containsKey(subsetVersion)){ // Only include the latest update of any major version
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
                LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
                ResponseEntity<JsonNode> ldsRE = consumer.getFrom("/"+id+"?timeline");
                JsonNode responseBodyJSON = ldsRE.getBody();
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
        LOG.debug("GET subsets/id/codes");
        if (Utils.isClean(id)){
            if (from == null && to == null){
                LOG.debug("getting all codes of the latest/current version of subset "+id);
                ResponseEntity<JsonNode> subsetResponseEntity = getSubset(id);
                JsonNode responseBodyJSON = subsetResponseEntity.getBody();
                if (responseBodyJSON != null){
                    ArrayNode codes = (ArrayNode) responseBodyJSON.get("codes");
                    ObjectMapper mapper = new ObjectMapper();
                    ArrayNode urnArray = mapper.createArrayNode();
                    for (int i = 0; i < codes.size(); i++) {
                        urnArray.add(codes.get(i).get("urn").textValue());
                    }
                    return new ResponseEntity<>(urnArray, HttpStatus.OK);
                }
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            boolean isFromDate = from != null;
            boolean isToDate = to != null;
            if ((!isFromDate || Utils.isYearMonthDay(from)) && (!isToDate || Utils.isYearMonthDay(to))){ // If a date is given as param, it must be valid format
                // If a date interval is specified using 'from' and 'to' query parameters
                ResponseEntity<JsonNode> versionsResponseEntity = getVersions(id);
                JsonNode responseBodyJSON = versionsResponseEntity.getBody();
                LOG.debug(String.format("Getting valid codes of subset %s from date %s to date %s", id, from, to));
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Integer> codeMap = new HashMap<>();
                int nrOfVersions;
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

                        boolean isFirstValidAtOrBeforeFromDate = true; // If no "from" date is given, version is automatically valid at or before "from" date
                        if (isFromDate)
                            isFirstValidAtOrBeforeFromDate = firstVersionValidFromString.compareTo(from) <= 0;
                        LOG.debug("isFirstValidAtOrBeforeFromDate? " + isFirstValidAtOrBeforeFromDate);

                        boolean isLastValidAtOrAfterToDate = true; // If no "to" date is given, it is automatically valid at or after "to" date
                        if (isToDate)
                            isLastValidAtOrAfterToDate = lastVersionValidUntilString.compareTo(to) >= 0;
                        LOG.debug("isLastValidAtOrAfterToDate? " + isLastValidAtOrAfterToDate);

                        if (isFirstValidAtOrBeforeFromDate && isLastValidAtOrAfterToDate) {
                            for (int i = 0; i < versionsArrayNode.size(); i++) {
                                // if this version has any overlap with the valid interval . . .
                                JsonNode arrayEntry = versionsArrayNode.get(i);
                                JsonNode subset = arrayEntry.get("document");
                                String validFromDateString = subset.get("validFrom").textValue().split("T")[0];
                                String validUntilDateString = subset.get("validUntil").textValue().split("T")[0];

                                boolean validUntilGTFrom = true;
                                if (isFromDate)
                                    validUntilGTFrom = validUntilDateString.compareTo(from) > 0;

                                boolean validFromLTTo = true;
                                if (isToDate)
                                    validFromLTTo = validFromDateString.compareTo(to) < 0;

                                if (validUntilGTFrom || validFromLTTo) {
                                    LOG.debug("Version " + subset.get("version") + " is valid in the interval, so codes will be added to map");
                                    // . . . using each code in this version as key, increment corresponding integer value in map
                                    JsonNode codes = arrayEntry.get("document").get("codes");
                                    ArrayNode codesArrayNode = (ArrayNode) codes;
                                    LOG.debug("There are " + codesArrayNode.size() + " codes in this version");
                                    for (int i1 = 0; i1 < codesArrayNode.size(); i1++) {
                                        String codeURN = codesArrayNode.get(i1).get("urn").asText();
                                        codeMap.merge(codeURN, 1, Integer::sum);
                                    }
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
                        
                        LOG.debug("nr of valid codes: " + intersectionValidCodesInIntervalArrayNode.size());
                        return new ResponseEntity<>(intersectionValidCodesInIntervalArrayNode, HttpStatus.OK);
                    }
                }
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
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
        LOG.debug("GET subsets/id/codesAt");
        if (date != null && Utils.isClean(id) && (Utils.isYearMonthDay(date))){
            ResponseEntity<JsonNode> versionsResponseEntity = getVersions(id);
            JsonNode responseBodyJSON = versionsResponseEntity.getBody();
            if (responseBodyJSON != null){
                if (responseBodyJSON.isArray()) {
                    ArrayNode arrayNode = (ArrayNode) responseBodyJSON;
                    for (int i = 0; i < arrayNode.size(); i++) {
                        JsonNode version = arrayNode.get(i).get("document");
                        String entryValidFrom = version.get("validFrom").textValue();
                        String entryValidUntil = version.get("validUntil").textValue();
                        if (entryValidFrom.compareTo(date) <= 0 && entryValidUntil.compareTo(date) >= 0 ){
                            LOG.debug("Found valid codes at "+date+". "+version.get("codes").size());
                            ObjectMapper mapper = new ObjectMapper();
                            ArrayNode codeArray = mapper.createArrayNode();
                            version.get("codes").forEach(e -> codeArray.add(e.get("urn")));
                            return new ResponseEntity<>(codeArray, HttpStatus.OK);
                        }
                    }
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
            }
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/v1/subsets/schema")
    public ResponseEntity<JsonNode> getSchema(){
        LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
        return consumer.getFrom("/?schema");
    }

    @GetMapping("/v1/classifications")
    public ResponseEntity<JsonNode> getClassifications(){
        LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
        return consumer.getFrom(".json");
    }

    @GetMapping("/v1/classifications/{id}")
    public ResponseEntity<JsonNode> getClassification(@PathVariable("id") String id){
        if (Utils.isClean(id)) {
            LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
            return consumer.getFrom("/" + id + ".json");
        }
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

}
