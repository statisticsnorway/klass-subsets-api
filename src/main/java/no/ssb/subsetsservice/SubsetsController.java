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

import java.util.*;

@CrossOrigin
@RestController
public class SubsetsController {

    private MetricsService metricsService;

    private static SubsetsController instance;
    private static final Logger LOG = LoggerFactory.getLogger(SubsetsController.class);

    @Autowired
    public SubsetsController(MetricsService metricsService){
        this.metricsService = metricsService;
        instance = this;
    }

    public SubsetsController(){
        instance = this;
    }

    public static SubsetsController getInstance(){
        return instance;
    }

    @GetMapping("/v1/subsets")
    public ResponseEntity<JsonNode> getSubsets( @RequestParam(defaultValue = "false") boolean includeDrafts, @RequestParam(defaultValue = "false") boolean includeFuture, @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
        metricsService.incrementGETCounter();

        LOG.info("GET subsets");
        LDSFacade ldsFacade = new LDSFacade();
        List<String> subsetIDsList = ldsFacade.getAllSubsetIDs();
        ArrayNode arrayNode = new ObjectMapper().createArrayNode();
        for (String id : subsetIDsList) {
            ResponseEntity<JsonNode> subsetRE = getSubset(id, includeDrafts, includeFuture, rankedUrnOnly);
            if (subsetRE.getStatusCode() == HttpStatus.OK)
                arrayNode.add(subsetRE.getBody());
            else
                ErrorHandler.newHttpError("On GET subsets, attempt to retrieve subset with ID "+id+" returned from LDS with "+subsetRE.getStatusCode().toString(), HttpStatus.INTERNAL_SERVER_ERROR, LOG);
        }
        return new ResponseEntity<>(arrayNode, HttpStatus.OK);
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

        if (subsetJson == null)
            return ErrorHandler.newHttpError("POST: Can not create subset from empty body", HttpStatus.BAD_REQUEST, LOG);

        //TODO: Validate that body is a val subset, somehow?

        if (!subsetJson.has(Field.VERSION))
            return ErrorHandler.newHttpError("Each subset must have the field 'version', which uniquely identifies the version of the subset", HttpStatus.BAD_REQUEST, LOG);

        if(subsetJson.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN) && subsetJson.get(Field.CODES).isEmpty())
            return ErrorHandler.newHttpError("Can not publish a subset with an empty code list", HttpStatus.BAD_REQUEST, LOG);

        if ((subsetJson.get(Field.VALID_FROM).asText().compareTo(subsetJson.get(Field.VERSION_VALID_FROM).asText()) != 0))
            return ErrorHandler.newHttpError("'versionValidFrom' can not be different from the subset's 'validFrom'", HttpStatus.BAD_REQUEST, LOG);

        if (subsetJson.has(Field.VALID_UNTIL) && subsetJson.has(Field.VERSION_VALID_UNTIL) && subsetJson.get(Field.VALID_UNTIL).asText().compareTo(subsetJson.get(Field.VERSION_VALID_UNTIL).asText()) != 0)
            return ErrorHandler.newHttpError("'versionValidUntil' can not be different from the subset's 'validUntil'", HttpStatus.BAD_REQUEST, LOG);

        if (subsetJson.has(Field.VALID_UNTIL) && subsetJson.get(Field.VALID_UNTIL).asText().compareTo(subsetJson.get(Field.VERSION_VALID_FROM).asText()) <= 0)
            return ErrorHandler.newHttpError("The subset's 'validUntil' must be set to a date after 'versionValidFrom' and 'validFrom'", HttpStatus.BAD_REQUEST, LOG);

        String id = subsetJson.get(Field.ID).textValue();
        LOG.info("POST subset with id "+id);

        if (!Utils.isClean(id))
            return ErrorHandler.illegalID(LOG);

