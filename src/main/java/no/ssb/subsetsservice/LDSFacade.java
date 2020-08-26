package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;

public class LDSFacade implements LDSInterface {

    String API_LDS = "";
    String SUBSETS_API = "/ns/ClassificationSubset";

    LDSFacade(){}

    LDSFacade(String API_LDS){
        this.API_LDS = API_LDS;
    }

    public List<String> getAllSubsetIDs() throws HttpClientErrorException {

        ResponseEntity<JsonNode> allSubsetsRE = getLastUpdatedVersionOfAllSubsets();

        if (allSubsetsRE.getStatusCodeValue() == HttpStatus.OK.value()) {
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
            }
        }
        throw new HttpClientErrorException(allSubsetsRE.getStatusCode());
    }

    public ResponseEntity<JsonNode> getLastUpdatedVersionOfAllSubsets(){
        return new LDSConsumer().getFrom(SUBSETS_API+"");
    }

    public ResponseEntity<JsonNode> getTimelineOfSubset(String id){
        return new LDSConsumer(API_LDS).getFrom(SUBSETS_API+"/"+id+"?timeline");
    }

    public boolean existsSubsetWithID(String id){
        return new LDSConsumer(API_LDS).getFrom(SUBSETS_API+"/"+id).getStatusCode().equals(HttpStatus.OK);
    }

    public ResponseEntity<JsonNode> getClassificationSubsetSchema(){
        return new LDSConsumer().getFrom(SUBSETS_API+"/?schema");
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
       new LDSConsumer(API_LDS).delete(SUBSETS_API+"/" + id);
    }

    public void deleteAllSubsets(){
        List<String> idList = getAllSubsetIDs();
        idList.forEach(this::deleteSubset);
    }
}
