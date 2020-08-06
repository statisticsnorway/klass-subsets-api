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
    public ResponseEntity<JsonNode> getSubsets( @RequestParam(defaultValue = "false") boolean includeFuture, @RequestParam(defaultValue = "false") boolean includeDrafts) {
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
                    JsonNode subset = getVersions(ldsAllSubsetsArrayNode.get(i).get(Field.ID).asText(), includeFuture, includeDrafts).getBody().get(0);
                    ldsAllSubsetsArrayNode.set(i, subset);
                }
                return new ResponseEntity<>(ldsAllSubsetsArrayNode, HttpStatus.OK);
            }
            return ErrorHandler.newHttpError("LDS returned 200 OK, but a non-array body. This was unexpected.", HttpStatus.INTERNAL_SERVER_ERROR, LOG);
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
            JsonNode idJN = subsetJson.get(Field.ID);
            String id = idJN.textValue();
            if (Utils.isClean(id)){
                LOG.info("POST subset with id "+id);
                LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
                ResponseEntity<JsonNode> ldsResponse = consumer.getFrom("/"+id);
                if (ldsResponse.getStatusCodeValue() == HttpStatus.NOT_FOUND.value()){
                    ObjectNode editableSubset = subsetJson.deepCopy();
                    String isoNow = Utils.getNowISO();
                    editableSubset.put(Field.LAST_UPDATED_DATE, isoNow);
                    editableSubset.put(Field.CREATED_DATE, isoNow);
                    String versionValidFrom = editableSubset.get(Field.VERSION_VALID_FROM).asText();
                    String validFrom = editableSubset.get(Field.VALID_FROM).asText();
                    if (!versionValidFrom.equals(validFrom)){
                        return ErrorHandler.newHttpError("validFrom must be equal versionValidFrom for the first version of the subset (this one)", HttpStatus.BAD_REQUEST, LOG);
                    }
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
    public ResponseEntity<JsonNode> getSubset(@PathVariable("id") String id, @RequestParam(defaultValue = "false") boolean includeDrafts, @RequestParam(defaultValue = "false") boolean includeFuture) {
        metricsService.incrementGETCounter();
        LOG.info("GET subset with id "+id);

        if (Utils.isClean(id)){
            ResponseEntity<JsonNode> majorVersionsRE = getVersions(id, includeFuture, includeDrafts);
            if (majorVersionsRE.getStatusCodeValue() != HttpStatus.OK.value())
                return majorVersionsRE;
            JsonNode majorVersionsBody = majorVersionsRE.getBody();
            if (majorVersionsBody != null && majorVersionsBody.isArray()){
                if (majorVersionsBody.has(0))
                    return new ResponseEntity<>(majorVersionsBody.get(0), HttpStatus.OK);
                return ErrorHandler.newHttpError("No subset matched the parameters", HttpStatus.NOT_FOUND, LOG);
            } else {
                return ErrorHandler.newHttpError("internal call to /versions did not return array", HttpStatus.INTERNAL_SERVER_ERROR, LOG);
            }
        }
        return ErrorHandler.illegalID(LOG);
    }

    @PutMapping(value = "/v1/subsets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> putSubset(@PathVariable("id") String id, @RequestBody JsonNode newVersionOfSubset) {
        metricsService.incrementPUTCounter();
        LOG.info("PUT subset with id "+id);

        if (Utils.isClean(id)) {
            ObjectNode editableSubset = Utils.cleanSubsetVersion(newVersionOfSubset).deepCopy();
            editableSubset.put(Field.LAST_UPDATED_DATE, Utils.getNowISO());
            ResponseEntity<JsonNode> mostRecentVersionRE = getSubset(id, true, true);
            ResponseEntity<JsonNode> oldVersionsRE = getVersions(id, true, true);
            JsonNode mostRecentVersionOfThisSubset = mostRecentVersionRE.getBody();
            if (mostRecentVersionRE.getStatusCodeValue() == HttpStatus.OK.value()){
                assert mostRecentVersionOfThisSubset != null && mostRecentVersionOfThisSubset.has(Field.ID) : "no old subset with this id was found in body of response entity";

                String oldID = mostRecentVersionOfThisSubset.get(Field.ID).asText();
                String newID = newVersionOfSubset.get(Field.ID).asText();
                boolean sameID = oldID.equals(newID);
                boolean sameIDAsRequest = newID.equals(id);
                boolean consistentID = oldID.equals(newID) && newID.equals(id);

                String newVersionValidFrom = newVersionOfSubset.get(Field.VERSION_VALID_FROM).asText();
                ArrayNode versionsArrayNode = Utils.sortByVersionValidFrom(Utils.cleanSubsetVersion(oldVersionsRE.getBody()).deepCopy());

                String newVersionString = newVersionOfSubset.get(Field.VERSION).asText();

                boolean sameVersionValidFrom = false;
                for (JsonNode jsonNode : versionsArrayNode) {
                    if (!jsonNode.get(Field.VERSION).asText().equals(newVersionString) && jsonNode.get(Field.VERSION_VALID_FROM).asText().equals(newVersionValidFrom)) {
                        sameVersionValidFrom = true;
                        break;
                    }
                }

                String mostRecentVersionValidFrom = mostRecentVersionOfThisSubset.get(Field.VERSION_VALID_FROM).asText();
                int newAndMostRecentVersionValidFromComparison = newVersionValidFrom.compareTo(mostRecentVersionValidFrom);
                if (newAndMostRecentVersionValidFromComparison < 0){
                    JsonNode firstVersion = versionsArrayNode.get(versionsArrayNode.size() - 1);
                    String firstVersionValidFrom = firstVersion.get(Field.VERSION_VALID_FROM).asText();
                    if (newVersionValidFrom.compareTo(firstVersionValidFrom) < 0){
                        if (!newVersionOfSubset.get(Field.VALID_FROM).asText().equals(newVersionOfSubset.get(Field.VERSION_VALID_FROM).asText())){
                            return ErrorHandler.newHttpError("'versionValidFrom' can not be earlier than subset 'validFrom'", HttpStatus.BAD_REQUEST, LOG);
                        }
                    }
                }

                ResponseEntity<JsonNode> prevPatchOfThisVersionRE = getVersion(id, newVersionString);
                boolean thisVersionExistsFromBefore = prevPatchOfThisVersionRE.getStatusCodeValue() == 200;
                boolean attemptToChangeCodesOfPublishedVersion = false;
                if (thisVersionExistsFromBefore){
                    JsonNode prevPatchOfThisVersion = prevPatchOfThisVersionRE.getBody();
                    boolean thisVersionIsPublishedFromBefore = prevPatchOfThisVersion.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN);

                    if (thisVersionIsPublishedFromBefore){
                        String oldCodeList = prevPatchOfThisVersion.get(Field.CODES).asText();
                        String newCodeList = newVersionOfSubset.get(Field.CODES).asText();
                        boolean differentCodeList = oldCodeList.equals(newCodeList);
                        attemptToChangeCodesOfPublishedVersion = !differentCodeList;

                        Iterator<String> prevPatchFieldNames = prevPatchOfThisVersion.fieldNames();
                        Iterator<String> newPatchFieldNames = newVersionOfSubset.fieldNames();

                        boolean allSameFields = true;
                        while (allSameFields && prevPatchFieldNames.hasNext()){
                            String field = prevPatchFieldNames.next();
                            if (!newVersionOfSubset.has(field)) {
                                allSameFields = false;
                            }
                        }

                        while (allSameFields && newPatchFieldNames.hasNext()){
                            String field = newPatchFieldNames.next();
                            if (!prevPatchOfThisVersion.has(field)) {
                                allSameFields = false;
                            }
                        }

                        if (!allSameFields){
                            return ErrorHandler.newHttpError("The submitted version does not contain all the same fields as the already published version that it attempts to override", HttpStatus.BAD_REQUEST, LOG);
                        }

                        newPatchFieldNames = newVersionOfSubset.fieldNames();
                        while (newPatchFieldNames.hasNext()){
                            String field = newPatchFieldNames.next();
                            String[] changeableFieldsInPublishedVersion = {Field.VERSION_RATIONALE, Field.VALID_UNTIL, Field.LAST_UPDATED_BY, Field.LAST_UPDATED_DATE};
                            ArrayList<String> changeableFieldsList = new ArrayList<>();
                            Collections.addAll(changeableFieldsList, changeableFieldsInPublishedVersion);
                            if (!changeableFieldsList.contains(field)){
                                if (!prevPatchOfThisVersion.get(field).asText().equals(newVersionOfSubset.get(field).asText())) {
                                    return ErrorHandler.newHttpError("The version of the subset you are trying to change is published, which means you can only change validUntil and versionRationale.", HttpStatus.BAD_REQUEST, LOG);
                                }
                            }
                        }
                    }
                }

                // If there is a version which is previous to this version that is still a DRAFT, you can not publish this version.

                if (consistentID && !attemptToChangeCodesOfPublishedVersion && !sameVersionValidFrom){
                    LDSConsumer consumer = new LDSConsumer(LDS_SUBSET_API);
                    return consumer.putTo("/" + id, editableSubset);
                } else {
                    StringBuilder errorStringBuilder = new StringBuilder();
                    if (!sameID)
                        errorStringBuilder.append("- ID of submitted subset (").append(newID).append(") was not same as id of stored subset(").append(oldID).append("). ");
                    if(!sameIDAsRequest)
                        errorStringBuilder.append("- ID of submitted subset(").append(newID).append(") was not the same as id in request param (").append(id).append("). ");
                    if (attemptToChangeCodesOfPublishedVersion)
                        errorStringBuilder.append("- No changes are allowed in the code list of a published version. ");
                    if (sameVersionValidFrom)
                        errorStringBuilder.append("- It is not allowed to submit a version with versionValidFrom equal to that of an existing version. ");
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
    public ResponseEntity<JsonNode> getVersions(@PathVariable("id") String id, @RequestParam(defaultValue = "false") boolean includeFuture, @RequestParam(defaultValue = "false") boolean includeDrafts) {
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
                        ObjectNode subsetVersionDocument = versionNode.get(Field.DOCUMENT).deepCopy();
                        if (!subsetVersionDocument.isEmpty()) {
                            if (includeDrafts || subsetVersionDocument.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)){
                                if (includeFuture || subsetVersionDocument.get(Field.VERSION_VALID_FROM).asText().compareTo(Utils.getNowISO()) < 0) {
                                    if (!subsetVersionDocument.has(Field.LAST_UPDATED_DATE)) {
                                        subsetVersionDocument.set(Field.LAST_UPDATED_DATE, subsetVersionDocument.get(Field.CREATED_DATE));
                                    }
                                    JsonNode self = Utils.getSelfLinkObject(mapper, ServletUriComponentsBuilder.fromCurrentRequestUri(), subsetVersionDocument);
                                    subsetVersionDocument.set("_links", self);
                                    int subsetMajorVersion = Integer.parseInt(subsetVersionDocument.get(Field.VERSION).textValue().split("\\.")[0]);
                                    if (!versionLastUpdatedMap.containsKey(subsetMajorVersion)) { // Only include the latest update of any major version
                                        versionLastUpdatedMap.put(subsetMajorVersion, subsetVersionDocument);
                                    } else {
                                        ObjectNode versionStoredInMap = versionLastUpdatedMap.get(subsetMajorVersion).deepCopy();
                                        if (!versionStoredInMap.has(Field.LAST_UPDATED_DATE)) {
                                            versionStoredInMap.set(Field.LAST_UPDATED_DATE, versionStoredInMap.get(Field.CREATED_DATE));
                                            versionLastUpdatedMap.put(subsetMajorVersion, versionStoredInMap);
                                        }
                                        String lastUpdatedDate = subsetVersionDocument.get(Field.LAST_UPDATED_DATE).textValue();
                                        if (versionLastUpdatedMap.get(subsetMajorVersion).get(Field.LAST_UPDATED_DATE).textValue().compareTo(lastUpdatedDate) < 0) {
                                            versionLastUpdatedMap.put(subsetMajorVersion, subsetVersionDocument);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ArrayList<JsonNode> versionList = new ArrayList<>(versionLastUpdatedMap.size());
                    versionLastUpdatedMap.forEach((versionInt, versionJsonNode) -> versionList.add(versionJsonNode));
                    versionList.sort(Comparator.comparing(v -> v.get(Field.VERSION_VALID_FROM).asText()));
                    String validTo = "";
                    for (JsonNode jsonNode : versionList) {
                        ObjectNode editableSubset = jsonNode.deepCopy();
                        editableSubset.set(Field.CODES, resolveURNs(editableSubset, validTo));
                        validTo = editableSubset.get(Field.VERSION_VALID_FROM).asText();
                    }

                    ArrayNode majorVersionsArrayNode = mapper.createArrayNode();
                    versionList.forEach(majorVersionsArrayNode::add);

                    JsonNode latestVersion = Utils.getLatestMajorVersion(majorVersionsArrayNode, false);
                    JsonNode latestPublishedVersionNode = Utils.getLatestMajorVersion(majorVersionsArrayNode, true);
                    boolean publishedVersionExists = latestPublishedVersionNode != null;
                    int latestMajorVersion = Integer.parseInt(latestVersion.get(Field.VERSION).asText().split("\\.")[0]);

                    ArrayNode majorVersionsObjectNodeArray = mapper.createArrayNode();
                    for (JsonNode versionNode : majorVersionsArrayNode) {
                        ObjectNode objectNode = versionNode.deepCopy();
                        int version = Integer.parseInt(objectNode.get(Field.VERSION).asText().split("\\.")[0]);
                        objectNode.put(Field.VERSION, Integer.toString(version));
                        if (publishedVersionExists && version < latestMajorVersion && objectNode.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)){
                            if (latestPublishedVersionNode.has(Field.NAME)){
                                objectNode.set(Field.NAME, latestPublishedVersionNode.get(Field.NAME));
                            }
                            if (latestPublishedVersionNode.has(Field.SHORT_NAME)){
                                objectNode.set(Field.SHORT_NAME, latestPublishedVersionNode.get(Field.SHORT_NAME));
                            }
                        }
                        majorVersionsObjectNodeArray.add(objectNode);
                    }
                    ArrayNode sorted = Utils.sortByVersionValidFrom(majorVersionsArrayNode);
                    return new ResponseEntity<>(sorted, HttpStatus.OK);
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
                ResponseEntity<JsonNode> versionsRE = getVersions(id, true, true);
                JsonNode responseBodyJSON = versionsRE.getBody();
                if (responseBodyJSON != null){
                    if (responseBodyJSON.isArray()) {
                        ArrayNode versionsArrayNode = (ArrayNode) responseBodyJSON;
                        for (JsonNode arrayEntry : versionsArrayNode) {
                            String subsetVersion = arrayEntry.get(Field.VERSION).asText();
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
    public ResponseEntity<JsonNode> getSubsetCodes(@PathVariable("id") String id, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(defaultValue = "false") boolean includeDrafts, @RequestParam(defaultValue = "false") boolean includeFuture) {
        LOG.info("GET codes of subset with id "+id);
        metricsService.incrementGETCounter();

        if (Utils.isClean(id)){
            if (from == null && to == null){
                LOG.debug("getting all codes of the latest/current version of subset "+id);
                ResponseEntity<JsonNode> subsetResponseEntity = getSubset(id, includeDrafts, includeFuture);
                JsonNode responseBodyJSON = subsetResponseEntity.getBody();
                if (responseBodyJSON != null){
                    ArrayNode codes = (ArrayNode) responseBodyJSON.get(Field.CODES);
                    ObjectMapper mapper = new ObjectMapper();
                    ArrayNode urnArray = mapper.createArrayNode();
                    for (JsonNode code : codes) {
                        urnArray.add(code.get(Field.URN).textValue());
                    }
                    return new ResponseEntity<>(urnArray, HttpStatus.OK);
                }
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            boolean isFromDate = from != null;
            boolean isToDate = to != null;
            if ((!isFromDate || Utils.isYearMonthDay(from)) && (!isToDate || Utils.isYearMonthDay(to))){ // If a date is given as param, it must be valid format
                // If a date interval is specified using 'from' and 'to' query parameters
                ResponseEntity<JsonNode> versionsResponseEntity = getVersions(id, includeFuture, includeDrafts);
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
                        JsonNode firstVersion = versionsArrayNode.get(versionsArrayNode.size() - 1).get(Field.DOCUMENT);
                        JsonNode lastVersion = versionsArrayNode.get(0).get(Field.DOCUMENT);
                        ArrayNode intersectionValidCodesInIntervalArrayNode = mapper.createArrayNode();
                        // Check if first version includes fromDate, and last version includes toDate. If not, then return an empty list.
                        String firstVersionValidFromString = firstVersion.get(Field.VALID_FROM).textValue().split("T")[0];
                        String lastVersionValidUntilString = lastVersion.get(Field.VALID_UNTIL).textValue().split("T")[0];
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
                                if (includeDrafts || arrayEntry.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)){
                                    // if this version has any overlap with the valid interval . . .
                                    JsonNode subset = arrayEntry.get(Field.DOCUMENT);
                                    String validFromDateString = subset.get(Field.VALID_FROM).textValue().split("T")[0];
                                    String validUntilDateString = subset.get(Field.VALID_UNTIL).textValue().split("T")[0];

                                    boolean validUntilGTFrom = true;
                                    if (isFromDate)
                                        validUntilGTFrom = validUntilDateString.compareTo(from) > 0;

                                    boolean validFromLTTo = true;
                                    if (isToDate)
                                        validFromLTTo = validFromDateString.compareTo(to) < 0;

                                    if (validUntilGTFrom || validFromLTTo) {
                                        LOG.debug("Version " + subset.get(Field.VERSION) + " is valid in the interval, so codes will be added to map");
                                        // . . . using each code in this version as key, increment corresponding integer value in map
                                        JsonNode codes = arrayEntry.get(Field.DOCUMENT).get(Field.CODES);
                                        ArrayNode codesArrayNode = (ArrayNode) codes;
                                        LOG.debug("There are " + codesArrayNode.size() + " codes in this version");
                                        for (JsonNode jsonNode : codesArrayNode) {
                                            String codeURN = jsonNode.get(Field.URN).asText();
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
    public ResponseEntity<JsonNode> getSubsetCodesAt(@PathVariable("id") String id, @RequestParam String date, @RequestParam(defaultValue = "false") boolean includeFuture, @RequestParam(defaultValue = "false") boolean includeDrafts) {
        metricsService.incrementGETCounter();
        LOG.info("GET codes valid at date "+date+" for subset with id "+id);

        if (date != null && Utils.isClean(id) && (Utils.isYearMonthDay(date))){
            ResponseEntity<JsonNode> versionsResponseEntity = getVersions(id, includeFuture, includeDrafts);
            JsonNode responseBodyJSON = versionsResponseEntity.getBody();
            if (responseBodyJSON != null){
                if (responseBodyJSON.isArray()) {
                    ArrayNode arrayNode = (ArrayNode) responseBodyJSON;
                    for (JsonNode jsonNode : arrayNode) {
                        JsonNode version = jsonNode.get(Field.DOCUMENT);
                        String entryValidFrom = version.get(Field.VALID_FROM).textValue();
                        String entryValidUntil = version.get(Field.VALID_UNTIL).textValue();
                        if (entryValidFrom.compareTo(date) <= 0 && entryValidUntil.compareTo(date) >= 0 ){
                            LOG.debug("Found valid codes at "+date+". "+version.get(Field.CODES).size());
                            ObjectMapper mapper = new ObjectMapper();
                            ArrayNode codeArray = mapper.createArrayNode();
                            version.get(Field.CODES).forEach(e -> codeArray.add(e.get(Field.URN)));
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

    private ArrayNode resolveURNs(JsonNode subset, String to){
        ArrayNode codes = (ArrayNode)subset.get(Field.CODES);
        List<String> codeURNs = new ArrayList<>(codes.size());
        codes.forEach(c->codeURNs.add(c.get(Field.URN).asText()));
        String versionValidFrom = subset.get(Field.VERSION_VALID_FROM).asText();
        try {
            ArrayNode codesArrayNode = KlassURNResolver.resolveURNs(codeURNs, versionValidFrom, to);
            return codesArrayNode;
        } catch (Exception | Error e){
            LOG.info("Could not resolve code URNs against KLASS. Returning subset with URNs only.");
            return codes;
        }
    }

}
