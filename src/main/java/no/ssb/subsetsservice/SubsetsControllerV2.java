package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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

        LOG.info("GET subsets includeDrafts="+includeDrafts+" includeFuture="+includeFuture+" includeExpired="+includeExpired);
        LDSFacade ldsFacade = new LDSFacade();
        ResponseEntity<JsonNode> subsetSeriesRE = ldsFacade.getAllSubsetSeries();
        ArrayNode subsetSeriesArray = subsetSeriesRE.getBody().deepCopy();
        String nowDate = Utils.getNowDate();
        for (int i = 0; i < subsetSeriesArray.size(); i++) {
            subsetSeriesArray.set(i, cleanSeries(subsetSeriesArray.get(i))); //TODO: performance wise it would be best not to do this
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
        return new ResponseEntity<>(subsetSeriesArray, HttpStatus.OK);
    }

    /**
     * Create a new ClassificationSubsetSeries resource, with no versions yet
     * @param subsetSeriesJson
     * @return
     */
    @PostMapping("/v2/subsets")
    public ResponseEntity<JsonNode> postSubset(@RequestBody JsonNode subsetSeriesJson) {
        metricsService.incrementPOSTCounter();

        if (subsetSeriesJson == null)
            return ErrorHandler.newHttpError("POST: Can not create subset from empty body", HttpStatus.BAD_REQUEST, LOG);

        ObjectNode editableSubsetSeries = subsetSeriesJson.deepCopy();
        subsetSeriesJson = null;

        if (!editableSubsetSeries.has(Field.ID))
            return ErrorHandler.newHttpError("Subset series must have field ID", HttpStatus.BAD_REQUEST, LOG);

        String id = editableSubsetSeries.get(Field.ID).textValue();
        LOG.info("POST subset with id "+id);

        if (!Utils.isClean(id))
            return ErrorHandler.illegalID(LOG);

        boolean subsetExists = new LDSFacade().existsSubsetWithID(id);
        if (subsetExists)
            return ErrorHandler.newHttpError(
                    "POST: Can not create subset. ID already in use",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        //TODO: Validate that body is a valid subset series, somehow

        String isoNowDate = Utils.getNowDate();
        editableSubsetSeries.put(Field.LAST_UPDATED_DATE, isoNowDate);
        editableSubsetSeries.put(Field.CREATED_DATE, isoNowDate);

        editableSubsetSeries.put(Field.CLASSIFICATION_TYPE, Field.SUBSET);

        ResponseEntity<JsonNode> responseEntity = new LDSFacade().createSubset(editableSubsetSeries, id);
        if (responseEntity.getStatusCode().equals(HttpStatus.CREATED)){
            responseEntity = new ResponseEntity<>(editableSubsetSeries, HttpStatus.CREATED);
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
        if (status.equals(HttpStatus.OK)) {
            JsonNode series = cleanSeries(subsetSeriesByIDRE.getBody());
            return new ResponseEntity<>(series, HttpStatus.OK);
        } else if (status.equals(HttpStatus.NOT_FOUND))
            return subsetSeriesByIDRE;
        else
            return resolveNonOKLDSResponse("GET subsetSeries w id '"+id+"'", subsetSeriesByIDRE);
    }

    /**
     * Use this to make edits to the ClassificationSubsetSeries, except the versions array.
     * This is the information that is in common for all the versions.
     * Changes to the versions array will not be accepted.
     * To add a new version to the versions array you must instead use POST /v2/subsets/{id}/versions
     * To edit an existing version you must use PUT /v2/subsets/{id}/versions/{version_id}
     * @param id
     * @param newVersionOfSeries
     * @return
     */
    @PutMapping(value = "/v2/subsets/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> putSubset(@PathVariable("id") String id, @RequestBody JsonNode newVersionOfSeries) {
        metricsService.incrementPUTCounter();
        LOG.info("PUT subset series with id "+id);

        if (!Utils.isClean(id))
            return ErrorHandler.illegalID(LOG);

        ResponseEntity<JsonNode> getSeriesRE = new LDSFacade().getSubsetSeries(id);

        if (getSeriesRE.getStatusCode().equals(HttpStatus.NOT_FOUND))
            return ErrorHandler.newHttpError(
                    "Can not PUT (edit) a subset that does not exist from before. POST the subset instead if you wish to create it",
                    HttpStatus.BAD_REQUEST,
                    LOG);

        if (!getSeriesRE.getStatusCode().equals(HttpStatus.OK)) {
            return resolveNonOKLDSResponse("GET subsetSeries w id '"+id+"'", getSeriesRE);
        }

        JsonNode currentVersionOfSeries = getSeriesRE.getBody();
        ObjectNode editableNewVersionOfSeries = newVersionOfSeries.deepCopy();
        newVersionOfSeries = null;

        //TODO: Validate that the incoming subset version contains the legal fields?

        ArrayNode versionsArray = currentVersionOfSeries.has(Field.VERSIONS) ? currentVersionOfSeries.get(Field.VERSIONS).deepCopy() : new ObjectMapper().createArrayNode();

        // This makes it so that you can not edit the versions list by PUTing a SubsetSeries.
        // In other words, you must PUT /ns/ClassificationSubsetSeries/{id}/versions/ClassificationSubsetVersion/{subset_version_id}
        // and POST /ns/ClassificationSubsetVersion/{subset_version_id}
        editableNewVersionOfSeries.set(Field.VERSIONS, versionsArray);

        assert editableNewVersionOfSeries.has(Field.ID) : "Subset series did not have the field '"+Field.ID+"'.";

        editableNewVersionOfSeries.put(Field.LAST_MODIFIED, Utils.getNowISO());

        String oldID = currentVersionOfSeries.get(Field.ID).asText();
        String newID = editableNewVersionOfSeries.get(Field.ID).asText();

        boolean sameID = oldID.equals(newID);
        boolean sameIDAsRequest = newID.equals(id);
        boolean consistentID = sameID && sameIDAsRequest;
        if (!consistentID){
            StringBuilder errorStringBuilder = new StringBuilder();
            if (!sameID)
                errorStringBuilder.append("- ID of submitted subset series (").append(newID).append(") was not the same as id of stored subset (").append(oldID).append("). ");
            if(!sameIDAsRequest)
                errorStringBuilder.append("- ID of submitted subset series (").append(newID).append(") was not the same as id in request param (").append(id).append("). ");
            return ErrorHandler.newHttpError(
                    errorStringBuilder.toString(),
                    HttpStatus.BAD_REQUEST,
                    LOG);
        }

        boolean thisSeriesIsPublished = currentVersionOfSeries.get(Field.ADMINISTRATIVE_STATUS).asText().equals(Field.OPEN);

        if (thisSeriesIsPublished) {
            Iterator<String> prevPatchFieldNames = currentVersionOfSeries.fieldNames();
            Iterator<String> newPatchFieldNames = editableNewVersionOfSeries.fieldNames();

            String[] changeableFieldsInPublishedSeries = {
                    Field.VERSION_RATIONALE,
                    Field.VALID_FROM,
                    Field.VALID_UNTIL,
                    Field.VERSION_VALID_UNTIL,
                    Field.LAST_UPDATED_BY,
                    Field.LAST_UPDATED_DATE};
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
                            .append(currentVersionOfSeries.get(Field.ID).asText())
                            .append("' does not contain the field '")
                            .append(field)
                            .append("' that is present in the old patch of this version (")
                            .append(currentVersionOfSeries.get(Field.ID).asText())
                            .append("), and is a field that is not allowed to change when a version is already published. ");
                    allSameFields = false;
                }
            }

            while (allSameFields && newPatchFieldNames.hasNext()) {
                String field = newPatchFieldNames.next();
                if (!currentVersionOfSeries.has(field) && !changeableFieldsList.contains(field)) {
                    fieldErrorBuilder
                            .append("- The previous patch of version (")
                            .append(currentVersionOfSeries.get(Field.VERSION).asText())
                            .append(") of the subset with ID '").append(currentVersionOfSeries.get(Field.ID).asText())
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
                        HttpStatus.BAD_REQUEST,
                        LOG);
            }

            newPatchFieldNames = editableNewVersionOfSeries.fieldNames();
            while (newPatchFieldNames.hasNext()) {
                String field = newPatchFieldNames.next();
                if (!changeableFieldsList.contains(field)) {
                    if (!currentVersionOfSeries.get(field).asText().equals(editableNewVersionOfSeries.get(field).asText())) {
                        return ErrorHandler.newHttpError(
                                "The version of the subset you are trying to change is published, which means you can only change validUntil and versionRationale.",
                                HttpStatus.BAD_REQUEST,
                                LOG);
                    }
                }
            }
        }

        ResponseEntity<JsonNode> responseEntity = new LDSFacade().editSeries(editableNewVersionOfSeries, id);
        if (responseEntity.getStatusCode().equals(HttpStatus.OK)){
            responseEntity = new ResponseEntity<>(editableNewVersionOfSeries, HttpStatus.OK);
        }
        return responseEntity;
    }

    @PutMapping(value = "/v2/subsets/{id}/versions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> putSubsetVersion(@PathVariable("id") String id, @RequestBody JsonNode version) {

        ResponseEntity<JsonNode> getSeriesByIDRE = getSubsetSeriesByID(id);
        if (!getSeriesByIDRE.getStatusCode().equals(HttpStatus.OK))
            return getSeriesByIDRE;
        ObjectNode editableVersion = version.deepCopy();

        //TODO: Validate the 'version' input's validity: Does it contain the right fields with the right values?

        //TODO: Make sure validFrom is before validUntil

        //TODO: Make sure validFrom and validUntil does not overlap existing versions

        //TODO: If OPEN and is new latest version, set validUntil of previous version to be == this version's validFrom

        //TODO: If OPEN and is new first version, set validUntil of this version to be == validFrom of next version

        JsonNode series = getSeriesByIDRE.getBody();
        if (!series.has(Field.VERSIONS))
            return new ResponseEntity<>(new ObjectMapper().createArrayNode(), HttpStatus.OK);

        editableVersion.put(Field.SUBSET_ID, id);

        int versionsSize = series.get(Field.VERSIONS).size();
        String versionID = Integer.toString(versionsSize+1);
        editableVersion.put(Field.VERSION, versionID);

        String nowDate = Utils.getNowDate();
        editableVersion.put(Field.LAST_MODIFIED, nowDate);
        editableVersion.put(Field.CREATED_DATE, nowDate);

        //TODO: IF this has administrative status OPEN, then set the SubsetSeries' status to OPEN too

        //TODO: If this has admin status OPEN, make sure there are codes present in the codes list

        ResponseEntity<JsonNode> ldsPUT = new LDSFacade().putVersionInSeries(id, versionID, editableVersion);

        if (ldsPUT.getStatusCode().equals(HttpStatus.OK))
            return new ResponseEntity<>(editableVersion, HttpStatus.OK);
        else
            return ldsPUT;
    }

    @GetMapping("/v2/subsets/{id}/versions")
    public ResponseEntity<JsonNode> getVersions(
            @PathVariable("id") String id,
            @RequestParam(defaultValue = "true") boolean includeFuture,
            @RequestParam(defaultValue = "true") boolean includeDrafts,
            @RequestParam(defaultValue = "true") boolean rankedUrnOnly) {
        metricsService.incrementGETCounter();
        LOG.info("GET all versions of subset with id: "+id+" includeFuture: "+includeFuture+" includeDrafts: "+includeDrafts);

        if (!Utils.isClean(id)){
            return ErrorHandler.illegalID(LOG);
        }

        ResponseEntity<JsonNode> getSeriesByIDRE = getSubsetSeriesByID(id);
        if (!getSeriesByIDRE.getStatusCode().equals(HttpStatus.OK))
            return getSeriesByIDRE;

        JsonNode body = getSeriesByIDRE.getBody();
        if (!body.has(Field.VERSIONS))
            return ErrorHandler.newHttpError("The subset series exists, but has no versions", HttpStatus.NOT_FOUND, LOG);

        ArrayNode versions = body.get(Field.VERSIONS).deepCopy();

        for (int i = 0; i < versions.size(); i++) {
            String versionPath = versions.get(i).asText(); // should be "/ClassificationSubsetVersion/{version_id}"
            String[] splitBySlash = versionPath.split("/");
            assert splitBySlash[0].isBlank() : "Index 0 in the array that splits the versionPath by '/' is not blank";
            assert splitBySlash[1].equals("ClassificationSubsetVersion") : "Index 1 in the array that splits the versionPath by '/' is not 'ClassificationSubsetVersion'"; //TODO: these checks should be removed later when i know it works
            String versionUID = splitBySlash[2];
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

        return new ResponseEntity<>(versions, HttpStatus.OK);
    }

    /**
     * Get a subset corresponding to a given version id.
     * @param id
     * @param version
     * @return
     */
    @GetMapping("/v2/subsets/{id}/versions/{version}")
    public ResponseEntity<JsonNode> getVersion(
            @PathVariable("id") String id,
            @PathVariable("version") String version) {
        metricsService.incrementGETCounter();
        LOG.info("GET version "+version+" of subset with id "+id);

        if (!Utils.isClean(id) || !Utils.isClean(version)){
            return ErrorHandler.illegalID(LOG);
        }
        String versionUID = id+"_"+version;
        ResponseEntity<JsonNode> versionRE = new LDSFacade().getVersionByID(versionUID);
        HttpStatus status = versionRE.getStatusCode();
        if (status.equals(HttpStatus.OK) || status.equals(HttpStatus.NOT_FOUND))
            return versionRE;
        else
            return resolveNonOKLDSResponse("GET version by id '"+versionUID+"' ", versionRE);
    }

    /**
     * Get a subset corresponding to a given version id.
     * @param id
     * @return
     */
    @GetMapping("/v2/ClassificationSubsetVersion/{id}")
    public ResponseEntity<JsonNode> getVersion(@PathVariable("id") String id) {
        metricsService.incrementGETCounter();
        LOG.info("GET version w id '"+id+"'.");

        if (!Utils.isClean(id)){
            return ErrorHandler.illegalID(LOG);
        }
        ResponseEntity<JsonNode> versionRE = new LDSFacade().getVersionByID(id);
        HttpStatus status = versionRE.getStatusCode();
        if (status.equals(HttpStatus.OK) || status.equals(HttpStatus.NOT_FOUND))
            return versionRE;
        else
            return resolveNonOKLDSResponse("GET version by id '"+id+"' ", versionRE);
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
                                                   @RequestParam(defaultValue = "false") boolean includeFuture,
                                                   @RequestParam(defaultValue = "false") boolean rankedUrnOnly) {
        LOG.info("GET codes of subset with id "+id);
        metricsService.incrementGETCounter();

        if (Utils.isClean(id)){
            boolean isFromDate = from != null;
            boolean isToDate = to != null;
            if (!isFromDate && !isToDate) {
                LOG.debug("getting all codes of the latest/current version of subset "+id);
                ResponseEntity<JsonNode> versionsByIDRE = getVersions(id, includeDrafts, includeFuture, rankedUrnOnly);
                JsonNode responseBodyJSON = versionsByIDRE.getBody();
                if (!versionsByIDRE.getStatusCode().equals(HttpStatus.OK))
                    return versionsByIDRE;
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

            if ((!isFromDate || Utils.isYearMonthDay(from)) && (!isToDate || Utils.isYearMonthDay(to))) { // If a date is given as param, it must be valid format
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
                        if (isToDate && lastVersion.has(Field.VALID_UNTIL) && !lastVersion.get(Field.VALID_UNTIL).isNull() && !lastVersion.get(Field.VALID_UNTIL).asText().isBlank()) {
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
    @GetMapping("/v2/subsets/{id}/codesAt")
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

    @GetMapping("/v2/subsets/schema")
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

    private static JsonNode cleanSeries(JsonNode subsetSeries){
        return subsetSeries;
    }

    private static ResponseEntity<JsonNode> resolveNonOKLDSResponse(String description, ResponseEntity<JsonNode> ldsRE){
        String body = "";
        if (ldsRE.hasBody())
            body = ldsRE.getBody().asText();
        body = body.replaceAll("\n", "\\n_");
        return ErrorHandler.newHttpError(
                description+" returned non-200 status code "+ldsRE.getStatusCode().toString()+". Body: "+body,
                HttpStatus.INTERNAL_SERVER_ERROR,
                LOG);
    }

}
