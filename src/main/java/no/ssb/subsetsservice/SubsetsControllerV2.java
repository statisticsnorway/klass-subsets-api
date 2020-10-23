package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static org.springframework.http.HttpStatus.*;

@CrossOrigin
@RestController
public class SubsetsControllerV2 {

    private MetricsService metricsService;

    private static SubsetsControllerV2 instance;
    private static final Logger LOG = LoggerFactory.getLogger(SubsetsControllerV2.class);

    @Autowired
    public SubsetsControllerV2(MetricsService metricsService){
        this.metricsService = metricsService;
        instance = this;
    }

    public SubsetsControllerV2(){
        instance = this;
    }

    public static SubsetsControllerV2 getInstance(){
        return instance;
    }

    @GetMapping("/v2/subsets")
    public ResponseEntity<JsonNode> getSubsets(
            @RequestParam(defaultValue = "true") boolean includeDrafts,
            @RequestParam(defaultValue = "true") boolean includeFuture,
            @RequestParam(defaultValue = "true") boolean includeExpired) {
        metricsService.incrementGETCounter();

        LOG.info("GET all subsets includeDrafts="+includeDrafts+" includeFuture="+includeFuture+" includeExpired="+includeExpired);
        LDSFacade ldsFacade = new LDSFacade();
        ResponseEntity<JsonNode> subsetSeriesRE = ldsFacade.getAllSubsetSeries();
        ArrayNode subsetSeriesArray = subsetSeriesRE.getBody().deepCopy();
        String nowDate = Utils.getNowDate();
        for (int i = 0; i < subsetSeriesArray.size(); i++) {
            subsetSeriesArray.set(i, cleanSeries(subsetSeriesArray.get(i))); //FIXME: performance wise it would be best not to do this
        }
        if (!includeDrafts || !includeFuture || !includeExpired){
            for (int i = subsetSeriesArray.size() - 1; i >= 0; i--) {
                JsonNode subsetSeries = subsetSeriesArray.get(i);
                if (!includeDrafts && subsetSeries.get(Field.ADMINISTRATIVE_STATUS).equals(Field.DRAFT))
                    subsetSeriesArray.remove(i);
                else if (!includeFuture && subsetSeries.get(Field.VALID_FROM).asText().compareTo(nowDate) > 0)
                    subsetSeriesArray.remove(i);
                else if (!includeExpired
                        && subsetSeries.has(Field.VALID_UNTIL)
                        && !subsetSeries.get(Field.VALID_UNTIL).asText().isBlank()
                        && subsetSeries.get(Field.VALID_UNTIL).asText().compareTo(nowDate) < 0)
                    subsetSeriesArray.remove(i);
            }
        }
        return new ResponseEntity<>(subsetSeriesArray, OK);
    }

    /**
     * Create a new ClassificationSubsetSeries resource, with no versions yet
     * @param subsetSeriesJson
     * @return
     */
    @PostMapping("/v2/subsets")
    public ResponseEntity<JsonNode> postSubset(@RequestBody JsonNode subsetSeriesJson) {
        metricsService.incrementPOSTCounter();
        LOG.info("POST subset series received. Checking body . . .");

        if (subsetSeriesJson == null)
            return ErrorHandler.newHttpError("POST subset series: Can not create subset series from an empty body", BAD_REQUEST, LOG);

        ObjectNode editableSubsetSeries = subsetSeriesJson.deepCopy();
        subsetSeriesJson = null;

        if (!editableSubsetSeries.has(Field.ID))
            return ErrorHandler.newHttpError("POST subset series: Subset series must contain the field 'id'", BAD_REQUEST, LOG);

        String id = editableSubsetSeries.get(Field.ID).textValue();
        LOG.info("POST subset series with id "+id);

        if (!Utils.isClean(id))
            return ErrorHandler.illegalID(LOG);

        boolean subsetExists = new LDSFacade().existsSubsetSeriesWithID(id);
        if (subsetExists)
            return ErrorHandler.newHttpError(
                    "POST: Can not create subset. ID already in use",
                    BAD_REQUEST,
                    LOG);
        LOG.info("Subset with id "+id+" does not exist from before");

        //TODO: Validate that body is a valid subset series, somehow

        editableSubsetSeries.put(Field.LAST_MODIFIED, Utils.getNowISO());
        editableSubsetSeries.put(Field.CREATED_DATE, Utils.getNowDate());
        editableSubsetSeries.put(Field.CLASSIFICATION_TYPE, Field.SUBSET);
        editableSubsetSeries.set(Field.VERSIONS, new ObjectMapper().createArrayNode());

        LOG.debug("POSTING subset series with id "+id+" to LDS");
        ResponseEntity<JsonNode> responseEntity = new LDSFacade().createSubsetSeries(editableSubsetSeries, id);
        if (responseEntity.getStatusCode().equals(CREATED)){
            LOG.info("Series with id "+id+" was successfully created in LDS");
            responseEntity = new ResponseEntity<>(editableSubsetSeries, CREATED);
        } else {
            LOG.error("Subset series with id " + id + " was NOT CREATED in LDS! Returning LDS responseEntity . . .");
        }
        return responseEntity;
    }

