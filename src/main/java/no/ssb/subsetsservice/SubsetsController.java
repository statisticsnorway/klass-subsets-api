package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@CrossOrigin
@RestController
public class SubsetsController {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsController.class);

    static final String LDS_PROD = "http://lds-klass.klass.svc.cluster.local/ns/ClassificationSubset";
    static final String LDS_LOCAL = "http://localhost:9090/ns/ClassificationSubset";
    private static String LDS_SUBSET_API = "";

    private static String KLASS_CLASSIFICATIONS_API = "https://data.ssb.no/api/klass/v1/classifications";

    private static final boolean prod = false;

    public SubsetsController(){
        updateLDSURL();
    }

    private void updateLDSURL(){
        if (prod){
            LDS_SUBSET_API = System.getenv().getOrDefault("API_LDS", LDS_PROD);
        } else {
            LDS_SUBSET_API = LDS_LOCAL;
        }
        LOG.info("Running with LDS url "+LDS_SUBSET_API);
        KLASS_CLASSIFICATIONS_API = System.getenv().getOrDefault("API_KLASS", KLASS_CLASSIFICATIONS_API);
    }

    @GetMapping("/v1/subsets")
    public ResponseEntity<JsonNode> getSubsets() {
        LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
        ResponseEntity<JsonNode> ldsRE = consumer.getFrom("");
        ArrayNode ldsAllSubsetsArrayNode = (ArrayNode) ldsRE.getBody();
        for (int i = 0; i < ldsAllSubsetsArrayNode.size(); i++) {
            ldsAllSubsetsArrayNode.set(i, getVersions(ldsAllSubsetsArrayNode.get(i).get("id").asText()).getBody().get(0));
        }
        return new ResponseEntity<>(ldsAllSubsetsArrayNode, HttpStatus.OK);
    }

    /**
     * This method figures out what the 'id' of the subset is from the value inside the JSON
     * and then post to subsets/{id}
     * @param subsetJson
     * @return
     */
    @PostMapping("/v1/subsets")
    public ResponseEntity<JsonNode> postSubset(@RequestBody JsonNode subsetJson) {
        if (subsetJson != null) {
            JsonNode idJN = subsetJson.get("id");
            String id = idJN.textValue();
            ObjectNode editableSubset = subsetJson.deepCopy();
            String isoNow = Utils.getNowISO();
            editableSubset.put("lastUpdatedDate", isoNow);
            editableSubset.put("createdDate", isoNow);
            LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
            ResponseEntity<JsonNode> ldsResponse = consumer.getFrom("/"+id);
            if (ldsResponse.getStatusCodeValue() == 404)
                return consumer.postTo("/" + id, Utils.cleanSubsetVersion(subsetJson));
        }
        return ErrorHandler.newHttpError("Can not POST subset with id that is already in use. Use PUT to update existing subsets", HttpStatus.BAD_REQUEST, LOG);
    }

    @GetMapping("/v1/subsets/{id}")
    public ResponseEntity<JsonNode> getSubset(@PathVariable("id") String id) {
        if (Utils.isClean(id)){
            ResponseEntity<JsonNode> majorVersions = getVersions(id);
            if (majorVersions.getStatusCodeValue() != 200)
                return majorVersions;
            else if (majorVersions.getBody().isArray()){
                ArrayNode majorVersionsArray = (ArrayNode) majorVersions.getBody();
                return new ResponseEntity<>(majorVersionsArray.get(0), HttpStatus.OK);
            } else {
                return ErrorHandler.newHttpError("internal call to /versions did not return array", HttpStatus.INTERNAL_SERVER_ERROR, LOG);
            }
        }
        return ErrorHandler.illegalID(LOG);
    }

    @PutMapping(value = "/v1/subsets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> putSubset(@PathVariable("id") String id, @RequestBody JsonNode subsetJson) {

        if (Utils.isClean(id)) {
            ObjectNode editableSubset = subsetJson.deepCopy();
            editableSubset.put("lastUpdatedDate", Utils.getNowISO());
            LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
            return consumer.putTo("/" + id, editableSubset);
        } else {
            return ErrorHandler.illegalID(LOG);
        }
    }

    @GetMapping("/v1/subsets/{id}/versions")
    public ResponseEntity<JsonNode> getVersions(@PathVariable("id") String id) {
        ObjectMapper mapper = new ObjectMapper();
        if (Utils.isClean(id)){
            LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
            ResponseEntity<JsonNode> ldsRE = consumer.getFrom("/"+id+"?timeline");
            if (ldsRE.getStatusCodeValue() != 200){
                return ldsRE;
            }
            JsonNode responseBodyJSON = ldsRE.getBody();
            if (responseBodyJSON != null){
                if (!responseBodyJSON.has(0)){
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
                if (responseBodyJSON.isArray()) {
                    ArrayNode versionsArrayNode = (ArrayNode) responseBodyJSON;
                    Map<Integer, JsonNode> versionLastUpdatedMap = new HashMap<>(versionsArrayNode.size() * 2, 0.51f);
                    for (JsonNode versionNode : versionsArrayNode) {
                        ObjectNode subsetVersionDocument = versionNode.get("document").deepCopy();
                        JsonNode self = Utils.getSelfLinkObject(mapper, ServletUriComponentsBuilder.fromCurrentRequestUri(), subsetVersionDocument);
                        subsetVersionDocument.set("_links", self);
                        int subsetMajorVersion = Integer.parseInt(subsetVersionDocument.get("version").textValue().split("\\.")[0]);
                        String lastUpdatedDate = subsetVersionDocument.get("lastUpdatedDate").textValue();
                        if (!versionLastUpdatedMap.containsKey(subsetMajorVersion)){ // Only include the latest update of any major version
                            versionLastUpdatedMap.put(subsetMajorVersion, subsetVersionDocument);
                        } else if (versionLastUpdatedMap.get(subsetMajorVersion).get("lastUpdatedDate").textValue().compareTo(lastUpdatedDate) < 0) {
                            versionLastUpdatedMap.put(subsetMajorVersion, subsetVersionDocument);
                        }
                    }
                    Set<Integer> keySet = versionLastUpdatedMap.keySet();
                    Integer[] keyArray = keySet.toArray(new Integer[keySet.size()]);
                    Arrays.sort(keyArray);
                    ArrayNode majorVersionsArrayNode = mapper.createArrayNode();
                    for (int i = keyArray.length - 1; i >= 0; i--) {
                        majorVersionsArrayNode.add(versionLastUpdatedMap.get(keyArray[i]));
                    }
                    JsonNode latestPublishedVersionNode = Utils.getLatestMajorVersion(majorVersionsArrayNode, true);
                    int latestPublishedVersion = Integer.parseInt(latestPublishedVersionNode.get("version").asText().split("\\.")[0]);

                    ArrayNode majorVersionsObjectNodeArray = mapper.createArrayNode();
                    for (JsonNode versionNode : majorVersionsArrayNode) {
                        ObjectNode objectNode = versionNode.deepCopy();
                        int version = Integer.parseInt(objectNode.get("version").asText().split("\\.")[0]);
                        objectNode.put("version", version);
                        if (version < latestPublishedVersion && objectNode.get("administrativeStatus").asText().equals("OPEN")){
                            if (latestPublishedVersionNode.has("name")){
                                objectNode.set("name", latestPublishedVersionNode.get("name"));
                            }
                            if (latestPublishedVersionNode.has("shortName")){
                                objectNode.set("shortName", latestPublishedVersionNode.get("shortName"));
                            }
                        }
                        majorVersionsObjectNodeArray.add(objectNode);
                    }
                    return new ResponseEntity<>(majorVersionsObjectNodeArray, HttpStatus.OK);
                } else {
                    return ErrorHandler.newHttpError("LDS response body was not JSON array", HttpStatus.INTERNAL_SERVER_ERROR, LOG);
                }
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } else {
            return ErrorHandler.illegalID(LOG);
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
        if (Utils.isClean(id) && Utils.isVersion(version)){
            if (Utils.isVersion(version)){
                ResponseEntity<JsonNode> versionsRE = getVersions(id);
                JsonNode responseBodyJSON = versionsRE.getBody();
                if (responseBodyJSON != null){
                    if (responseBodyJSON.isArray()) {
                        ArrayNode versionsArrayNode = (ArrayNode) responseBodyJSON;
                        for (JsonNode arrayEntry : versionsArrayNode) {
                            String subsetVersion = arrayEntry.get("version").asText();
                            if (subsetVersion.startsWith(version)){
                                return new ResponseEntity<>(arrayEntry, HttpStatus.OK);
                            }
                        }
                    }
                }
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            return ErrorHandler.malformedVersion(LOG);
        }
        return ErrorHandler.illegalID(LOG);
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
                    for (JsonNode code : codes) {
                        urnArray.add(code.get("urn").textValue());
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
                            for (JsonNode arrayEntry : versionsArrayNode) {
                                // if this version has any overlap with the valid interval . . .
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
                                    for (JsonNode jsonNode : codesArrayNode) {
                                        String codeURN = jsonNode.get("urn").asText();
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
                    for (JsonNode jsonNode : arrayNode) {
                        JsonNode version = jsonNode.get("document");
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

}
