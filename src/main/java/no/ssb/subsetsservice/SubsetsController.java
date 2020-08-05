package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;

@CrossOrigin
@RestController
public class SubsetsController {

    private MetricsService metricsService;

    private static SubsetsController instance;
    private static final Logger LOG = LoggerFactory.getLogger(SubsetsController.class);

    static final String LDS_PROD = "http://lds-klass.klass.svc.cluster.local/ns/ClassificationSubset";
    static final String LDS_LOCAL = "http://localhost:9090/ns/ClassificationSubset";
    private static String LDS_SUBSET_API = "";

    private static String KLASS_CLASSIFICATIONS_API = "https://data.ssb.no/api/klass/v1/classifications";

    private static final boolean prod = true;

    @Autowired
    public SubsetsController(MetricsService metricsService){
        this.metricsService = metricsService;
        instance = this;
        updateLDSURL();
    }

    public static SubsetsController getInstance(){
        return instance;
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
        metricsService.incrementGETCounter();

        LOG.info("GET subsets");
        LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
        ResponseEntity<JsonNode> ldsRE = consumer.getFrom("");

        JsonNode ldsREBody = ldsRE.getBody();
        if (ldsRE.getStatusCodeValue() != HttpStatus.OK.value()){
            return ErrorHandler.newHttpError("LDS returned a "+ldsRE.getStatusCode().toString(), HttpStatus.INTERNAL_SERVER_ERROR, LOG);
        } else {
            if(ldsREBody != null && ldsREBody.isArray()){
                ArrayNode ldsAllSubsetsArrayNode = (ArrayNode) ldsREBody;
                for (int i = 0; i < ldsAllSubsetsArrayNode.size(); i++) {
                    ldsAllSubsetsArrayNode.set(i, getVersions(ldsAllSubsetsArrayNode.get(i).get("id").asText()).getBody().get(0));
                }
                return new ResponseEntity<>(ldsAllSubsetsArrayNode, HttpStatus.OK);
            }
            return ErrorHandler.newHttpError("LDS returned OK, but a non-array body. This was unexpected.", HttpStatus.INTERNAL_SERVER_ERROR, LOG);
        }
    }

    /**
     * This method figures out what the 'id' of the subset is from the value inside the JSON
     * and then post to subsets/{id}
     * @param subsetJson
     * @return
     */
    @PostMapping("/v1/subsets")
    public ResponseEntity<JsonNode> postSubset(@RequestBody JsonNode subsetJson) {
        metricsService.incrementPOSTCounter();

        if (subsetJson != null) {
            JsonNode idJN = subsetJson.get("id");
            String id = idJN.textValue();
            if (Utils.isClean(id)){
                LOG.info("POST subset with id "+id);
                LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
                ResponseEntity<JsonNode> ldsResponse = consumer.getFrom("/"+id);
                if (ldsResponse.getStatusCodeValue() == HttpStatus.NOT_FOUND.value()){
                    ObjectNode editableSubset = subsetJson.deepCopy();
                    String isoNow = Utils.getNowISO();
                    editableSubset.put("lastUpdatedDate", isoNow);
                    editableSubset.put("createdDate", isoNow);
                    JsonNode cleanSubset = Utils.cleanSubsetVersion(editableSubset);
                    return consumer.postTo("/" + id, cleanSubset);
                }
                return ErrorHandler.newHttpError("POST: Can not create subset. ID already in use", HttpStatus.BAD_REQUEST, LOG);
            }
            return ErrorHandler.illegalID(LOG);
        }
        return ErrorHandler.newHttpError("POST: Can not create subset from empty body", HttpStatus.BAD_REQUEST, LOG);
    }

    @GetMapping("/v1/subsets/{id}")
    public ResponseEntity<JsonNode> getSubset(@PathVariable("id") String id, @RequestParam(defaultValue = "false") boolean publishedOnly) {
        metricsService.incrementGETCounter();
        LOG.info("GET subset with id "+id);

        if (Utils.isClean(id)){
            ResponseEntity<JsonNode> majorVersions = getVersions(id);
            if (majorVersions.getStatusCodeValue() != HttpStatus.OK.value())
                return majorVersions;
            JsonNode majorVersionsBody = majorVersions.getBody();
            if (majorVersionsBody != null && majorVersionsBody.isArray()){
                ArrayNode majorVersionsArray = (ArrayNode) majorVersionsBody;
                for (JsonNode version : majorVersionsArray) {
                    if (!publishedOnly || version.get("administrativeStatus").asText().equals("OPEN")){
                        return new ResponseEntity<>(majorVersionsArray.get(0), HttpStatus.OK);
                    }
                }
            } else {
                return ErrorHandler.newHttpError("internal call to /versions did not return array", HttpStatus.INTERNAL_SERVER_ERROR, LOG);
            }
        }
        return ErrorHandler.illegalID(LOG);
    }

