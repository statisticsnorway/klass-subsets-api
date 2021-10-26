package no.ssb.subsetsservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/**
 * This interface presents some operations that can be made against a connection
 * to an instance of Linked Data Store
 */
public interface BackendInterface {

    ResponseEntity<JsonNode> initializeBackend();

    ResponseEntity<JsonNode> getVersionByID(String versionId);

    ResponseEntity<JsonNode> getSubsetSeries(String id);

    ResponseEntity<JsonNode> getAllSubsetSeries();

    boolean healthReady();

    ResponseEntity<JsonNode> editSeries(JsonNode newVersionOfSeries, String seriesID);

    ResponseEntity<JsonNode> createSubsetSeries(JsonNode subset, String id);

    ResponseEntity<JsonNode> saveVersionInSeries(String id, String versionID, JsonNode versionNode);

    ResponseEntity<JsonNode> resolveVersionLink(String versionLink);

    boolean existsSubsetSeriesWithID(String id);

    ResponseEntity<JsonNode> getSubsetSeriesDefinition();

    ResponseEntity<JsonNode> getSubsetSeriesSchema();

    ResponseEntity<JsonNode> getSubsetVersionsDefinition();

    ResponseEntity<JsonNode> getSubsetVersionSchema();

    ResponseEntity<JsonNode> getSubsetCodeDefinition();

    ResponseEntity<JsonNode> deleteAllSubsetSeries();

    ResponseEntity<JsonNode> deleteSubsetSeries(String id);

    void deleteSubsetVersion(String subsetId, String versionUid);

    ResponseEntity<JsonNode> editVersion(ObjectNode editablePutVersion);
}