    @GetMapping("/v2/subsets/{id}")
    public ResponseEntity<JsonNode> getSubsetSeriesByID(@PathVariable("id") String id) {
        metricsService.incrementGETCounter();
        LOG.info("GET subset series with id "+id);

        if (!Utils.isClean(id))
            return ErrorHandler.illegalID(LOG);

        ResponseEntity<JsonNode> subsetSeriesByIDRE = new LDSFacade().getSubsetSeries(id);
        HttpStatus status = subsetSeriesByIDRE.getStatusCode();
        if (status.equals(OK)) {
            JsonNode series = cleanSeries(subsetSeriesByIDRE.getBody());
            return new ResponseEntity<>(series, OK);
        } else
            return resolveNonOKLDSResponse("GET subsetSeries w id '"+id+"'", subsetSeriesByIDRE);
    }

    /**
     * Use this to make edits to the ClassificationSubsetSeries, except the versions array.
     * This is the information that is in common for all the versions.
     * Changes to the versions array will not be accepted.
     * To add a new version to the versions array you must instead use POST /v2/subsets/{id}/versions
     * To edit an existing version you must use PUT /v2/subsets/{id}/versions/{version_id}
     * @param seriesId
     * @param newVersionOfSeries
     * @return
     */
    @PutMapping(value = "/v2/subsets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> putSubset(@PathVariable("id") String seriesId, @RequestBody JsonNode newVersionOfSeries) {
        metricsService.incrementPUTCounter();
        LOG.info("PUT subset series with id "+seriesId);

        if (!Utils.isClean(seriesId))
            return ErrorHandler.illegalID(LOG);

        ResponseEntity<JsonNode> getSeriesRE = new LDSFacade().getSubsetSeries(seriesId);

        if (getSeriesRE.getStatusCode().equals(NOT_FOUND))
            return ErrorHandler.newHttpError(
                    "Can not PUT (edit) a subset that does not exist from before. POST the subset instead if you wish to create it",
                    BAD_REQUEST,
                    LOG);

        if (!getSeriesRE.getStatusCode().equals(OK)) {
            return resolveNonOKLDSResponse("GET subsetSeries w id '"+seriesId+"'", getSeriesRE);
        }

        JsonNode currentLatestEditionOfSeries = getSeriesRE.getBody();
        ObjectNode editableNewVersionOfSeries = newVersionOfSeries.deepCopy();
        newVersionOfSeries = null;

        //TODO: Validate that the incoming subset series edition contains all the right and legal fields?

        ArrayNode oldVersionsArray = currentLatestEditionOfSeries.has(Field.VERSIONS) ? currentLatestEditionOfSeries.get(Field.VERSIONS).deepCopy() : new ObjectMapper().createArrayNode();
        editableNewVersionOfSeries.set(Field.VERSIONS, oldVersionsArray);

        assert editableNewVersionOfSeries.has(Field.ID) : "Subset series did not have the field '"+Field.ID+"'.";

        editableNewVersionOfSeries.put(Field.LAST_MODIFIED, Utils.getNowISO());

        String oldID = currentLatestEditionOfSeries.get(Field.ID).asText();
        String newID = editableNewVersionOfSeries.get(Field.ID).asText();

        boolean sameID = oldID.equals(newID);
        boolean sameIDAsRequest = newID.equals(seriesId);
        boolean consistentID = sameID && sameIDAsRequest;
        if (!consistentID){
            StringBuilder errorStringBuilder = new StringBuilder();
            if (!sameID)
                errorStringBuilder.append("- ID of submitted subset series (").append(newID).append(") was not the same as id of stored subset (").append(oldID).append("). ");
            if(!sameIDAsRequest)
                errorStringBuilder.append("- ID of submitted subset series (").append(newID).append(") was not the same as id in request param (").append(seriesId).append("). ");
            return ErrorHandler.newHttpError(
                    errorStringBuilder.toString(),
                    BAD_REQUEST,
                    LOG);
        }

        boolean thisSeriesIsPublished = currentLatestEditionOfSeries.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN);

