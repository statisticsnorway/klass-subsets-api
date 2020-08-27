package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.api.Http;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SubsetsControllerTest {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsServiceApplicationTests.class);
    File fCS1 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/ClassificationSubset_1.json");
    File fCS2 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/ClassificationSubset_2.json");
    File fv0_1 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/uttrekk_for_publiseringstesting_v0.1.json"); // try to set versionValidFrom earlier than validFrom
    File fv0_2 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/uttrekk_for_publiseringstesting_v0.2.json"); // try to set versionValidFrom later than validFrom
    File fv0_3 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/uttrekk_for_publiseringstesting_v0.3.json"); // version field is missing
    File fv0_9 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/uttrekk_for_publiseringstesting_v0.9.json"); // status DRAFT
    File fv1_0 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/uttrekk_for_publiseringstesting_v1.0.json"); // status OPEN
    File fv1_1 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/uttrekk_for_publiseringstesting_v1.1.json"); // try to delete code
    File f1_2 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/uttrekk_for_publiseringstesting_v1.2.json"); // try to change validFrom and versionValidFrom to a later date
    File fv1_3 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/uttrekk_for_publiseringstesting_v1.3.json"); // try to change versionValidFrom to a later date than validFrom, even if this is only version of subset
    File fv2 = new File("src/test/java/no/ssb/subsetsservice/subset_examples/uttrekk_for_publiseringstesting_v2.json");

    @Test
    void testIfFilesArePresent(){
        assertTrue(fCS1.exists());
        assertTrue(fCS2.exists());
        assertTrue(fv0_1.exists());
        assertTrue(fv0_2.exists());
        assertTrue(fv0_3.exists());
        assertTrue(fv0_9.exists());
        assertTrue(fv1_0.exists());
        assertTrue(fv1_1.exists());
        assertTrue(f1_2.exists());
        assertTrue(fv1_3.exists());
        assertTrue(fv2.exists());
    }

    public JsonNode getSubset(File file){
        assert file.exists() : "File "+file.getAbsolutePath()+" did not exist";
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readTree(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Test
    void deleteAllThenCheckThatEmpty() {
        SubsetsController instance = SubsetsController.getInstance();
        // Delete all subsets
        instance.deleteAll();

        // Test if database is empty
        ResponseEntity<JsonNode> allSubsets = instance.getSubsets(true, true, true);
        assertEquals(HttpStatus.OK, allSubsets.getStatusCode());
        JsonNode body = allSubsets.getBody();
        assertNotNull(body);
        assertTrue(body.isArray());
        assertTrue(body.isEmpty());
    }

    @Test
    void postDeletePostGetVersionsCheckLength(){
        SubsetsController instance = SubsetsController.getInstance();
        instance.deleteAll();

        JsonNode subsetJsonNode = getSubset(fCS1);
        ResponseEntity<JsonNode> postRE = instance.postSubset(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());
        ResponseEntity<JsonNode> getVersionsRE = instance.getVersions(subsetJsonNode.get(Field.ID).asText(), true, true, true);
        ArrayNode versionsBody = (ArrayNode)getVersionsRE.getBody();
        assertEquals( 1, versionsBody.size());
    }

    @Test
    void postSubsetAndCheckIDOfResponse(){
        SubsetsController instance = SubsetsController.getInstance();
        instance.deleteAll();

        JsonNode subsetJsonNode = getSubset(fCS1);
        ResponseEntity<JsonNode> postRE = instance.postSubset(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());
        String originalID = subsetJsonNode.get(Field.ID).asText();
        JsonNode retrievedSubset = instance.getSubset(originalID, true, true, true).getBody();

        assertNotNull(retrievedSubset);
        assertFalse(retrievedSubset.isEmpty());
        assertEquals(originalID, retrievedSubset.get(Field.ID).asText());
    }

    @Test
    void postSubsetAndCheckDateStampsOfResponse(){
        SubsetsController instance = SubsetsController.getInstance();
        instance.deleteAll();

        JsonNode subsetJsonNode = getSubset(fCS1);
        instance.postSubset(subsetJsonNode);
        JsonNode retrievedSubset = instance.getSubset(subsetJsonNode.get(Field.ID).asText(), true, true, true).getBody();
        assertTrue(retrievedSubset.has(Field.CREATED_DATE));
        assertTrue(retrievedSubset.has(Field.LAST_UPDATED_DATE));
    }

    @Test
    void postInvalidVersionValidFrom(){
        SubsetsController instance = SubsetsController.getInstance();
        instance.deleteAll();

        JsonNode subsetJsonNode = getSubset(fv0_1);
        ResponseEntity<JsonNode> postRE = instance.postSubset(subsetJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, postRE.getStatusCode());
    }

    @Test
    void postInvalidVersionValidFrom2(){
        SubsetsController instance = SubsetsController.getInstance();
        instance.deleteAll();

        JsonNode subsetJsonNode = getSubset(fv0_2);
        ResponseEntity<JsonNode> postRE = instance.postSubset(subsetJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, postRE.getStatusCode());
    }

    @Test
    void postMissingVersion(){
        SubsetsController instance = SubsetsController.getInstance();
        instance.deleteAll();

        JsonNode subsetJsonNode = getSubset(fv0_3);
        ResponseEntity<JsonNode> postRE = instance.postSubset(subsetJsonNode);
        assertEquals(HttpStatus.BAD_REQUEST, postRE.getStatusCode());
    }

    @Test
    void postDraftSubset(){
        SubsetsController instance = SubsetsController.getInstance();
        instance.deleteAll();

        JsonNode subsetJsonNode = getSubset(fv0_9);
        ResponseEntity<JsonNode> postRE = instance.postSubset(subsetJsonNode);
        assertEquals(HttpStatus.CREATED, postRE.getStatusCode());
    }

    @Test
    void postDraftThenPutOpen(){
        SubsetsController instance = SubsetsController.getInstance();
        instance.deleteAll();

        JsonNode draft = getSubset(fv0_9);
        ResponseEntity<JsonNode> postDraftRE = instance.postSubset(draft);
        assertEquals(HttpStatus.CREATED, postDraftRE.getStatusCode());

        JsonNode open = getSubset(fv1_0);
        ResponseEntity<JsonNode> putOpenRE = instance.putSubset(open.get(Field.ID).asText(), open);
        assertEquals(HttpStatus.OK, putOpenRE.getStatusCode());
    }

    @Test
    void getSubsetsCheckStatusOK() {
        SubsetsController instance = SubsetsController.getInstance();
        assertNotNull(instance);
        ResponseEntity<JsonNode> subsets = instance.getSubsets(true, true, false);
        assertEquals(HttpStatus.OK, subsets.getStatusCode());
    }

    @Test
    void getSubsetsUrnOnlyCheckStatusOK() {
        SubsetsController instance = SubsetsController.getInstance();
        assertNotNull(instance);
        ResponseEntity<JsonNode> subsets = instance.getSubsets(true, true, true);
        assertEquals(HttpStatus.OK, subsets.getStatusCode());
    }

    @Test
    void getSubsetsUrnOnlyCheckContainsURNAndRank() {
        SubsetsController instance = SubsetsController.getInstance();
        assertNotNull(instance);
        ResponseEntity<JsonNode> subsets = instance.getSubsets(true, true, true);
        assertEquals(HttpStatus.OK, subsets.getStatusCode());
        JsonNode body = subsets.getBody();
        assertNotNull(body);
        assertTrue(body.isArray());
        ArrayNode bodyArrayNode = (ArrayNode) body;
        System.out.println("Size of list of subsets: "+bodyArrayNode.size());
        for (JsonNode subsetJsonNode : bodyArrayNode) {
            JsonNode codesList = subsetJsonNode.get(Field.CODES);
            assertTrue(codesList.isArray());
            ArrayNode codesListArrayNode = (ArrayNode) codesList;
            for (JsonNode rankedURN : codesListArrayNode) {
                assertTrue(rankedURN.has(Field.URN));
                assertTrue(rankedURN.has(Field.RANK));
            }
        }
    }

    @Test
    void getAllSubsetsCheckStatusOK() {
        SubsetsController instance = SubsetsController.getInstance();
        ResponseEntity<JsonNode> response = instance.getSubsets(true, true, false);
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getAllSubsetsCheckResponseBodyNotNull() {
        SubsetsController instance = SubsetsController.getInstance();
        ResponseEntity<JsonNode> response = instance.getSubsets(true, true, false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = response.getBody();
        assertNotNull(body);
    }

    @Test
    void getAllSubsetsCheckBodyIsArray() {
        SubsetsController instance = SubsetsController.getInstance();
        ResponseEntity<JsonNode> response = instance.getSubsets(true, true, false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode body = response.getBody();
        assertTrue(body.isArray());

        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());
    }

    @Test
    void getIllegalIdSubset() {
        ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubset("this-id-is-not-legal-¤%&#!§|`^¨~'*=)(/\\£$@{[]}", false, false, false);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());
    }

    @Test
    void getNonExistingSubset() {
        ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubset("this-id-does-not-exist", true, true, true);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());
    }

    @Test
    void getNonExistentSubsetVersions() {
        ResponseEntity<JsonNode> response = SubsetsController.getInstance().getVersions("this-id-does-not-exist", true, true, true);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());
    }

    @Test
    void getAllIndividualSubsetsCompareIDs() {
        ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubsets(true, true, false);

        System.out.println("All subsets:");
        JsonNode responseBody = response.getBody();
        System.out.println(responseBody);
        assertNotNull(responseBody);
        assertTrue(responseBody.isArray());
        System.out.println("IDs:");
        for (JsonNode jsonNode : responseBody) {
            String id = jsonNode.get(Field.ID).asText();
            JsonNode subset = SubsetsController.getInstance().getSubset(id, true, true, false).getBody();
            assert subset != null : "getSubset "+id+" did not return any result";
            assertTrue(subset.has(Field.ID));
            assertEquals(subset.get(Field.ID).asText(), jsonNode.get(Field.ID).asText());
            System.out.println(subset.get(Field.ID));
        }
    }

    @Test
    void getAllVersionsOfAllSubsets() {
        ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubsets( true, true, false);

        System.out.println("All subsets:");
        JsonNode body = response.getBody();
        assertNotNull(body);
        System.out.println(body);
        System.out.println("IDs:");
        for (JsonNode jsonNode : response.getBody()) {
            JsonNode subset = SubsetsController.getInstance().getSubset(jsonNode.get("id").asText(), true, true, false).getBody();
            assertNotNull(subset);
            assertTrue(subset.has(Field.ID));
            assertEquals(subset.get(Field.ID).asText(), jsonNode.get(Field.ID).asText());
            System.out.println("ID: "+subset.get(Field.ID));

            ArrayNode versions = (ArrayNode) SubsetsController.getInstance().getVersions(subset.get(Field.ID).asText(), true, true, false).getBody();
            assertNotNull(versions);
            assertNotEquals(0, versions.size());
            for (JsonNode version : versions) {
                System.out.println("Version: "+version.get(Field.VERSION).asText()+" admin status: "+version.get(Field.ADMINISTRATIVE_STATUS).asText()+" name: "+version.get(Field.NAME).get(0).get("languageText").asText());
            }
        }
    }
}