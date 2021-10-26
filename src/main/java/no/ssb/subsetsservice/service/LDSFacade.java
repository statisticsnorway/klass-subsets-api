package no.ssb.subsetsservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.ssb.subsetsservice.entity.Field;
import no.ssb.subsetsservice.controller.ErrorHandler;
import no.ssb.subsetsservice.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.springframework.http.HttpStatus.OK;

public class LDSFacade implements BackendInterface {

    String API_LDS = "";
    String SUBSETS_API =    "/ns/ClassificationSubset";
    String SERIES_API =     "/ns/ClassificationSubsetSeries";
    String VERSIONS_API =   "/ns/ClassificationSubsetVersion";

    LDSFacade(){}

    LDSFacade(String API_LDS){
        this.API_LDS = API_LDS;
    }

    @Override
    public ResponseEntity<JsonNode> initializeBackend() {
        return new ResponseEntity<>(OK);
    }

    /**
     * Get a specific subset version by its UID, without going through the subset series.
     * @param versionId is the UID for the version, unique among all versions, that is used in LDS
     * @return
     */
    public ResponseEntity<JsonNode> getVersionByID(String versionId) {
        return new LDSConsumer(API_LDS).getFrom(VERSIONS_API+"/"+versionId);
    }

    public ResponseEntity<JsonNode> getSubsetSeries(String id) {
        return new LDSConsumer(API_LDS).getFrom(SERIES_API+"/"+id);
    }

    public ResponseEntity<JsonNode> getAllSubsetSeries(){
        return new LDSConsumer(API_LDS).getFrom(SERIES_API+"");
    }

    public boolean existsSubsetSeriesWithID(String id) {
        return new LDSConsumer(API_LDS).getFrom(SERIES_API+"/"+id).getStatusCode().equals(OK);
    }

    public ResponseEntity<JsonNode> createSubsetSeries(JsonNode subset, String id) {
        return new LDSConsumer(API_LDS).postTo(SERIES_API+"/" + id, subset);
    }

    public boolean healthReady() {
        return new LDSConsumer(API_LDS).getFrom("/health/ready").getStatusCode().equals(OK);
    }

    public ResponseEntity<JsonNode> editSeries(JsonNode series, String id) {
        return new LDSConsumer(API_LDS).putTo(SERIES_API+"/" + id, series);
    }

    public ResponseEntity<JsonNode> saveVersionInSeries(String seriesID, String versionNr, JsonNode versionJsonNode) {
        Logger logger = LoggerFactory.getLogger(LDSFacade.class);
        String versionUID = seriesID+"_"+versionNr;
        logger.debug("Attempting to POST version with UID "+versionUID+" to LDS");
        ResponseEntity<JsonNode> postVersionRE = new LDSConsumer(API_LDS).postTo(VERSIONS_API+"/"+versionUID, versionJsonNode);
        if (!postVersionRE.getStatusCode().equals(HttpStatus.CREATED)) {
            return postVersionRE;
        }
        logger.debug("Attempting to PUT link from series with seriesID "+seriesID+" to version with UID "+versionUID+" to LDS");
        ResponseEntity<JsonNode> putLinkRE = new LDSConsumer(API_LDS).putTo(SERIES_API+"/"+seriesID+"/versions/ClassificationSubsetVersion/"+versionUID, new ObjectMapper().createObjectNode());
        if (!putLinkRE.getStatusCode().equals(OK)) {
            return ErrorHandler.newHttpError("Trying to PUT a link between the subset Series "+seriesID+" and subset Version "+versionUID+" in LDS failed, with status code "+putLinkRE.getStatusCode(), putLinkRE.getStatusCode(), logger);
        }
        logger.debug("Successfully posted and linked version with UID "+versionUID+" to subset series with id "+seriesID);
        return new ResponseEntity<>(versionJsonNode, HttpStatus.CREATED);
    }