        boolean subsetExists = new LDSFacade().existsSubsetWithID(id);
        if (subsetExists)
            return ErrorHandler.newHttpError("POST: Can not create subset. ID already in use", HttpStatus.BAD_REQUEST, LOG);

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
        ResponseEntity<JsonNode> responseEntity = new LDSFacade().createSubset(cleanSubset, id);
        if (responseEntity.getStatusCode().equals(HttpStatus.CREATED)){
            responseEntity = new ResponseEntity<>(cleanSubset, HttpStatus.CREATED);
        }
        return responseEntity;
    }

    @GetMapping("/v1/subsets/{id}")
    public ResponseEntity<JsonNode> getSubset(@PathVariable("id") String id, @RequestParam(defaultValue = "false") boolean includeDrafts, @RequestParam(defaultValue = "false") boolean includeFuture, @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
        metricsService.incrementGETCounter();
        LOG.info("GET subset with id "+id+" includeDrafts");

        if (Utils.isClean(id)){
            ResponseEntity<JsonNode> majorVersionsRE = getVersions(id, includeFuture, includeDrafts, rankedUrnOnly);
            if (majorVersionsRE.getStatusCode() != HttpStatus.OK) {
                LOG.error("Failed to get version of subset "+id);
                return majorVersionsRE;
            }
            JsonNode majorVersionsBody = majorVersionsRE.getBody();
            if (majorVersionsBody != null && majorVersionsBody.isArray()){
                LOG.debug("The array node with major versions");
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

        if (!Utils.isClean(id))
            return ErrorHandler.illegalID(LOG);

        ResponseEntity<JsonNode> oldVersionsRE = getVersions(id, true, true, true);
        HttpStatus getVersionsStatus = oldVersionsRE.getStatusCode();

        if (getVersionsStatus.equals(HttpStatus.NOT_FOUND))
            return ErrorHandler.newHttpError("Can not PUT a subset that does not exist from before. POST the subset instead", HttpStatus.BAD_REQUEST, LOG);

        if (!getVersionsStatus.equals(HttpStatus.OK))
            return ErrorHandler.newHttpError("Call for version of subset '"+id+"' returned with code "+getVersionsStatus.toString(), HttpStatus.INTERNAL_SERVER_ERROR, LOG);

        //TODO: Validate that the incoming subset version contains the legal fields?

        if (!newVersionOfSubset.has(Field.VERSION))
            return ErrorHandler.newHttpError("Each subset must have the field 'version', which uniquely identifies the version of the subset", HttpStatus.BAD_REQUEST, LOG);

        if(newVersionOfSubset.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN) && newVersionOfSubset.get(Field.CODES).isEmpty())
            return ErrorHandler.newHttpError("Can not publish a subset with an empty code list", HttpStatus.BAD_REQUEST, LOG);

        if (!(newVersionOfSubset.get(Field.VALID_FROM).asText().compareTo(newVersionOfSubset.get(Field.VERSION_VALID_FROM).asText()) <= 0))
            return ErrorHandler.newHttpError("'versionValidFrom' can not be earlier than subset 'validFrom'", HttpStatus.BAD_REQUEST, LOG);

        if (newVersionOfSubset.has(Field.VALID_UNTIL) && newVersionOfSubset.get(Field.VALID_UNTIL).asText().compareTo(newVersionOfSubset.get(Field.VERSION_VALID_FROM).asText()) <= 0)
            return ErrorHandler.newHttpError("The subset's 'validUntil' must be set to a date after 'versionValidFrom'", HttpStatus.BAD_REQUEST, LOG);

        if (newVersionOfSubset.has(Field.VALID_UNTIL) && newVersionOfSubset.has(Field.VERSION_VALID_UNTIL) && newVersionOfSubset.get(Field.VALID_UNTIL).asText().compareTo(newVersionOfSubset.get(Field.VERSION_VALID_UNTIL).asText()) > 0)
            return ErrorHandler.newHttpError("The subset's 'versionValidUntil' must be set to a date equal to or before 'validUntil'", HttpStatus.BAD_REQUEST, LOG);

        if (!(newVersionOfSubset.get(Field.VALID_FROM).asText().compareTo(newVersionOfSubset.get(Field.VERSION_VALID_FROM).asText()) <= 0))
            return ErrorHandler.newHttpError("'versionValidFrom' can not be earlier than subset 'validFrom'", HttpStatus.BAD_REQUEST, LOG);

        ResponseEntity<JsonNode> oldPublishedVersionsRE = getVersions(id, true, false, true);
        boolean thisSubsetIsPublishedFromBefore = oldPublishedVersionsRE.getStatusCode().equals(HttpStatus.OK) && oldPublishedVersionsRE.getBody() != null && oldPublishedVersionsRE.getBody().size() > 0;
        LOG.debug("This SUBSET has at leas one published version");

        ArrayNode versionsArrayNode = Utils.sortByVersionValidFrom(Utils.cleanSubsetVersion(Objects.requireNonNull(oldVersionsRE.getBody())).deepCopy());
        JsonNode mostRecentVersionOfThisSubset = versionsArrayNode.get(0);

        assert mostRecentVersionOfThisSubset.has(Field.ID) : "most recent version of this subset did not have the field '"+Field.ID+"' ";

        ObjectNode editableNewVersionOfSubset = Utils.cleanSubsetVersion(newVersionOfSubset).deepCopy();
        editableNewVersionOfSubset.put(Field.LAST_UPDATED_DATE, Utils.getNowISO());
        JsonNode createdDate = mostRecentVersionOfThisSubset.get(Field.CREATED_DATE);
        editableNewVersionOfSubset.set(Field.CREATED_DATE, createdDate);

        if (!editableNewVersionOfSubset.has(Field.VALID_UNTIL)){
            //TODO: Add the validUntil date from the existing published version, if exists?
            JsonNode validUntil = null;
            if (thisSubsetIsPublishedFromBefore){
                JsonNode publishedVersion = oldPublishedVersionsRE.getBody().get(0);
                if (publishedVersion.has(Field.VALID_UNTIL))
                    validUntil = publishedVersion.get(Field.VALID_UNTIL);
            }
            if (validUntil == null && mostRecentVersionOfThisSubset.has(Field.VALID_UNTIL)) {
                validUntil = mostRecentVersionOfThisSubset.get(Field.VALID_UNTIL);
            }
            if (validUntil != null)
                editableNewVersionOfSubset.set(Field.VALID_UNTIL, validUntil);
            else
                LOG.error("The PUT version as well as the most recent published version and the most recent version overall had no validUntil field, so this version will have to validUntil");
        }

        String oldID = mostRecentVersionOfThisSubset.get(Field.ID).asText();
        String newID = editableNewVersionOfSubset.get(Field.ID).asText();
        boolean sameID = oldID.equals(newID);
        boolean sameIDAsRequest = newID.equals(id);
        boolean consistentID = sameID && sameIDAsRequest;
        if (!consistentID){
            StringBuilder errorStringBuilder = new StringBuilder();
            if (!sameID)
                errorStringBuilder.append("- ID of submitted subset (").append(newID).append(") was not same as id of stored subset(").append(oldID).append("). ");
            if(!sameIDAsRequest)
                errorStringBuilder.append("- ID of submitted subset(").append(newID).append(") was not the same as id in request param (").append(id).append("). ");
            return ErrorHandler.newHttpError(errorStringBuilder.toString(), HttpStatus.BAD_REQUEST, LOG);
        }

        String newVersionValidFrom = editableNewVersionOfSubset.get(Field.VERSION_VALID_FROM).asText();
        String newSubsetValidFrom = editableNewVersionOfSubset.get(Field.VALID_FROM).asText();
        String newVersionString = editableNewVersionOfSubset.get(Field.VERSION).asText();

        for (JsonNode subsetVersionJsonNode : versionsArrayNode) {
            if (!subsetVersionJsonNode.get(Field.VERSION).asText().equals(newVersionString) && subsetVersionJsonNode.get(Field.VERSION_VALID_FROM).asText().equals(newVersionValidFrom)) {
                return ErrorHandler.newHttpError("It is not allowed to submit a version with versionValidFrom equal to that of an existing version.", HttpStatus.BAD_REQUEST, LOG);
            }
        }

        String mostRecentVersionValidFrom = mostRecentVersionOfThisSubset.get(Field.VERSION_VALID_FROM).asText();
        if (newVersionValidFrom.compareTo(mostRecentVersionValidFrom) < 0){
            // The new version being PUT is not the last version!
            JsonNode firstVersion = versionsArrayNode.get(versionsArrayNode.size() - 1);
            String firstVersionValidFrom = firstVersion.get(Field.VERSION_VALID_FROM).asText();
            if (newVersionValidFrom.compareTo(firstVersionValidFrom) < 0){
                // The new version being PUT is the new first version!
                if (!newSubsetValidFrom.equals(newVersionValidFrom)){
                    return ErrorHandler.newHttpError("When a new first version is posted, the subset 'validFrom' must be set equal to the 'versionValidFrom'", HttpStatus.BAD_REQUEST, LOG);
                }
            } else {
                // The new version being PUT is neither the first or the last version, but is valid in between existing versions
                // TODO: Should this be illegal?
            }
        } else {
            // The new version being put is the new last version
            // If defined, the subset's 'validUntil' in the new version must be the same as the subsets 'versionValidUntil' .
        }

        ResponseEntity<JsonNode> prevPatchOfThisVersionRE = getVersion(id, newVersionString, true, true, true);
        boolean thisVersionExistsFromBefore = prevPatchOfThisVersionRE.getStatusCode().equals(HttpStatus.OK);
        if (thisVersionExistsFromBefore){
            ResponseEntity<JsonNode> prevPublishedPatchOfThisVersionRE = getVersion(id, newVersionString, false, false, true);
            JsonNode prevPublishedPatchOfThisVersion = prevPublishedPatchOfThisVersionRE.getBody();
            boolean thisVersionIsPublishedFromBefore = prevPublishedPatchOfThisVersionRE.getStatusCode().equals(HttpStatus.OK) && prevPublishedPatchOfThisVersion.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN);

            if (thisVersionIsPublishedFromBefore){
                String oldCodeList = prevPublishedPatchOfThisVersion.get(Field.CODES).asText();
                String newCodeList = editableNewVersionOfSubset.get(Field.CODES).asText();
                if (!oldCodeList.equals(newCodeList)){
                    return ErrorHandler.newHttpError("No changing the code list of a published subset version", HttpStatus.BAD_REQUEST, LOG);
                }

                Iterator<String> prevPatchFieldNames = prevPublishedPatchOfThisVersion.fieldNames();
                Iterator<String> newPatchFieldNames = editableNewVersionOfSubset.fieldNames();

                String[] changeableFieldsInPublishedVersion = {Field.VERSION_RATIONALE, Field.VALID_UNTIL, Field.LAST_UPDATED_BY, Field.LAST_UPDATED_DATE};
                ArrayList<String> changeableFieldsList = new ArrayList<>();
                Collections.addAll(changeableFieldsList, changeableFieldsInPublishedVersion);
                StringBuilder fieldErrorBuilder = new StringBuilder();
                fieldErrorBuilder.append("Changeable fields: [ ");
                changeableFieldsList.forEach(s->fieldErrorBuilder.append(s).append(" "));
                fieldErrorBuilder.append("]");

                boolean allSameFields = true;
                while (allSameFields && prevPatchFieldNames.hasNext()){
                    String field = prevPatchFieldNames.next();
                    if (!editableNewVersionOfSubset.has(field) && !changeableFieldsList.contains(field)) {
                        fieldErrorBuilder.append("- The new patch of version (").append(editableNewVersionOfSubset.get(Field.VERSION).asText()).append(") of the subset with ID '").append(prevPublishedPatchOfThisVersion.get(Field.ID).asText()).append("' does not contain the field '").append(field).append("' that is present in the old patch of this version (").append(prevPublishedPatchOfThisVersion.get(Field.ID).asText()).append("), and is a field that is not allowed to change when a version is already published. ");
                        allSameFields = false;
                    }
                }

                while (allSameFields && newPatchFieldNames.hasNext()){
                    String field = newPatchFieldNames.next();
                    if (!prevPublishedPatchOfThisVersion.has(field) && !changeableFieldsList.contains(field)) {
                        fieldErrorBuilder.append("- The previous patch of version (").append(prevPublishedPatchOfThisVersion.get(Field.VERSION).asText()).append(") of the subset with ID '").append(prevPublishedPatchOfThisVersion.get(Field.ID).asText()).append("' does not contain the field '").append(field).append("' that is present in the new patch of this version version (").append(editableNewVersionOfSubset.get(Field.ID).asText()).append("), and is a field that is not allowed to change when a version is already published. ");
                        allSameFields = false;
                    }
                }

                if (!allSameFields){
                    return ErrorHandler.newHttpError(fieldErrorBuilder.toString(), HttpStatus.BAD_REQUEST, LOG);
                }

                newPatchFieldNames = editableNewVersionOfSubset.fieldNames();
                while (newPatchFieldNames.hasNext()){
                    String field = newPatchFieldNames.next();
                    if (!changeableFieldsList.contains(field)){
                        if (!prevPublishedPatchOfThisVersion.get(field).asText().equals(editableNewVersionOfSubset.get(field).asText())) {
                            return ErrorHandler.newHttpError("The version of the subset you are trying to change is published, which means you can only change validUntil and versionRationale.", HttpStatus.BAD_REQUEST, LOG);
                        }
                    }
                }
            }
        }

        ResponseEntity<JsonNode> responseEntity = new LDSFacade().editSubset(editableNewVersionOfSubset, id);
        if (responseEntity.getStatusCode().equals(HttpStatus.OK)){
            responseEntity = new ResponseEntity<>(editableNewVersionOfSubset, HttpStatus.OK);
        }
        return responseEntity;
    }

    @GetMapping("/v1/subsets/{id}/versions")
    public ResponseEntity<JsonNode> getVersions(@PathVariable("id") String id, @RequestParam(defaultValue = "false") boolean includeFuture, @RequestParam(defaultValue = "false") boolean includeDrafts, @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
        metricsService.incrementGETCounter();
        LOG.info("GET all versions of subset with id: "+id+" includeFuture: "+includeFuture+" includeDrafts: "+includeDrafts);

        ObjectMapper mapper = new ObjectMapper();
        if (Utils.isClean(id)){
            ResponseEntity<JsonNode> ldsRE = new LDSFacade().getTimelineOfSubset(id);
            if (ldsRE.getStatusCodeValue() != HttpStatus.OK.value()){
                return ldsRE;
            }
            JsonNode responseBodyJSON = ldsRE.getBody();
            if (responseBodyJSON != null){
                if (!responseBodyJSON.has(0)){
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                }
                if (responseBodyJSON.isArray()) {
                    LOG.debug("versions response body json is array");
                    ArrayNode timelineArrayNode = (ArrayNode) responseBodyJSON;
                    LOG.debug("timelineArrayNode size: "+timelineArrayNode.size());

                    for (int i = timelineArrayNode.size()-1; i >= 0; i--) {
                        if (timelineArrayNode.get(i).get(Field.DOCUMENT).isEmpty()){
                            LOG.debug("Found an empty subset version in the LDS timeline at index "+i+"/"+(timelineArrayNode.size()-1)+", indicating a DELETE action. Deleting all previous versions.");
                            for (int i1 = 0; i1 <= i; i1++) {
                                timelineArrayNode.remove(0);
                            }
                            break;
                        }
                    }
                    LOG.debug("timelineArrayNode is now of size "+timelineArrayNode.size());

                    Map<Integer, JsonNode> versionLastUpdatedMap = new HashMap<>(timelineArrayNode.size() * 2, 0.51f);
                    for (JsonNode versionNode : timelineArrayNode) {
                        ObjectNode subsetVersionDocument = versionNode.get(Field.DOCUMENT).deepCopy();
                        if (!subsetVersionDocument.isEmpty()) {
                            if (includeDrafts || subsetVersionDocument.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)){
                                if (includeFuture || subsetVersionDocument.get(Field.VERSION_VALID_FROM).asText().compareTo(Utils.getNowISO()) < 0) {
                                    if (!subsetVersionDocument.has(Field.LAST_UPDATED_DATE)) {
                                        subsetVersionDocument.set(Field.LAST_UPDATED_DATE, subsetVersionDocument.get(Field.CREATED_DATE));
                                    }
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
                    LOG.debug("versionLastUpdatedMap size: "+versionLastUpdatedMap.size());

                    ArrayList<JsonNode> versionList = new ArrayList<>(versionLastUpdatedMap.size());
                    for (Map.Entry<Integer, JsonNode> entry : versionLastUpdatedMap.entrySet()) {
                        versionList.add(entry.getValue());
                    }
                    LOG.debug("versionList size: "+versionList.size());
                    versionList.sort(Utils::versionComparator);
                    if (!rankedUrnOnly)
                        versionList = resolveURNsOfCodesInAllVersions(versionList);
                    ArrayNode majorVersionsArrayNode = mapper.createArrayNode();
                    versionList.forEach(majorVersionsArrayNode::add);
                    LOG.debug("majorVersionsArrayNode size: "+majorVersionsArrayNode.size());
                    if (majorVersionsArrayNode.isEmpty())
                        return ErrorHandler.newHttpError("No versions of the subset "+id+" exist with the given constraints includeDrafts="+includeDrafts+" and includeFuture="+includeFuture, HttpStatus.NOT_FOUND, LOG);

                    JsonNode latestVersion = Utils.getLatestMajorVersion(majorVersionsArrayNode, false);
                    LOG.debug("gotten latestVersion");
                    boolean latestVersionExist = latestVersion != null;
                    LOG.debug("latest version exist? "+ latestVersionExist);
                    JsonNode latestPublishedVersionNode = Utils.getLatestMajorVersion(majorVersionsArrayNode, true);
                    LOG.debug("gotten lastestPublishedVersion");
                    boolean publishedVersionExists = latestPublishedVersionNode != null;
                    LOG.debug("published version exists? "+publishedVersionExists);

                    ArrayNode majorVersionsObjectNodeArray = mapper.createArrayNode();
                    for (JsonNode versionNode : majorVersionsArrayNode) {
                        ObjectNode editableVersionNode = versionNode.deepCopy();
                        int version = Integer.parseInt(editableVersionNode.get(Field.VERSION).asText().split("\\.")[0]);
                        editableVersionNode.put(Field.VERSION, Integer.toString(version));
                        if (publishedVersionExists){
                            int latestPublishedMajorVersionInt = Integer.parseInt(latestPublishedVersionNode.get(Field.VERSION).asText().split("\\.")[0]);
                            LOG.debug("latest major version id as int: "+latestPublishedMajorVersionInt);
                            if (version < latestPublishedMajorVersionInt && editableVersionNode.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)){
                                if (latestPublishedVersionNode.has(Field.NAME)){
                                    editableVersionNode.set(Field.NAME, latestPublishedVersionNode.get(Field.NAME));
                                }
                                if (latestPublishedVersionNode.has(Field.SHORT_NAME)){
                                    editableVersionNode.set(Field.SHORT_NAME, latestPublishedVersionNode.get(Field.SHORT_NAME));
                                }
                            }
                        }

                        majorVersionsObjectNodeArray.add(editableVersionNode);
                    }
                    LOG.debug("sorting by versionValidFrom");
                    ArrayNode sorted = Utils.sortByVersionValidFrom(majorVersionsArrayNode);
                    LOG.debug("returning sorted");
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
    public ResponseEntity<JsonNode> getVersion(@PathVariable("id") String id, @PathVariable("version") String version, @RequestParam(defaultValue = "false") boolean includeFuture, @RequestParam(defaultValue = "false") boolean includeDrafts, @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
        metricsService.incrementGETCounter();
        LOG.info("GET version "+version+" of subset with id "+id);

        if (Utils.isClean(id) && Utils.isVersion(version)){
            if (Utils.isVersion(version)){
                ResponseEntity<JsonNode> versionsRE = getVersions(id, includeFuture, includeDrafts, rankedUrnOnly);
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
    public ResponseEntity<JsonNode> getSubsetCodes(@PathVariable("id") String id,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to,
                                                   @RequestParam(defaultValue = "false") boolean includeDrafts,
                                                   @RequestParam(defaultValue = "false") boolean includeFuture,
                                                   @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {

        LOG.info("GET codes of subset with id "+id);
        metricsService.incrementGETCounter();

        if (Utils.isClean(id)){
            if (from == null && to == null){
                LOG.debug("getting all codes of the latest/current version of subset "+id);
                ResponseEntity<JsonNode> subsetResponseEntity = getSubset(id, includeDrafts, includeFuture, rankedUrnOnly);
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
                ResponseEntity<JsonNode> versionsResponseEntity = getVersions(id, includeFuture, includeDrafts, rankedUrnOnly);
                JsonNode versionsResponseBodyJson = versionsResponseEntity.getBody();
                LOG.debug(String.format("Getting valid codes of subset %s from date %s to date %s", id, from, to));
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Integer> codeMap = new HashMap<>();
                int nrOfVersions;
                if (versionsResponseBodyJson != null) {
                    if (versionsResponseBodyJson.isArray()) {
                        ArrayNode versionsArrayNode = (ArrayNode) versionsResponseBodyJson;
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
                            for (JsonNode subsetVersion : versionsArrayNode) {
                                if (includeDrafts || subsetVersion.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)){
                                    // if this version has any overlap with the valid interval . . .
                                    String validFromDateString = subsetVersion.get(Field.VALID_FROM).textValue().split("T")[0];
                                    String validUntilDateString = subsetVersion.get(Field.VALID_UNTIL).textValue().split("T")[0];

                                    boolean validUntilGTFrom = true;
                                    if (isFromDate)
                                        validUntilGTFrom = validUntilDateString.compareTo(from) > 0;

                                    boolean validFromLTTo = true;
                                    if (isToDate)
                                        validFromLTTo = validFromDateString.compareTo(to) < 0;

                                    if (validUntilGTFrom || validFromLTTo) {
                                        LOG.debug("Version " + subsetVersion.get(Field.VERSION) + " is valid in the interval, so codes will be added to map");
                                        // . . . using each code in this version as key, increment corresponding integer value in map
                                        JsonNode codes = subsetVersion.get(Field.CODES);
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
    public ResponseEntity<JsonNode> getSubsetCodesAt(@PathVariable("id") String id,
                                                     @RequestParam String date,
                                                     @RequestParam(defaultValue = "false") boolean includeFuture,
                                                     @RequestParam(defaultValue = "false") boolean includeDrafts,
                                                     @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
        metricsService.incrementGETCounter();
        LOG.info("GET codes valid at date "+date+" for subset with id "+id);

        if (date != null && Utils.isClean(id) && (Utils.isYearMonthDay(date))){
            ResponseEntity<JsonNode> versionsResponseEntity = getVersions(id, includeFuture, includeDrafts, rankedUrnOnly);
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
        return new LDSFacade().getClassificationSubsetSchema();
    }

    void deleteAll(){
        new LDSFacade().deleteAllSubsets();
    }

    void deleteById(String id){
        new LDSFacade().deleteSubset(id);
    }

    private ArrayList<JsonNode> resolveURNsOfCodesInAllVersions(ArrayList<JsonNode> versionListInput){
        ArrayList<JsonNode> versionList = new ArrayList<>(versionListInput.size());
        versionListInput.forEach(v -> versionList.add(v.deepCopy()));
        versionList.sort(Utils::versionComparator);

        String validTo = ""; //FIXME: This means the most recent version does not have a validTo. Is it potentially a mistake?
        for (int i = 0; i < versionList.size(); i++) {
            ObjectNode editableSubset = versionList.get(i).deepCopy();
            ArrayNode resolvedCodes = resolveURNsOfCodesInThisSubset(editableSubset, validTo);
            editableSubset.set(Field.CODES, resolvedCodes);
            validTo = editableSubset.get(Field.VERSION_VALID_FROM).asText();
            versionList.set(i, editableSubset.deepCopy());
        }
        LOG.debug("URNs of all versions are resolved");
        return versionList;
    }

    private ArrayNode resolveURNsOfCodesInThisSubset(JsonNode subset, String subsetValidTo){
        subsetValidTo = subsetValidTo.split("T")[0];
        if (subsetValidTo.equals("") || Utils.isYearMonthDay(subsetValidTo)){
            try {
                ArrayNode codeURNArrayNode = new KlassURNResolver().resolveURNs(subset, subsetValidTo);
                return codeURNArrayNode;
            } catch (Exception | Error e){
                LOG.error(e.toString());
                return (ArrayNode)subset.get(Field.CODES);
            }
        }
        throw new IllegalArgumentException("'to' must be empty string '' or on the form YYYY-MM-DD, but was "+subsetValidTo);
    }

}
