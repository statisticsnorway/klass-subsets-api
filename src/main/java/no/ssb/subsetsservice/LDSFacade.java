package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;

public class LDSFacade implements LDSInterface {

    String API_LDS = "";
    String SUBSETS_API =    "/ns/ClassificationSubset";
    String SERIES_API =     "/ns/ClassificationSubsetSeries";
    String VERSIONS_API =   "/ns/ClassificationSubsetVersion";

    LDSFacade(){}

    LDSFacade(String API_LDS){
        this.API_LDS = API_LDS;
    }

    public List<String> getAllSubsetIDs() throws HttpClientErrorException {

        ResponseEntity<JsonNode> allSubsetsRE = getLastUpdatedVersionOfAllSubsets();

        if (allSubsetsRE.getStatusCode().equals(HttpStatus.OK)) {
            JsonNode ldsREBody = allSubsetsRE.getBody();
            if (ldsREBody != null) {
                if (ldsREBody.isArray()) {
                    ArrayNode ldsAllSubsetsArrayNode = (ArrayNode) ldsREBody;
                    List<String> idList = new ArrayList<>(ldsAllSubsetsArrayNode.size());
                    for (JsonNode jsonNode : ldsAllSubsetsArrayNode) {
                        idList.add(jsonNode.get(Field.ID).asText());
                    }
                    return idList;
                }
                throw new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "GET all subsets body was not ArrayNode");
            }
            throw new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "GET all subsets body was null");
        }
        throw new HttpClientErrorException(allSubsetsRE.getStatusCode());
    }

    public ResponseEntity<JsonNode> getVersionsInSubsetWithID(String subsetId){
        ResponseEntity<JsonNode> seriesRE = new LDSConsumer(API_LDS).getFrom(SERIES_API+"/"+subsetId);
        JsonNode subsetSeriesJsonNode = seriesRE.getBody();
        ArrayNode versionArrayNode = subsetSeriesJsonNode.get("versions").deepCopy();
        /*
        for (JsonNode version : versionArrayNode){
            String versionID = version.get("_links").get("self").get("href").asText();
        }
        */
        return new ResponseEntity<>(versionArrayNode, HttpStatus.OK);
    }

    /**
     * Get a specific subset version by its UID, without going through the subset series.
     * @param versionId is the UID for the version, unique among all versions, that is used in LDS
     * @return
     */
    public ResponseEntity<JsonNode> getVersionByID(String versionId){
        return new LDSConsumer(API_LDS).getFrom(VERSIONS_API+"/"+versionId);
    }

    public ResponseEntity<JsonNode> getSubsetSeries(String id){
        return new LDSConsumer(API_LDS).getFrom(SERIES_API+"/"+id);
    }

    public ResponseEntity<JsonNode> getAllSubsetSeries(){
        return new LDSConsumer(API_LDS).getFrom(SERIES_API+"");
    }

    public ResponseEntity<JsonNode> getLastUpdatedVersionOfAllSubsets(){
        return new LDSConsumer(API_LDS).getFrom(SUBSETS_API+"");
    }

    public ResponseEntity<JsonNode> getTimelineOfSubset(String id){
        return new LDSConsumer(API_LDS).getFrom(SUBSETS_API+"/"+id+"?timeline");
    }

    public boolean existsSubsetWithID(String id){
        return new LDSConsumer(API_LDS).getFrom(SUBSETS_API+"/"+id).getStatusCode().equals(HttpStatus.OK);
    }

    public ResponseEntity<JsonNode> getClassificationSubsetSchema(){
        return new LDSConsumer(API_LDS).getFrom(SUBSETS_API+"/?schema");
    }

    public ResponseEntity<JsonNode> editSubset(JsonNode subset, String id){
        return new LDSConsumer(API_LDS).putTo(SUBSETS_API+"/" + id, subset);
    }

    public ResponseEntity<JsonNode> createSubset(JsonNode subset, String id){
        return new LDSConsumer(API_LDS).postTo(SUBSETS_API+"/" + id, subset);
    }

    public boolean healthReady() {
        return new LDSConsumer(API_LDS).getFrom("/health/ready").getStatusCode().equals(HttpStatus.OK);
    }

    public void deleteSubset(String id) {
        String url = SUBSETS_API+"/"+id;
       new LDSConsumer(API_LDS).delete(url);
    }

    public ResponseEntity<JsonNode> editSeries(JsonNode series, String id) {
        return new LDSConsumer(API_LDS).putTo(SERIES_API+"/" + id, series);
    }

    public void deleteAllSubsets(){
        List<String> idList = getAllSubsetIDs();
        LoggerFactory.getLogger(LDSFacade.class).info("DELETE all "+idList.size()+" subset(s) from LDS");
        idList.forEach(this::deleteSubset);
    }

    public ResponseEntity<JsonNode> putVersionInSeries(String id, String versionID, JsonNode versionJsonNode) {
        String versionUID = id+"_"+versionID;
        ResponseEntity<JsonNode> putVersionRE = new LDSConsumer(API_LDS).putTo(VERSIONS_API+"/"+versionUID, versionJsonNode);
        if (!putVersionRE.getStatusCode().equals(HttpStatus.OK)){
            return ErrorHandler.newHttpError("Trying to PUT a subset version to LDS failed with status code "+putVersionRE.getStatusCode(), putVersionRE.getStatusCode(), LoggerFactory.getLogger(LDSFacade.class));
        }
        ResponseEntity<JsonNode> putLinkRE = new LDSConsumer(API_LDS).putTo(SERIES_API+"/"+id+"/versions/ClassificationSubsetVersion/"+versionUID, new ObjectMapper().createObjectNode());
        if (!putLinkRE.getStatusCode().equals(HttpStatus.OK)){
            return ErrorHandler.newHttpError("Trying to PUT a link between the subset Series and subset Version in LDS failed, with status code "+putLinkRE.getStatusCode(), putLinkRE.getStatusCode(), LoggerFactory.getLogger(LDSFacade.class));
        }
        return putLinkRE;
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
        return null;
    }
}
