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
    public ResponseEntity<JsonNode> getSubsets(
            @RequestParam(defaultValue = "false") boolean includeDrafts,
            @RequestParam(defaultValue = "false") boolean includeFuture,
            @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
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
                ErrorHandler.newHttpError(
                        "On GET subsets, attempt to retrieve subset with ID "+id+" returned from LDS with "+subsetRE.getStatusCode().toString(),
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        LOG);
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
            return ErrorHandler.newHttpError(
                    "Each subset must have the field 'version', which uniquely identifies the version of the subset",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if(subsetJson.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN) && subsetJson.get(Field.CODES).isEmpty())
            return ErrorHandler.newHttpError(
                    "Can not publish a subset with an empty code list",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if ((subsetJson.get(Field.VALID_FROM).asText().compareTo(subsetJson.get(Field.VERSION_VALID_FROM).asText()) != 0))
            return ErrorHandler.newHttpError(
                    "'versionValidFrom' can not be different from the subset's 'validFrom'",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if (subsetJson.has(Field.VALID_UNTIL) && subsetJson.has(Field.VERSION_VALID_UNTIL)
                && subsetJson.get(Field.VALID_UNTIL).asText().compareTo(subsetJson.get(Field.VERSION_VALID_UNTIL).asText()) != 0)
            return ErrorHandler.newHttpError(
                    "'versionValidUntil' can not be different from the subset's 'validUntil'",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if (subsetJson.has(Field.VALID_UNTIL)
                && subsetJson.get(Field.VALID_UNTIL).asText().compareTo(subsetJson.get(Field.VERSION_VALID_FROM).asText()) <= 0)
            return ErrorHandler.newHttpError(
                    "The subset's 'validUntil' must be set to a date after 'versionValidFrom' and 'validFrom'",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        String id = subsetJson.get(Field.ID).textValue();
        LOG.info("POST subset with id "+id);

        if (!Utils.isClean(id))
            return ErrorHandler.illegalID(LOG);

        boolean subsetExists = new LDSFacade().existsSubsetWithID(id);
        if (subsetExists)
            return ErrorHandler.newHttpError(
                    "POST: Can not create subset. ID already in use",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        ObjectNode editableSubset = subsetJson.deepCopy();
        subsetJson = null;

        String isoNow = Utils.getNowISO();
        editableSubset.put(Field.LAST_UPDATED_DATE, isoNow);
        editableSubset.put(Field.CREATED_DATE, isoNow);

        String versionValidFrom = editableSubset.get(Field.VERSION_VALID_FROM).asText();
        String validFrom = editableSubset.get(Field.VALID_FROM).asText();

        if (!versionValidFrom.equals(validFrom)){
            return ErrorHandler.newHttpError(
                    "validFrom must be equal versionValidFrom for the first version of the subset (this one)",
                    HttpStatus.BAD_REQUEST,
                    LOG);
        }

        if (!editableSubset.has(Field.VALID_UNTIL))
            editableSubset.set(Field.VALID_UNTIL, null);
        else if (editableSubset.has(Field.VERSION_VALID_UNTIL)){
            if (!editableSubset.get(Field.VERSION_VALID_UNTIL).asText().equals(editableSubset.get(Field.VALID_UNTIL).asText()))
                return ErrorHandler.newHttpError(
                        "validUntil must be equal versionValidUntil for the first version of the subset (this one)",
                        HttpStatus.BAD_REQUEST,
                        LOG);
        }

        JsonNode cleanSubset = Utils.cleanSubsetVersion(editableSubset);
        ResponseEntity<JsonNode> responseEntity = new LDSFacade().createSubset(cleanSubset, id);
        if (responseEntity.getStatusCode().equals(HttpStatus.CREATED)){
            responseEntity = new ResponseEntity<>(cleanSubset, HttpStatus.CREATED);
        }
        return responseEntity;
    }

    @GetMapping("/v1/subsets/{id}")
    public ResponseEntity<JsonNode> getSubset(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "false") boolean includeDrafts,
            @RequestParam(defaultValue = "false") boolean includeFuture,
            @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
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
                return ErrorHandler.newHttpError(
                        "No subset matched the parameters",
                        HttpStatus.NOT_FOUND,
                        LOG);
            } else {
                return ErrorHandler.newHttpError(
                        "internal call to /versions did not return array",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        LOG);
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
            return ErrorHandler.newHttpError(
                    "Can not PUT a subset that does not exist from before. POST the subset instead",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if (!getVersionsStatus.equals(HttpStatus.OK))
            return ErrorHandler.newHttpError(
                    "Call for version of subset '"+id+"' returned with code "+getVersionsStatus.toString(),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    LOG);

        //TODO: Validate that the incoming subset version contains the legal fields?

        if (!newVersionOfSubset.has(Field.VERSION))
            return ErrorHandler.newHttpError(
                    "Each subset must have the field 'version', which uniquely identifies the version of the subset",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if(newVersionOfSubset.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)
                && newVersionOfSubset.get(Field.CODES).isEmpty())
            return ErrorHandler.newHttpError(
                    "Can not publish a subset with an empty code list",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if (!(newVersionOfSubset.get(Field.VALID_FROM).asText().compareTo(newVersionOfSubset.get(Field.VERSION_VALID_FROM).asText()) <= 0))
            return ErrorHandler.newHttpError(
                    "'versionValidFrom' can not be earlier than subset 'validFrom'",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if (newVersionOfSubset.has(Field.VALID_UNTIL)
                && !newVersionOfSubset.get(Field.VALID_UNTIL).isNull() && newVersionOfSubset.get(Field.VALID_UNTIL).asText().compareTo(newVersionOfSubset.get(Field.VERSION_VALID_FROM).asText()) <= 0)
            return ErrorHandler.newHttpError(
                    "The subset's 'validUntil' must be set to a date after 'versionValidFrom'",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if (newVersionOfSubset.has(Field.VALID_UNTIL)
                && newVersionOfSubset.has(Field.VERSION_VALID_UNTIL)
                && newVersionOfSubset.get(Field.VALID_UNTIL).asText().compareTo(newVersionOfSubset.get(Field.VERSION_VALID_UNTIL).asText()) > 0)
            return ErrorHandler.newHttpError(
                    "The subset's 'versionValidUntil' must be set to a date equal to or before 'validUntil'",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if (!(newVersionOfSubset.get(Field.VALID_FROM).asText().compareTo(newVersionOfSubset.get(Field.VERSION_VALID_FROM).asText()) <= 0))
            return ErrorHandler.newHttpError(
                    "'versionValidFrom' can not be earlier than subset 'validFrom'",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        // When a new draft is posted that is the new first version, make sure validUntil == versionValidUntil
        ResponseEntity<JsonNode> oldPublishedVersionsRE = getVersions(id, true, false, true);
        boolean thisSubsetIsPublishedFromBefore = oldPublishedVersionsRE.getStatusCode().equals(HttpStatus.OK) && oldPublishedVersionsRE.getBody() != null && oldPublishedVersionsRE.getBody().size() > 0;
        if (thisSubsetIsPublishedFromBefore){
            LOG.debug("This SUBSET has at least one published version");
            ArrayNode oldPublishedVersions = oldPublishedVersionsRE.getBody().deepCopy();

            JsonNode firstPublished = oldPublishedVersions.get(oldPublishedVersions.size()-1);
            JsonNode lastVersion = oldPublishedVersions.get(0);

            String firstValidFrom = firstPublished.get(Field.VALID_FROM).asText();
            String newValidFrom = newVersionOfSubset.get(Field.VALID_FROM).asText();
            String newVersionValidFrom = newVersionOfSubset.get(Field.VERSION_VALID_FROM).asText();

            if (!firstValidFrom.equals(newValidFrom)){
                if (!newValidFrom.equals(newVersionValidFrom))
                    return ErrorHandler.newHttpError(
                            "The 'validFrom' field must at all times match the 'versionValidFrom' of the earliest version",
                            HttpStatus.BAD_REQUEST,
                            LOG);
            }

            String lastValidUntil = lastVersion.get(Field.VALID_UNTIL).asText();
            String lastVersionValidFrom = lastVersion.get(Field.VERSION_VALID_FROM).asText();
            String newValidUntil = newVersionOfSubset.get(Field.VALID_UNTIL).asText();

            if (!lastValidUntil.equals(newValidUntil)){
                if (lastValidUntil.compareTo(newValidUntil) > 0)
                    return ErrorHandler.newHttpError(
                            "You can't turn back validUntil, only turn it forward",
                            HttpStatus.BAD_REQUEST,
                            LOG);
                if (!(newVersionValidFrom.compareTo(lastVersionValidFrom) >= 0))
                    return ErrorHandler.newHttpError(
                            "You can only update the validUntil by changing the latest version, or publishing a new latest version",
                            HttpStatus.BAD_REQUEST,
                            LOG);
            }
        }


        ArrayNode versionsArrayNode = Utils.sortByVersionValidFrom(Utils.cleanSubsetVersion(Objects.requireNonNull(oldVersionsRE.getBody())).deepCopy());
        JsonNode mostRecentVersionOfThisSubset = versionsArrayNode.get(0);
        JsonNode earliestVersionOfThisSubset = versionsArrayNode.get(versionsArrayNode.size()-1);

        assert mostRecentVersionOfThisSubset.has(Field.ID) : "most recent version of this subset did not have the field '"+Field.ID+"' ";

        ObjectNode editableNewVersionOfSubset = Utils.cleanSubsetVersion(newVersionOfSubset).deepCopy();
        newVersionOfSubset = null;
        editableNewVersionOfSubset.put(Field.LAST_UPDATED_DATE, Utils.getNowISO());
        JsonNode createdDate = mostRecentVersionOfThisSubset.get(Field.CREATED_DATE);
        editableNewVersionOfSubset.set(Field.CREATED_DATE, createdDate);

        if (!editableNewVersionOfSubset.has(Field.VALID_UNTIL)){
            editableNewVersionOfSubset.set(Field.VALID_UNTIL, null);
            LOG.info("The PUT version did not have a validUntil value defined. validUntil was defined and set to null");
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
            return ErrorHandler.newHttpError(
                    errorStringBuilder.toString(),
                    HttpStatus.BAD_REQUEST,
                    LOG);
        }

        String newVersionValidFrom = editableNewVersionOfSubset.get(Field.VERSION_VALID_FROM).asText();
        String newSubsetValidFrom = editableNewVersionOfSubset.get(Field.VALID_FROM).asText();
        String newVersionString = editableNewVersionOfSubset.get(Field.VERSION).asText();

        for (JsonNode subsetVersionJsonNode : versionsArrayNode) {
            String versionValidFrom = subsetVersionJsonNode.get(Field.VERSION_VALID_FROM).asText();
            if (!subsetVersionJsonNode.get(Field.VERSION).asText().equals(newVersionString) && versionValidFrom.equals(newVersionValidFrom))
                return ErrorHandler.newHttpError(
                        "It is not allowed to submit a version with versionValidFrom equal to that of an existing version.",
                        HttpStatus.BAD_REQUEST,
                        LOG);
            if (!subsetVersionJsonNode.get(Field.VERSION).asText().equals(editableNewVersionOfSubset.get(Field.VERSION).asText())
                    && newVersionValidFrom.compareTo(versionValidFrom) < 0
                    && editableNewVersionOfSubset.has(Field.VERSION_VALID_UNTIL) && editableNewVersionOfSubset.get(Field.VALID_UNTIL).asText().compareTo(versionValidFrom) > 0)
                return ErrorHandler.newHttpError(
                        "It is not allowed for subset version to overlap in validity periods",
                        HttpStatus.BAD_REQUEST,
                        LOG);
        }

        String mostRecentVersionValidFrom = mostRecentVersionOfThisSubset.get(Field.VERSION_VALID_FROM).asText();
        if (newVersionValidFrom.compareTo(mostRecentVersionValidFrom) < 0){
            // The new version being PUT is not the last version!
            JsonNode firstVersion = versionsArrayNode.get(versionsArrayNode.size() - 1);
            String firstVersionValidFrom = firstVersion.get(Field.VERSION_VALID_FROM).asText();
            if (newVersionValidFrom.compareTo(firstVersionValidFrom) < 0){
                // The new version being PUT is the new first version!
                if (!newSubsetValidFrom.equals(newVersionValidFrom)){
                    return ErrorHandler.newHttpError(
                            "When a new first version is posted, the subset 'validFrom' must be set equal to the 'versionValidFrom'",
                            HttpStatus.BAD_REQUEST,
                            LOG);
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
                if (!oldCodeList.equals(newCodeList))
                    return ErrorHandler.newHttpError(
                            "No changing the code list of a published subset version",
                            HttpStatus.BAD_REQUEST,
                            LOG);

                Iterator<String> prevPatchFieldNames = prevPublishedPatchOfThisVersion.fieldNames();
                Iterator<String> newPatchFieldNames = editableNewVersionOfSubset.fieldNames();

                String[] changeableFieldsInPublishedVersion = {
                        Field.VERSION_RATIONALE,
                        Field.VALID_UNTIL,
                        Field.VERSION_VALID_UNTIL,
                        Field.LAST_UPDATED_BY,
                        Field.LAST_UPDATED_DATE};
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
                        fieldErrorBuilder
                                .append("- The new patch of version (")
                                .append(editableNewVersionOfSubset.get(Field.VERSION).asText())
                                .append(") of the subset with ID '")
                                .append(prevPublishedPatchOfThisVersion.get(Field.ID).asText())
                                .append("' does not contain the field '")
                                .append(field)
                                .append("' that is present in the old patch of this version (")
                                .append(prevPublishedPatchOfThisVersion.get(Field.ID).asText())
                                .append("), and is a field that is not allowed to change when a version is already published. ");
                        allSameFields = false;
                    }
                }

                while (allSameFields && newPatchFieldNames.hasNext()){
                    String field = newPatchFieldNames.next();
                    if (!prevPublishedPatchOfThisVersion.has(field) && !changeableFieldsList.contains(field)) {
                        fieldErrorBuilder
                                .append("- The previous patch of version (")
                                .append(prevPublishedPatchOfThisVersion.get(Field.VERSION).asText())
                                .append(") of the subset with ID '").append(prevPublishedPatchOfThisVersion.get(Field.ID).asText())
                                .append("' does not contain the field '")
                                .append(field)
                                .append("' that is present in the new patch of this version version (")
                                .append(editableNewVersionOfSubset.get(Field.ID).asText())
                                .append("), and is a field that is not allowed to change when a version is already published. ");
                        allSameFields = false;
                    }
                }

                if (!allSameFields){
                    return ErrorHandler.newHttpError(
                            fieldErrorBuilder.toString(),
                            HttpStatus.BAD_REQUEST,
                            LOG);
                }

                newPatchFieldNames = editableNewVersionOfSubset.fieldNames();
                while (newPatchFieldNames.hasNext()){
                    String field = newPatchFieldNames.next();
                    if (!changeableFieldsList.contains(field)){
                        if (!prevPublishedPatchOfThisVersion.get(field).asText().equals(editableNewVersionOfSubset.get(field).asText())) {
                            return ErrorHandler.newHttpError(
                                    "The version of the subset you are trying to change is published, which means you can only change validUntil and versionRationale.",
                                    HttpStatus.BAD_REQUEST,
                                    LOG);
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
    public ResponseEntity<JsonNode> getVersions(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "false") boolean includeFuture,
            @RequestParam(defaultValue = "false") boolean includeDrafts,
            @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
        metricsService.incrementGETCounter();
        LOG.info("GET all versions of subset with id: "+id+" includeFuture: "+includeFuture+" includeDrafts: "+includeDrafts);

        ObjectMapper mapper = new ObjectMapper();
        if (Utils.isClean(id)){
            ResponseEntity<JsonNode> ldsRE = new LDSFacade().getTimelineOfSubset(id);
            JsonNode responseBodyJSON = ldsRE.getBody();
            if (ldsRE.getStatusCode() != HttpStatus.OK){
                if (ldsRE.getStatusCode().equals(HttpStatus.NOT_FOUND))
                    return ErrorHandler.newHttpError(
                            "Subset versions not found. No timeline for subset found in LDS.",
                            HttpStatus.NOT_FOUND,
                            LOG);
                return ErrorHandler.newHttpError(
                        "LDS.getTimelineOfSubset("+id+") did not return HttpStatus 200 OK. Status of the LDS response: "
                                +ldsRE.getStatusCodeValue()+" Body of the LDS response: \n"
                                +(responseBodyJSON == null ? "null" : responseBodyJSON.asText())
                                +" \n ******* end body ******* ",
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        LOG);
            }
            if (responseBodyJSON != null){
                if (!responseBodyJSON.has(0)){
                    return ErrorHandler.newHttpError(
                            "Subset versions not found. LDS timeline response body did not have a field at index 0",
                            HttpStatus.NOT_FOUND,
                            LOG);
                }
                if (responseBodyJSON.isArray()) {
                    LOG.debug("versions response body json is array");
                    ArrayNode timelineArrayNode = (ArrayNode) responseBodyJSON;
                    LOG.debug("timelineArrayNode size: "+timelineArrayNode.size());

                    for (int i = timelineArrayNode.size()-1; i >= 0; i--) {
                        if (timelineArrayNode.get(i).get(Field.DOCUMENT).isEmpty()){
                            LOG.debug("Found an empty subset version in the LDS timeline at index "+i+"/" +(timelineArrayNode.size()-1)
                                    +", indicating a DELETE action. Deleting all previous versions.");
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
                    ArrayList<JsonNode> publishedVersionList = new ArrayList<>();
                    for (JsonNode jsonNode : versionList) {
                        if (jsonNode.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN))
                            publishedVersionList.add(jsonNode);
                    }
                    ArrayNode majorVersionsArrayNode = mapper.createArrayNode();
                    versionList.forEach(majorVersionsArrayNode::add);
                    LOG.debug("majorVersionsArrayNode size: "+majorVersionsArrayNode.size());
                    if (majorVersionsArrayNode.isEmpty())
                        return ErrorHandler.newHttpError(
                                "No versions of the subset "+id+" exist with the given constraints includeDrafts="+includeDrafts+" and includeFuture="+includeFuture,
                                HttpStatus.NOT_FOUND,
                                LOG);

                    JsonNode latestVersionNode = versionList.isEmpty() ? null : versionList.get(0);
                    LOG.debug("gotten latestVersion");
                    boolean latestVersionExist = latestVersionNode != null;
                    LOG.debug("latest version exist? "+ latestVersionExist);
                    JsonNode latestPublishedVersionNode = publishedVersionList.isEmpty() ? null : publishedVersionList.get(0);
                    LOG.debug("gotten latestPublishedVersion");
                    boolean publishedVersionExists = latestPublishedVersionNode != null;
                    LOG.debug("published version exists? "+publishedVersionExists);
                    JsonNode firstPublishedVersionNode = publishedVersionList.isEmpty() ? null : publishedVersionList.get(publishedVersionList.size()-1);
                    boolean firstPublishedVersionExists = firstPublishedVersionNode != null;

                    JsonNode latestPublishedName = publishedVersionExists && latestPublishedVersionNode.has(Field.NAME) ? latestPublishedVersionNode.get(Field.NAME) : null;
                    JsonNode latestPublishedShortName = publishedVersionExists && latestPublishedVersionNode.has(Field.SHORT_NAME) ? latestPublishedVersionNode.get(Field.SHORT_NAME) : null;
                    JsonNode latestPublishedValidUntil = publishedVersionExists && latestPublishedVersionNode.has(Field.VALID_UNTIL) ? latestPublishedVersionNode.get(Field.VALID_UNTIL) : null;
                    if (latestPublishedValidUntil == null && publishedVersionExists && latestPublishedVersionNode.has(Field.VERSION_VALID_UNTIL))
                        latestPublishedValidUntil = latestPublishedVersionNode.get(Field.VERSION_VALID_UNTIL);
                    JsonNode firstPublishedValidFrom = firstPublishedVersionExists && firstPublishedVersionNode.has(Field.VALID_FROM) ? firstPublishedVersionNode.get(Field.VALID_FROM) : null;

                    ArrayNode majorVersionsObjectNodeArray = mapper.createArrayNode();
                    for (JsonNode versionNode : majorVersionsArrayNode) {
                        ObjectNode editableVersionNode = versionNode.deepCopy();
                        int version = Integer.parseInt(editableVersionNode.get(Field.VERSION).asText().split("\\.")[0]);
                        editableVersionNode.put(Field.VERSION, Integer.toString(version));
                        String versionValidFromString = editableVersionNode.get(Field.VERSION_VALID_FROM).asText();
                        if (publishedVersionExists){
                            String latestVersionValidFromString = latestPublishedVersionNode.get(Field.VERSION_VALID_FROM).asText();
                            int latestPublishedMajorVersionInt = Integer.parseInt(latestPublishedVersionNode.get(Field.VERSION).asText().split("\\.")[0]);
                            LOG.debug("latest major version id as int: "+latestPublishedMajorVersionInt);
                            if (editableVersionNode.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)){
                                if (versionValidFromString.compareTo(latestVersionValidFromString) > 0)
                                    LOG.error("Version "+latestPublishedMajorVersionInt+" was picked as the latest published version, but had versionValidFrom "
                                            +latestVersionValidFromString+" while version "+version+" had versionValidFrom "+versionValidFromString);
                                if (latestPublishedVersionNode.has(Field.NAME))
                                    editableVersionNode.set(Field.NAME, latestPublishedName);
                                if (latestPublishedVersionNode.has(Field.SHORT_NAME))
                                    editableVersionNode.set(Field.SHORT_NAME, latestPublishedShortName);
                                if (latestPublishedValidUntil != null)
                                    editableVersionNode.set(Field.VALID_UNTIL, latestPublishedValidUntil);
                                if (firstPublishedValidFrom != null)
                                    editableVersionNode.set(Field.VALID_FROM, firstPublishedValidFrom);
                            }
                        }

                        majorVersionsObjectNodeArray.add(editableVersionNode);
                    }
                    LOG.debug("sorting by versionValidFrom");
                    ArrayNode sorted = Utils.sortByVersionValidFrom(majorVersionsObjectNodeArray);
                    LOG.debug("returning sorted");
                    return new ResponseEntity<>(sorted, HttpStatus.OK);
                } else {
                    return ErrorHandler.newHttpError(
                            "LDS response body was not JSON array",
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            LOG);
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
    public ResponseEntity<JsonNode> getVersion(
            @PathVariable("id") String id,
            @PathVariable("version") String version,
            @RequestParam(defaultValue = "false") boolean includeFuture,
            @RequestParam(defaultValue = "false") boolean includeDrafts,
            @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
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
            boolean isFromDate = from != null;
            boolean isToDate = to != null;
            if (!isFromDate && !isToDate){
                LOG.debug("getting all codes of the latest/current version of subset "+id);
                ResponseEntity<JsonNode> subsetResponseEntity = getSubset(id, includeDrafts, includeFuture, rankedUrnOnly);
                JsonNode responseBodyJSON = subsetResponseEntity.getBody();
                if (!subsetResponseEntity.getStatusCode().equals(HttpStatus.OK))
                    return subsetResponseEntity;
                else if (responseBodyJSON != null){
                    ArrayNode codes = (ArrayNode) responseBodyJSON.get(Field.CODES);
                    ObjectMapper mapper = new ObjectMapper();
                    ArrayNode urnArray = mapper.createArrayNode();
                    for (JsonNode code : codes) {
                        urnArray.add(code.get(Field.URN).textValue());
                    }
                    return new ResponseEntity<>(urnArray, HttpStatus.OK);
                }
                return ErrorHandler.newHttpError("response body of getSubset with id "+id+" was null, so could not get codes.", HttpStatus.INTERNAL_SERVER_ERROR, LOG);
            }

            if ((!isFromDate || Utils.isYearMonthDay(from)) && (!isToDate || Utils.isYearMonthDay(to))){ // If a date is given as param, it must be valid format
                // If a date interval is specified using 'from' and 'to' query parameters
                ResponseEntity<JsonNode> versionsResponseEntity = getVersions(id, includeFuture, includeDrafts, rankedUrnOnly);
                if (!versionsResponseEntity.getStatusCode().equals(HttpStatus.OK))
                    return versionsResponseEntity;
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

                        boolean isFirstValidAtOrBeforeFromDate = true; // If no "from" date is given, version is automatically valid at or before "from" date
                        if (isFromDate) {
                            String firstVersionValidFromString = firstVersion.get(Field.VALID_FROM).textValue().split("T")[0];
                            LOG.debug("First version valid from: " + firstVersionValidFromString);
                            isFirstValidAtOrBeforeFromDate = firstVersionValidFromString.compareTo(from) <= 0;
                        }
                        LOG.debug("isFirstValidAtOrBeforeFromDate? " + isFirstValidAtOrBeforeFromDate);

                        boolean isLastValidAtOrAfterToDate = true; // If no "to" date is given, it is automatically valid at or after "to" date
                        if (isToDate && lastVersion.has(Field.VALID_UNTIL) && !lastVersion.get(Field.VALID_UNTIL).isNull()) {
                            String lastVersionValidUntilString = lastVersion.get(Field.VALID_UNTIL).textValue().split("T")[0];
                            LOG.debug("Last version valid until: " + lastVersionValidUntilString);
                            isLastValidAtOrAfterToDate = lastVersionValidUntilString.compareTo(to) >= 0;
                        }
                        LOG.debug("isLastValidAtOrAfterToDate? " + isLastValidAtOrAfterToDate);

                        if (isFirstValidAtOrBeforeFromDate && isLastValidAtOrAfterToDate) {
                            for (JsonNode subsetVersion : versionsArrayNode) {
                                if (includeDrafts || subsetVersion.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)){
                                    // if this version has any overlap with the valid interval . . .
                                    boolean validUntilGTFrom = true;
                                    if (isFromDate) {
                                        String validUntilDateString = subsetVersion.get(Field.VALID_UNTIL).textValue().split("T")[0];
                                        validUntilGTFrom = validUntilDateString.compareTo(from) > 0;
                                    }

                                    boolean validFromLTTo = true;
                                    if (isToDate) {
                                        String validFromDateString = subsetVersion.get(Field.VALID_FROM).textValue().split("T")[0];
                                        validFromLTTo = validFromDateString.compareTo(to) < 0;
                                    }

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
                return ErrorHandler.newHttpError("Response body was null", HttpStatus.INTERNAL_SERVER_ERROR, LOG);
            }
        }
        return ErrorHandler.illegalID(LOG);
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
            JsonNode versionsResponseBodyJSON = versionsResponseEntity.getBody();
            if (versionsResponseBodyJSON != null){
                if (versionsResponseBodyJSON.isArray()) {
                    ArrayNode versionsArrayNode = (ArrayNode) versionsResponseBodyJSON;
                    for (JsonNode versionJsonNode : versionsArrayNode) {
                        String entryValidFrom = versionJsonNode.get(Field.VALID_FROM).textValue();
                        String entryValidUntil = versionJsonNode.get(Field.VALID_UNTIL).textValue();
                        if (entryValidFrom.compareTo(date) <= 0 && entryValidUntil.compareTo(date) >= 0 ){
                            LOG.debug("Found valid codes at "+date+". "+versionJsonNode.get(Field.CODES).size());
                            ObjectMapper mapper = new ObjectMapper();
                            ArrayNode codeArray = mapper.createArrayNode();
                            versionJsonNode.get(Field.CODES).forEach(e -> codeArray.add(e.get(Field.URN)));
                            return new ResponseEntity<>(codeArray, HttpStatus.OK);
                        }
                    }
                    return ErrorHandler.newHttpError(
                            "Subset w id "+id+" not found",
                            HttpStatus.NOT_FOUND,
                            LOG);
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
        return ErrorHandler.newHttpError(
                stringBuilder.toString(),
                HttpStatus.BAD_REQUEST,
                LOG);
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