    @PutMapping(value = "/v1/subsets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> putSubset(@PathVariable("id") String id, @RequestBody JsonNode subsetJson) {
        metricsService.incrementPUTCounter();
        LOG.info("PUT subset with id "+id);

        if (Utils.isClean(id)) {
            ObjectNode editableSubset = Utils.cleanSubsetVersion(subsetJson).deepCopy();
            editableSubset.put("lastUpdatedDate", Utils.getNowISO());
            ResponseEntity<JsonNode> mostRecentVersionRE = getSubset(id, false);
            ResponseEntity<JsonNode> oldVersionsRE = getVersions(id);
            JsonNode mostRecentSubset = mostRecentVersionRE.getBody();
            if (mostRecentVersionRE.getStatusCodeValue() == HttpStatus.OK.value()){
                assert mostRecentSubset != null && mostRecentSubset.has("id") : "no old subset with this id was found in body of response entity";

                String oldID = mostRecentSubset.get("id").asText();
                String newID = subsetJson.get("id").asText();
                boolean sameID = oldID.equals(newID);
                boolean sameIDAsRequest = newID.equals(id);
                boolean consistentID = oldID.equals(newID) && newID.equals(id);

                JsonNode oldCodeList = mostRecentSubset.get("codes");
                JsonNode newCodeList = subsetJson.get("codes");
                boolean sameCodeList = oldCodeList.toString().equals(newCodeList.toString());

                String newVersionValidFrom = subsetJson.get("versionValidFrom").asText();
                ArrayNode versionsArrayNode = Utils.cleanSubsetVersion(oldVersionsRE.getBody()).deepCopy();

                String newVersionString = subsetJson.get("version").asText();

                boolean sameVersionValidFrom = false;
                for (JsonNode jsonNode : versionsArrayNode) {
                    if (!jsonNode.get("version").asText().equals(newVersionString) && jsonNode.get("versionValidFrom").asText().equals(newVersionValidFrom)) {
                        sameVersionValidFrom = true;
                        break;
                    }
                }

                ResponseEntity<JsonNode> prevPatchOfThisVersionRE = getVersion(id, newVersionString);
                boolean thisVersionExistsFromBefore = prevPatchOfThisVersionRE.getStatusCodeValue() == 200;
                boolean thisVersionIsPublishedFromBefore = thisVersionExistsFromBefore && prevPatchOfThisVersionRE.getBody().get("administrativeStatus").asText().equals("OPEN");

                boolean attemptToChangeCodesOfPublishedVersion = thisVersionIsPublishedFromBefore && !sameCodeList;
                if (consistentID && !attemptToChangeCodesOfPublishedVersion && !sameVersionValidFrom){
                    LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
                    return consumer.putTo("/" + id, editableSubset);
                } else {
                    StringBuilder errorStringBuilder = new StringBuilder();
                    if (!sameID)
                        errorStringBuilder.append("ID of submitted subset (").append(newID).append(") was not same as id of stored subset(").append(oldID).append("). ");
                    if(!sameIDAsRequest)
                        errorStringBuilder.append("ID of submitted subset(").append(newID).append(") was not the same as id in request param (").append(id).append("). ");
                    if (attemptToChangeCodesOfPublishedVersion)
                        errorStringBuilder.append("No changes are allowed in the code list of a published version. ");
                    if (sameVersionValidFrom)
                        errorStringBuilder.append("A new version was created with a versionValidFrom equal to that of another version. ");
                    return ErrorHandler.newHttpError(errorStringBuilder.toString(), HttpStatus.BAD_REQUEST, LOG);
                }
            } else {
                return ErrorHandler.newHttpError(mostRecentVersionRE.toString(), mostRecentVersionRE.getStatusCode(), LOG);
            }
        } else {
            return ErrorHandler.illegalID(LOG);
        }
    }