        if (thisSeriesIsPublished) {
            Iterator<String> prevPatchFieldNames = currentLatestEditionOfSeries.fieldNames();
            Iterator<String> newPatchFieldNames = editableNewVersionOfSeries.fieldNames();

            String[] changeableFieldsInPublishedSeries = {
                    Field.VALID_FROM,
                    Field.VALID_UNTIL,
                    Field.LAST_MODIFIED_BY,
                    Field.LAST_UPDATED_DATE,
                    Field.VERSIONS};
            ArrayList<String> changeableFieldsList = new ArrayList<>();
            Collections.addAll(changeableFieldsList, changeableFieldsInPublishedSeries);
            StringBuilder fieldErrorBuilder = new StringBuilder();
            fieldErrorBuilder.append("Changeable fields: [ ");
            changeableFieldsList.forEach(s -> fieldErrorBuilder.append(s).append(" "));
            fieldErrorBuilder.append("]");

            boolean allSameFields = true;
            while (allSameFields && prevPatchFieldNames.hasNext()) {
                String field = prevPatchFieldNames.next();
                if (!editableNewVersionOfSeries.has(field) && !changeableFieldsList.contains(field)) {
                    fieldErrorBuilder
                            .append("- The new patch of version (")
                            .append(editableNewVersionOfSeries.get(Field.VERSION).asText())
                            .append(") of the subset with ID '")
                            .append(currentLatestEditionOfSeries.get(Field.ID).asText())
                            .append("' does not contain the field '")
                            .append(field)
                            .append("' that is present in the old patch of this version (")
                            .append(currentLatestEditionOfSeries.get(Field.ID).asText())
                            .append("), and is a field that is not allowed to change when a version is already published. ");
                    allSameFields = false;
                }
            }

            while (allSameFields && newPatchFieldNames.hasNext()) {
                String field = newPatchFieldNames.next();
                if (!currentLatestEditionOfSeries.has(field) && !changeableFieldsList.contains(field)) {
                    fieldErrorBuilder
                            .append("- The previous patch of version (")
                            .append(currentLatestEditionOfSeries.get(Field.VERSION).asText())
                            .append(") of the subset with ID '").append(currentLatestEditionOfSeries.get(Field.ID).asText())
                            .append("' does not contain the field '")
                            .append(field)
                            .append("' that is present in the new patch of this version version (")
                            .append(editableNewVersionOfSeries.get(Field.ID).asText())
                            .append("), and is a field that is not allowed to change when a version is already published. ");
                    allSameFields = false;
                }
            }

            if (!allSameFields) {
                return ErrorHandler.newHttpError(
                        fieldErrorBuilder.toString(),
                        BAD_REQUEST,
                        LOG);
            }

            newPatchFieldNames = editableNewVersionOfSeries.fieldNames();
            while (newPatchFieldNames.hasNext()) {
                String field = newPatchFieldNames.next();
                if (!changeableFieldsList.contains(field)) {
                    if (!currentLatestEditionOfSeries.get(field).asText().equals(editableNewVersionOfSeries.get(field).asText())) {
                        return ErrorHandler.newHttpError(
                                "The version of the subset you are trying to change is published, which means you can only change validUntil and versionRationale.",
                                BAD_REQUEST,
                                LOG);
                    }
                }
            }
        }

