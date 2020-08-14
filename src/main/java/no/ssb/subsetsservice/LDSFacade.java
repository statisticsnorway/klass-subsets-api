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
        return new LDSConsumer().getFrom("");
    }

    public ResponseEntity<JsonNode> getTimelineOfSubset(String id){
        return new LDSConsumer(API_LDS).getFrom("/"+id+"?timeline");
    }

    public boolean existsSubsetWithID(String id){
        return new LDSConsumer(API_LDS).getFrom("/"+id).getStatusCode().equals(HttpStatus.OK);
    }

    public ResponseEntity<JsonNode> getClassificationSubsetSchema(){
        return new LDSConsumer().getFrom("/?schema");
    }

    public ResponseEntity<JsonNode> editSubset(JsonNode subset, String id){
        return new LDSConsumer(API_LDS).putTo("/" + id, subset);
    }

    public ResponseEntity<JsonNode> createSubset(JsonNode subset, String id){
        return new LDSConsumer(API_LDS).postTo("/" + id, subset);
    }

    @Override
    public boolean pingLDSSubsets() {
        return getLastUpdatedVersionOfAllSubsets().getStatusCode().equals(HttpStatus.OK);
    }
}
