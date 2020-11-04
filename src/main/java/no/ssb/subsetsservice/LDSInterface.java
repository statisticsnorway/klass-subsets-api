package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

/**
 * This interface presents some operations that can be made against a connection
 * to an instance of Linked Data Store
 */
public interface LDSInterface {

    /**
     * Get a list of all the IDs of existing subsets
     * @return
     * @throws HttpClientErrorException
     */
    List<String> getAllSubsetIDs() throws HttpClientErrorException;

    ResponseEntity<JsonNode> getVersionByID(String versionId);

    ResponseEntity<JsonNode> getSubsetSeries(String id);

    ResponseEntity<JsonNode> getAllSubsetSeries();

    /**
     * GET a list of all the version of each subset that was last UPDATED by the user.
     * This does NOT necessarily return the version with the most recent subsetValidFrom,
     * or the version that was most recently created.
     * This is the way resources are accessed by default in LDS.
     * @return
     */
    ResponseEntity<JsonNode> getLastUpdatedVersionOfAllSubsets();

    /**
     * Get a timeline of all patches to all versions of this subset, in chronological order.
     * @param id
     * @return
     */
    ResponseEntity<JsonNode> getTimelineOfSubset(String id);

    /**
     * Check if a subset with the given ID exists
     * @param id
     * @return
     */
    boolean existsSubsetWithID(String id);

    /**
     * Retrieve the schema of the ClassificationSubset resource type
     * @return
     */
    ResponseEntity<JsonNode> getClassificationSubsetSchema();

    /**
     * Submit a complete version of an existing subset, that contains changes to that subset
     * @param subset
     * @param id
     * @return
     */
    ResponseEntity<JsonNode> editSubset(JsonNode subset, String id);

    /**
     * Submit a subset with a previously unused ID
     * @param subset
     * @param id
     * @return
     */
    ResponseEntity<JsonNode> createSubset(JsonNode subset, String id);

    boolean healthReady();

    void deleteSubset(String url);

    public void deleteAllSubsets();

    ResponseEntity<JsonNode> editSeries(JsonNode newVersionOfSeries, String seriesID);

    ResponseEntity<JsonNode> postVersionInSeries(String id, String versionID, JsonNode versionNode);

    ResponseEntity<JsonNode> resolveVersionLink(String versionLink);

    ResponseEntity<JsonNode> getSubsetSeriesDefinition();

    ResponseEntity<JsonNode> getSubsetSeriesSchema();

    ResponseEntity<JsonNode> getSubsetVersionsDefinition();

    ResponseEntity<JsonNode> deleteAllSubsetSeries();

    ResponseEntity<JsonNode> deleteSubsetSeries(String id);
}