    @GetMapping("/v1/subsets/{id}/versions")
    public ResponseEntity<JsonNode> getVersions(@PathVariable("id") String id) {
        metricsService.incrementGETCounter();
        LOG.info("GET all versions of subset with id "+id);

        ObjectMapper mapper = new ObjectMapper();
        if (Utils.isClean(id)){
            LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
            ResponseEntity<JsonNode> ldsRE = consumer.getFrom("/"+id+"?timeline");
            if (ldsRE.getStatusCodeValue() != HttpStatus.OK.value()){
                return ldsRE;
            }
            JsonNode responseBodyJSON = ldsRE.getBody();
            if (responseBodyJSON != null){
                if (!responseBodyJSON.has(0)){
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
                if (responseBodyJSON.isArray()) {
                    ArrayNode timelineArrayNode = (ArrayNode) responseBodyJSON;
                    Map<Integer, JsonNode> versionLastUpdatedMap = new HashMap<>(timelineArrayNode.size() * 2, 0.51f);
                    for (JsonNode versionNode : timelineArrayNode) {
                        ObjectNode subsetVersionDocument = versionNode.get("document").deepCopy();
                        if (!subsetVersionDocument.isEmpty()){
                            JsonNode self = Utils.getSelfLinkObject(mapper, ServletUriComponentsBuilder.fromCurrentRequestUri(), subsetVersionDocument);
                            subsetVersionDocument.set("_links", self);
                            int subsetMajorVersion = Integer.parseInt(subsetVersionDocument.get("version").textValue().split("\\.")[0]);
                            if (!versionLastUpdatedMap.containsKey(subsetMajorVersion)){ // Only include the latest update of any major version
                                versionLastUpdatedMap.put(subsetMajorVersion, subsetVersionDocument);
                            } else {
                                if (!subsetVersionDocument.has("lastUpdatedDate")){
                                    subsetVersionDocument.set("lastUpdatedDate", subsetVersionDocument.get("createdDate"));
                                }
                                ObjectNode versionStoredInMap = versionLastUpdatedMap.get(subsetMajorVersion).deepCopy();
                                if (!versionStoredInMap.has("lastUpdatedDate")){
                                    versionStoredInMap.set("lastUpdatedDate",versionStoredInMap.get("createdDate"));
                                    versionLastUpdatedMap.put(subsetMajorVersion, versionStoredInMap);
                                }
                                String lastUpdatedDate = subsetVersionDocument.get("lastUpdatedDate").textValue();
                                if (versionLastUpdatedMap.get(subsetMajorVersion).get("lastUpdatedDate").textValue().compareTo(lastUpdatedDate) < 0) {
                                    versionLastUpdatedMap.put(subsetMajorVersion, subsetVersionDocument);
                                }
                            }
                        }
                    }

                    ArrayList<JsonNode> versionList = new ArrayList<>(versionLastUpdatedMap.size());
                    versionLastUpdatedMap.forEach((key, value) -> versionList.add(value));
                    versionList.sort(Comparator.comparing(v -> v.get("versionValidFrom").asText()));

                    ArrayNode majorVersionsArrayNode = mapper.createArrayNode();
                    versionList.forEach(majorVersionsArrayNode::add);

                    JsonNode latestVersion = Utils.getLatestMajorVersion(majorVersionsArrayNode, false);
                    JsonNode latestPublishedVersionNode = Utils.getLatestMajorVersion(majorVersionsArrayNode, true);
                    boolean publishedVersionExists = latestPublishedVersionNode != null;
                    int latestMajorVersion = Integer.parseInt(latestVersion.get("version").asText().split("\\.")[0]);

                    ArrayNode majorVersionsObjectNodeArray = mapper.createArrayNode();
                    for (JsonNode versionNode : majorVersionsArrayNode) {
                        ObjectNode objectNode = versionNode.deepCopy();
                        int version = Integer.parseInt(objectNode.get("version").asText().split("\\.")[0]);
                        objectNode.put("version", version);
                        if (publishedVersionExists && version < latestMajorVersion && objectNode.get("administrativeStatus").asText().equals("OPEN")){
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
        metricsService.incrementGETCounter();
        LOG.info("GET version "+version+" of subset with id "+id);

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
    public ResponseEntity<JsonNode> getSubsetCodes(@PathVariable("id") String id, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(defaultValue = "false") boolean publishedOnly) {
        LOG.info("GET codes of subset with id "+id);
        metricsService.incrementGETCounter();

        if (Utils.isClean(id)){
            if (from == null && to == null){
                LOG.debug("getting all codes of the latest/current version of subset "+id);
                ResponseEntity<JsonNode> subsetResponseEntity = getSubset(id, publishedOnly);
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
                                if (!publishedOnly || arrayEntry.get("administrativeStatus").asText().equals("OPEN")){
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
        metricsService.incrementGETCounter();
        LOG.info("GET codes valid at date "+date+" for subset with id "+id);

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
                    return ErrorHandler.newHttpError("Subset w id "+id+" not found", HttpStatus.NOT_FOUND, LOG);
                }
            }
        }
        StringBuilder stringBuilder = new StringBuilder();
        if (date == null){
            stringBuilder.append("date == null. ");
        } else if (!Utils.isYearMonthDay(date)){
            stringBuilder.append("date ").append(date).append(" was wrong format");
        }
        if (!Utils.isClean(id)){
            stringBuilder.append("id ").append(id).append(" was not clean");
        }
        return ErrorHandler.newHttpError(stringBuilder.toString(), HttpStatus.BAD_REQUEST, LOG);
    }

    @GetMapping("/v1/subsets/schema")
    public ResponseEntity<JsonNode> getSchema(){
        metricsService.incrementGETCounter();
        LOG.info("GET schema for subsets");
        
        LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
        return consumer.getFrom("/?schema");
    }

}