        ResponseEntity<JsonNode> responseEntity = new LDSFacade().editSeries(editableNewVersionOfSeries, seriesId);
        if (responseEntity.getStatusCode().equals(OK)){
            responseEntity = new ResponseEntity<>(editableNewVersionOfSeries, OK);
        }
        return responseEntity;
    }

    /**
     * Use this to edit an existing version within a series.
     * Only edit versions that already have been created with post.
     * @param seriesId
     * @param versionUID
     * @param putVersion
     * @return
     */
    @PutMapping(value = "/v2/subsets/{seriesId}/versions/{versionUID}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> putSubsetVersion(@PathVariable("seriesId") String seriesId, @PathVariable("versionUID") String versionUID, @RequestBody JsonNode putVersion) {
        if (!Utils.isClean(seriesId))
            return ErrorHandler.illegalID(LOG);
        if (!Utils.isClean(versionUID))
            return ErrorHandler.newHttpError("Illegal characters in versionUID", BAD_REQUEST, LOG);

        ResponseEntity<JsonNode> getPreviousEditionOfVersion = getVersion(seriesId, versionUID);
        HttpStatus status = getPreviousEditionOfVersion.getStatusCode();
        if (status.equals(NOT_FOUND)){
            return ErrorHandler.newHttpError("Can not edit a subset version that does not exist.", BAD_REQUEST, LOG);
        }
        if (!status.equals(OK))
            return getPreviousEditionOfVersion;
        JsonNode previousEditionOfVersion = getPreviousEditionOfVersion.getBody();
        ObjectNode editablePutVersion = putVersion.deepCopy();


        editablePutVersion.set(Field.VERSION, previousEditionOfVersion.get(Field.VERSION));
        editablePutVersion.set(Field.CREATED_DATE, previousEditionOfVersion.get(Field.CREATED_DATE));
        editablePutVersion.set(Field.SERIES_ID, previousEditionOfVersion.get(Field.SERIES_ID));
        editablePutVersion.put(Field.LAST_MODIFIED, Utils.getNowISO());

        boolean wasDraftFromBefore = previousEditionOfVersion.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.DRAFT);
        boolean attemptToPublish = editablePutVersion.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN);

        // One set of rules for if the old version is DRAFT:
        if (wasDraftFromBefore){
            // If the new version is OPEN and the old one was DRAFT:
            // OR If validFrom or validUntil is changed
            if (attemptToPublish ||
                    (!previousEditionOfVersion.get(Field.VALID_FROM).equals(editablePutVersion.get(Field.VALID_FROM))) ||
                    (previousEditionOfVersion.has(Field.VALID_UNTIL) && (!editablePutVersion.has(Field.VALID_UNTIL)) || !editablePutVersion.get(Field.VALID_UNTIL).equals(previousEditionOfVersion.get(Field.VALID_UNTIL))) ||
                    (editablePutVersion.has(Field.VALID_UNTIL) && !previousEditionOfVersion.has(Field.VALID_UNTIL)) ){
                // check validity period overlap with other OPEN versions
                ResponseEntity<JsonNode> checkOverlapRE = isOverlappingValidity(editablePutVersion);
                if (!checkOverlapRE.getStatusCode().is2xxSuccessful()){
                    return checkOverlapRE;
                }
            }
        } else {
            JsonNode oldCodeList = previousEditionOfVersion.get(Field.CODES);
            JsonNode newCodeList = editablePutVersion.get(Field.CODES);

            //TODO:
            // Another stricter set of rules for if the old version is OPEN
            // Make sure codes are not changed.
            // Make sure validFrom is not changed.
        }

        return new ResponseEntity<>(editablePutVersion, OK);
    }

    private ResponseEntity<JsonNode> isOverlappingValidity(JsonNode editableVersion) {

        String validFrom = editableVersion.get(Field.VALID_FROM).asText();
        String validUntil = editableVersion.has(Field.VALID_UNTIL) ? editableVersion.get(Field.VALID_UNTIL).asText() : null;

        ArrayNode subsetVersionsArray = getVersions(editableVersion.get(Field.SERIES_ID).asText(), true, false).getBody().deepCopy();
        if (!subsetVersionsArray.isEmpty()){
            JsonNode firstPublishedVersion = null;
            JsonNode lastPublishedVersion = null;
            String firstValidFrom = null;
            String lastValidFrom = null;
            for (JsonNode versionJsonNode : subsetVersionsArray) {
                if (versionJsonNode.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)) { // We only care about cheching against published subset versions
                    LOG.debug("Checking version "+versionJsonNode.get(Field.SERIES_ID)+"_"+versionJsonNode.get(Field.VERSION)+" for overlap with the new version, since it is published.");
                    String versionValidFrom = versionJsonNode.get(Field.VALID_FROM).asText();
                    if (firstValidFrom == null || versionValidFrom.compareTo(firstValidFrom) < 0)
                        firstValidFrom = versionValidFrom;
                    if (lastValidFrom == null || versionValidFrom.compareTo(lastValidFrom) > 0)
                        lastValidFrom = versionValidFrom;
                    if (validFrom.compareTo(versionValidFrom) == 0)
                        return ErrorHandler.newHttpError(
                                "validFrom can not be the same as existing subset's valid from",
                                BAD_REQUEST,
                                LOG);
                    String versionValidUntil = versionJsonNode.has(Field.VALID_UNTIL) ? versionJsonNode.get(Field.VALID_UNTIL).asText() : null;
                    if (versionValidUntil != null && validUntil != null) {
                        if (validUntil.compareTo(versionValidUntil) <= 0 && validUntil.compareTo(versionValidFrom) >= 0)
                            return ErrorHandler.newHttpError(
                                    "The new version's validUntil is within the validity range of an existing subset",
                                    BAD_REQUEST,
                                    LOG);
                        if (validFrom.compareTo(versionValidFrom) >= 0 && validFrom.compareTo(versionValidUntil) <= 0)
                            return ErrorHandler.newHttpError(
                                    "The new version's validFrom is within the validity range of an existing subset",
                                    BAD_REQUEST,
                                    LOG);
                        if (validUntil.compareTo(versionValidUntil) == 0)
                            return ErrorHandler.newHttpError(
                                    "validUntil can not be the same as existing subset's validUntil, when they are explicit",
                                    BAD_REQUEST,
                                    LOG);
                    }
                }
            }
            LOG.debug("Done iterating over all existing versions of the subset to check validity period overlaps");
            if (firstValidFrom != null && validFrom.compareTo(firstValidFrom) >= 0 && lastValidFrom != null && validFrom.compareTo(lastValidFrom) <= 0)
                return ErrorHandler.newHttpError("The validity period of a new subset must be before or after all existing versions", BAD_REQUEST, LOG);
            boolean isNewLatestVersion = lastValidFrom == null || validFrom.compareTo(lastValidFrom) > 0;
            boolean isNewFirstVersion = firstValidFrom == null || validFrom.compareTo(firstValidFrom) < 0;

            if (editableVersion.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN) && subsetVersionsArray.size() > 0) {
                if (isNewLatestVersion) {
                    //TODO: If OPEN and is new latest version and other versions exist from before, set validUntil of previous version to be == this version's validFrom ?
                }
                if (isNewFirstVersion) {
                    //TODO: If OPEN and is new first version and other versions exist from before, set validUntil of this version to be == validFrom of next version ?
                }
            }
            LOG.debug("Done processing and checking new version in relation to old versions");
        }
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("message", "Subset version had no overlap with published versions");
        body.put("status", OK.value());
        return new ResponseEntity<>(body, OK);
    }

    @PostMapping(value = "/v2/subsets/{seriesId}/versions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> putSubsetVersion(@PathVariable("seriesId") String seriesId, @RequestBody JsonNode version) {
        if (!Utils.isClean(seriesId))
            return ErrorHandler.illegalID(LOG);

        ResponseEntity<JsonNode> getSeriesByIDRE = getSubsetSeriesByID(seriesId);
        if (!getSeriesByIDRE.getStatusCode().equals(OK)) {
            LOG.error("Attempt to get subset series by id '"+seriesId+"' returned a non-OK status code.");
            return getSeriesByIDRE;
        }
        ObjectNode editableVersion = version.deepCopy();
        version = null;

        //TODO: Validate the 'version' input's validity: Does it contain the right fields with the right values?

        String validFrom = editableVersion.get(Field.VALID_FROM).asText();
        String validUntil = editableVersion.has(Field.VALID_UNTIL) && !editableVersion.get(Field.VALID_UNTIL).isNull() ? editableVersion.get(Field.VALID_UNTIL).asText() : null;
        boolean hasValidUntil = validUntil != null;
        if (hasValidUntil && validFrom.compareTo(validUntil) >= 0)
            return ErrorHandler.newHttpError(
                    "validFrom can not be the same date as- or before validUntil, when validUntil is defined",
                    BAD_REQUEST,
                    LOG);

        JsonNode series = getSeriesByIDRE.getBody();
        int versionsSize = series.has(Field.VERSIONS) ? series.get(Field.VERSIONS).size() : 0;
        LOG.debug("series "+seriesId+" versions size: "+versionsSize);
        String versionNr = Integer.toString(versionsSize+1);
        editableVersion.put(Field.VERSION, versionNr);
        editableVersion.put(Field.SERIES_ID, seriesId);
        editableVersion.put(Field.LAST_MODIFIED, Utils.getNowISO());
        editableVersion.put(Field.CREATED_DATE, Utils.getNowDate());

        boolean isStatusOpen = editableVersion.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN);

        if (isStatusOpen)
            if (!editableVersion.has(Field.CODES) || editableVersion.get(Field.CODES).size() == 0)
                return ErrorHandler.newHttpError("Published subset version must have a non-empty code list", BAD_REQUEST, LOG);

        if (versionsSize == 0) {
            LOG.debug("Since there are no versions from before, we post new version to LDS without checking validity overlap");
            ResponseEntity<JsonNode> ldsPostRE = new LDSFacade().postVersionInSeries(seriesId, versionNr, editableVersion);
            if (!ldsPostRE.getStatusCode().is2xxSuccessful())
                return resolveNonOKLDSResponse("POST first version in series "+seriesId+" ", ldsPostRE);
            return ldsPostRE;
        }

        ResponseEntity<JsonNode> isOverlappingValidityRE = isOverlappingValidity(editableVersion);
        if (!isOverlappingValidityRE.getStatusCode().is2xxSuccessful()){
            return isOverlappingValidityRE;
        }

        LOG.debug("Attempting to POST version nr "+versionNr+" of subset series "+seriesId+" to LDS");
        ResponseEntity<JsonNode> ldsPostRE = new LDSFacade().postVersionInSeries(seriesId, versionNr, editableVersion);

        if (ldsPostRE.getStatusCode().equals(CREATED)) {
            LOG.debug("Successfully POSTed version nr "+versionNr+" of subset series "+seriesId+" to LDS");
            return new ResponseEntity<>(editableVersion, CREATED);
        } else
            return resolveNonOKLDSResponse("POST version nr "+versionNr+" of series with id "+seriesId+" ", ldsPostRE);
    }

    @GetMapping("/v2/subsets/{id}/versions")
    public ResponseEntity<JsonNode> getVersions(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "true") boolean includeFuture,
            @RequestParam(defaultValue = "true") boolean includeDrafts) {
        metricsService.incrementGETCounter();
        LOG.info("GET all versions of subset with id: "+id+" includeFuture: "+includeFuture+" includeDrafts: "+includeDrafts);

        if (!Utils.isClean(id)){
            return ErrorHandler.illegalID(LOG);
        }

        ResponseEntity<JsonNode> getSeriesByIDRE = getSubsetSeriesByID(id);
        if (!getSeriesByIDRE.getStatusCode().equals(OK))
            return getSeriesByIDRE;

        JsonNode body = getSeriesByIDRE.getBody();
        if (!body.has(Field.VERSIONS))
            return ErrorHandler.newHttpError("The subset series exists, but has no versions", NOT_FOUND, LOG);

        ArrayNode versions = body.get(Field.VERSIONS).deepCopy();

        for (int i = 0; i < versions.size(); i++) {
            String versionUID = versions.get(i).asText(); // should be "version_id"
            ResponseEntity<JsonNode> versionRE = new LDSFacade().getVersionByID(versionUID);
            JsonNode versionBody = versionRE.getBody();
            versions.set(i, versionBody);
        }

        String nowDate = Utils.getNowDate();
        for (int v = versions.size() - 1; v >= 0; v--) {
            JsonNode version = versions.get(v);
            if (!includeDrafts && version.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.DRAFT))
                versions.remove(v);
            if (!includeFuture && version.get(Field.VALID_FROM).asText().compareTo(nowDate) > 0)
                versions.remove(v);
        }

        return new ResponseEntity<>(versions, OK);
    }

    /**
     * Get a subset corresponding to a given version id.
     * @param seriesID
     * @param versionID
     * @return
     */
    @GetMapping("/v2/subsets/{id}/versions/{versionID}")
    public ResponseEntity<JsonNode> getVersion(
            @PathVariable("id") String seriesID,
            @PathVariable("versionID") String versionID) {
        metricsService.incrementGETCounter();
        LOG.info("GET version "+versionID+" of subset with id "+seriesID);

        if (!Utils.isClean(seriesID))
            return ErrorHandler.illegalID(LOG);
        if (!Utils.isClean(versionID))
            return ErrorHandler.newHttpError("Illegal version ID", BAD_REQUEST, LOG);

        String[] splitUnderscore = versionID.split("_");
        if (splitUnderscore.length < 2) {
            String versionNr = splitUnderscore[0];
            try {
                int versionNrInt = Integer.parseInt(versionNr);
                versionID = String.format("%s_%d", seriesID, versionNrInt);
            } catch (NumberFormatException e){
                return ErrorHandler.newHttpError("version id must be given either as an integer indicating the version nr, or as a full versionUID on the form '{seriesUID}_{versionNr}'", BAD_REQUEST, LOG);
            }
        }
        ResponseEntity<JsonNode> versionRE = new LDSFacade().getVersionByID(versionID);
        HttpStatus status = versionRE.getStatusCode();
        if (status.equals(OK) || status.equals(NOT_FOUND))
            return versionRE;
        else
            return resolveNonOKLDSResponse("GET version by id '"+versionID+"' ", versionRE);
    }

    /**
     * Get a subset corresponding to a given version id.
     * @param versionUID
     * @return
     */
    @GetMapping("/v2/ClassificationSubsetVersion/{id}")
    public ResponseEntity<JsonNode> getVersion(@PathVariable("id") String versionUID) {
        metricsService.incrementGETCounter();
        LOG.info("GET version w id '"+versionUID+"'.");

        if (!Utils.isClean(versionUID))
            return ErrorHandler.newHttpError("Illegal characters in versionUID", BAD_REQUEST, LOG);

        ResponseEntity<JsonNode> versionRE = new LDSFacade().getVersionByID(versionUID);
        HttpStatus status = versionRE.getStatusCode();
        if (status.equals(OK) || status.equals(NOT_FOUND))
            return versionRE;
        else
            return resolveNonOKLDSResponse("GET version by id '"+versionUID+"' ", versionRE);
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
    @GetMapping("/v2/subsets/{id}/codes")
    public ResponseEntity<JsonNode> getSubsetCodes(@PathVariable("id") String id,
                                                   @RequestParam(required = false) String from,
                                                   @RequestParam(required = false) String to,
                                                   @RequestParam(defaultValue = "false") boolean includeDrafts,
                                                   @RequestParam(defaultValue = "false") boolean includeFuture) {
        LOG.info("GET codes of subset with id "+id);
        metricsService.incrementGETCounter();

        if (!Utils.isClean(id)) {
            return ErrorHandler.illegalID(LOG);
        }

        boolean isFromDate = from != null;
        boolean isToDate = to != null;

        if ((isFromDate && ! Utils.isYearMonthDay(from)) || (isToDate && ! Utils.isYearMonthDay(to))){
            return ErrorHandler.newHttpError("'from' and 'to' need to be dates on the form 'yyyy-MM-dd'", BAD_REQUEST, LOG);
        }

        if (!isFromDate && !isToDate) {
            LOG.debug("getting all codes of the latest/current version of subset "+id);
            ResponseEntity<JsonNode> versionsByIDRE = getVersions(id, includeDrafts, includeFuture);
            JsonNode responseBodyJSON = versionsByIDRE.getBody();
            if (!versionsByIDRE.getStatusCode().equals(OK))
                return resolveNonOKLDSResponse("get versions of series with id "+id+" ", versionsByIDRE);
            else if (responseBodyJSON != null){
                String date = Utils.getNowDate();
                ResponseEntity<JsonNode> codesAtRE = getSubsetCodesAt(id, date, includeFuture, includeDrafts);
                if (!codesAtRE.getStatusCode().equals(OK))
                    return resolveNonOKLDSResponse("get codesAt "+date+" in series with id "+id+" ", codesAtRE);
                ArrayNode codes = (ArrayNode) codesAtRE.getBody();
                return new ResponseEntity<>(codes, OK);
            }
            return ErrorHandler.newHttpError("response body of getSubset with id "+id+" was null, so could not get codes.", INTERNAL_SERVER_ERROR, LOG);
        }

        // If a date interval is specified using 'from' and 'to' query parameters
        ResponseEntity<JsonNode> versionsResponseEntity = getVersions(id, includeFuture, includeDrafts);
        if (!versionsResponseEntity.getStatusCode().equals(OK))
            return versionsResponseEntity;
        JsonNode versionsResponseBodyJson = versionsResponseEntity.getBody();
        LOG.debug(String.format("Getting valid codes of subset %s from date %s to date %s", id, from, to));
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Integer> codeMap = new HashMap<>();
        int nrOfVersions;
        if (versionsResponseBodyJson == null) {
            return ErrorHandler.newHttpError("Response body was null", INTERNAL_SERVER_ERROR, LOG);
        }
        if (versionsResponseBodyJson.isArray()) {
            ArrayNode versionsValidInDateRange = (ArrayNode) versionsResponseBodyJson;
            nrOfVersions = versionsValidInDateRange.size();
            LOG.debug("Nr of versions: " + nrOfVersions);

            for (int i = versionsValidInDateRange.size() - 1; i >= 0; i--) {
                JsonNode version = versionsValidInDateRange.get(i);
                if (!includeDrafts && !version.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN)) {
                    versionsValidInDateRange.remove(i);
                }
                if (isToDate && version.get(Field.VALID_FROM).asText().compareTo(to) > 0) {
                    versionsValidInDateRange.remove(i);
                }
                if (isFromDate && version.has(Field.VALID_UNTIL) && version.get(Field.VALID_UNTIL).asText().compareTo(from) < 0) {
                    versionsValidInDateRange.remove(i);
                }
            }

            // Check if first version includes fromDate, and last version includes toDate. If not, then return an empty list.

            for (JsonNode subsetVersion : versionsValidInDateRange) {
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

            ArrayNode intersectionValidCodesInIntervalArrayNode = mapper.createArrayNode();

            // Only return codes that were in every version in the interval, (=> they were always valid)
            for (String key : codeMap.keySet()) {
                int value = codeMap.get(key);
                LOG.trace("key:" + key + " value:" + value);
                if (value == nrOfVersions)
                    intersectionValidCodesInIntervalArrayNode.add(key);
            }

            LOG.debug("nr of valid codes: " + intersectionValidCodesInIntervalArrayNode.size());
            return new ResponseEntity<>(intersectionValidCodesInIntervalArrayNode, OK);
        }
        return ErrorHandler.newHttpError("Response body was null", INTERNAL_SERVER_ERROR, LOG);
    }

    /**
     * Returns all codes of the subset version that is valid on the given date.
     * Assumes only one subset is valid on any given date, with no overlap of start/end date.
     * @param id
     * @param date
     * @return
     */
    @GetMapping("/v2/subsets/{id}/codesAt")
    public ResponseEntity<JsonNode> getSubsetCodesAt(@PathVariable("id") String id,
                                                     @RequestParam String date,
                                                     @RequestParam(defaultValue = "false") boolean includeFuture,
                                                     @RequestParam(defaultValue = "false") boolean includeDrafts) {
        metricsService.incrementGETCounter();
        LOG.info("GET codes valid at date "+date+" for subset with id "+id);

        if (date != null && Utils.isClean(id) && (Utils.isYearMonthDay(date))){
            ResponseEntity<JsonNode> versionsRE = getVersions(id, includeFuture, includeDrafts);
            if (!versionsRE.getStatusCode().equals(OK)){
                return resolveNonOKLDSResponse("Call for versions of subset with id "+id+" ", versionsRE);
            }
            JsonNode versionsResponseBodyJSON = versionsRE.getBody();
            if (versionsResponseBodyJSON != null){
                if (versionsResponseBodyJSON.isArray()) {
                    ArrayNode versionsArrayNode = (ArrayNode) versionsResponseBodyJSON;
                    for (JsonNode versionJsonNode : versionsArrayNode) {
                        String entryValidFrom = versionJsonNode.get(Field.VALID_FROM).textValue();
                        String entryValidUntil = versionJsonNode.has(Field.VALID_UNTIL) ? versionJsonNode.get(Field.VALID_UNTIL).textValue() : null;
                        if (entryValidFrom.compareTo(date) <= 0 && (entryValidUntil == null || entryValidUntil.compareTo(date) >= 0) ){
                            JsonNode codes = versionJsonNode.get(Field.CODES);
                            return new ResponseEntity<>(codes, OK);
                        }
                    }
                    return new ResponseEntity<>(new ObjectMapper().createArrayNode(), OK);
                }
                return ErrorHandler.newHttpError("versions response body was not array", INTERNAL_SERVER_ERROR, LOG);
            }
            return ErrorHandler.newHttpError("versions response body was null", INTERNAL_SERVER_ERROR, LOG);
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
                BAD_REQUEST,
                LOG);
    }

    @GetMapping("/v2/subsets/schema")
    public ResponseEntity<JsonNode> getSchema(){
        metricsService.incrementGETCounter();
        LOG.info("GET schema for subsets series");
        return new LDSFacade().getSubsetSeriesSchema();
    }

    void deleteAllSeries(){
        new LDSFacade().deleteAllSubsetSeries();
    }

    void deleteSeriesById(String id){
        new LDSFacade().deleteSubsetSeries(id);
    }

    private static JsonNode cleanSeries(JsonNode subsetSeries){
        // Replace "/ClassificationSubsetVersion/id" with "subsets/id/versions/nr"
        ObjectNode editableSeries = subsetSeries.deepCopy();
        ArrayNode versions = editableSeries.get(Field.VERSIONS).deepCopy();
        ArrayNode newVersions = new ObjectMapper().createArrayNode();
        String id = editableSeries.get(Field.ID).asText();
        for (int i = 0; i < versions.size(); i++) {
            JsonNode version = versions.get(i);
            String versionPath = version.asText(); // should be "/ClassificationSubsetVersion/{version_id}", since this is how LDS links a resource of a different type
            String[] splitBySlash = versionPath.split("/");
            assert splitBySlash[0].isBlank() : "Index 0 in the array that splits the versionPath by '/' is not blank";
            assert splitBySlash[1].equals("ClassificationSubsetVersion") : "Index 1 in the array that splits the versionPath by '/' is not 'ClassificationSubsetVersion'"; //TODO: these checks should be removed later when i know it works
            String versionUID = splitBySlash[2];
            newVersions.add("/subsets/"+id+"/versions/"+versionUID);
        }
        editableSeries.set(Field.VERSIONS, newVersions);
        return editableSeries;
    }

    /**
     *
     * @param description of what the call to LDS was
     * @param ldsRE response entity gotten from the LDS instance
     * @return
     */
    private static ResponseEntity<JsonNode> resolveNonOKLDSResponse(String description, ResponseEntity<JsonNode> ldsRE){
        String body = "";
        if (ldsRE.hasBody())
            body = ldsRE.getBody().asText();
        body = body.replaceAll("\n", "\\n_");
        return ErrorHandler.newHttpError(
                description+" returned non-200 status code "+ldsRE.getStatusCode().toString()+". Body: "+body,
                INTERNAL_SERVER_ERROR,
                LOG);
    }

}