    /**
     * versionLink should be on the form "/ClassificationSubsetVersion/{versionUID}"
     * @param versionLink
     * @return
     */
    public ResponseEntity<JsonNode> resolveVersionLink(String versionLink) {
        String[] splitOnSlash = versionLink.split("/");
        if (!splitOnSlash[1].equals("ClassificationSubsetVersion"))
            throw new IllegalArgumentException("versionLink must be on format '/ClassificationSubsetVersion/{versionUID}'");
        String versionID = splitOnSlash[2];
        if (!Utils.isClean(versionID))
            throw new IllegalArgumentException("versionID must be clean (no special characters except '-' and '_')");
        ResponseEntity<JsonNode> versionRE = new LDSFacade().getVersionByID(versionID);
        return versionRE;
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetSeriesDefinition() {
        ResponseEntity<JsonNode> versionSchemaRE = getSubsetSeriesSchema();
        if (!versionSchemaRE.getStatusCode().is2xxSuccessful()){
            return versionSchemaRE;
        }
        JsonNode definition = versionSchemaRE.getBody().get("definitions").get("ClassificationSubsetSeries");
        return new ResponseEntity<>(definition, OK);
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetSeriesSchema() {
        return new LDSConsumer(API_LDS).getFrom(SERIES_API+"/?schema");
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetVersionsDefinition() {
        ResponseEntity<JsonNode> versionSchemaRE = new LDSConsumer(API_LDS).getFrom(VERSIONS_API+"/?schema");
        if (!versionSchemaRE.getStatusCode().is2xxSuccessful()){
            return versionSchemaRE;
        }
        JsonNode definition = versionSchemaRE.getBody().get("definitions").get("ClassificationSubsetVersion");
        return new ResponseEntity<>(definition, OK);
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetVersionSchema() {
        return new LDSConsumer(API_LDS).getFrom(VERSIONS_API+"/?schema");
    }

    @Override
    public ResponseEntity<JsonNode> getSubsetCodeDefinition() {
        ResponseEntity<JsonNode> versionSchemaRE = new LDSConsumer(API_LDS).getFrom(VERSIONS_API+"/?schema");
        if (!versionSchemaRE.getStatusCode().is2xxSuccessful()) {
            return versionSchemaRE;
        }
        JsonNode definition = versionSchemaRE.getBody().get("definitions").get("ClassificationSubsetCode");
        return new ResponseEntity<>(definition, OK);
    }

    @Override
    public ResponseEntity<JsonNode> deleteAllSubsetSeries() {
        ResponseEntity<JsonNode> getAllRE = getAllSubsetSeries();
        ArrayNode allSeriesArrayNode = (ArrayNode) getAllRE.getBody();
        for (JsonNode series : allSeriesArrayNode) {
            String id = series.get(Field.ID).asText();
            deleteSubsetSeries(id);
        }
        return new ResponseEntity<>(OK);
    }

    @Override
    public ResponseEntity<JsonNode> deleteSubsetSeries(String id) {
        ResponseEntity<JsonNode> getSeriesRE = getSubsetSeries(id);
        ArrayNode subsetVersionsArrayNode = (ArrayNode)getSeriesRE.getBody().get(Field.VERSIONS);
        for (JsonNode versionLink : subsetVersionsArrayNode) {
            String[] splitSlash = versionLink.asText().split("/");
            String versionUID = splitSlash[splitSlash.length-1];
            new LDSConsumer(API_LDS).delete(VERSIONS_API+"/"+versionUID);
        }
        new LDSConsumer(API_LDS).delete(SERIES_API+"/"+id);
        return new ResponseEntity<>(OK);
    }

    @Override
    public void deleteSubsetVersion(String subsetId, String versionUidToDelete) {
        new LDSConsumer(API_LDS).delete(SERIES_API + "/" + subsetId + "/versions/ClassificationSubsetVersion/"+versionUidToDelete);
        new LDSConsumer(API_LDS).delete(VERSIONS_API + "/" + versionUidToDelete);
    }

    @Override
    public ResponseEntity<JsonNode> editVersion(ObjectNode editablePutVersion) {
        String seriesUid = editablePutVersion.get(Field.SUBSET_ID).asText();
        String versionNr = editablePutVersion.get(Field.VERSION_ID).asText();
        String versionUid = seriesUid+"_"+versionNr;
        return new LDSConsumer(API_LDS).putTo(VERSIONS_API+"/" + versionUid, editablePutVersion);
    }
}
