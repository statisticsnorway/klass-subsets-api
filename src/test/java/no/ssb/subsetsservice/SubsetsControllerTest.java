package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SubsetsControllerTest {

    private static final Logger LOG = LoggerFactory.getLogger(SubsetsServiceApplicationTests.class);

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
    void getAllSubsetsCheckResponseNotNull() {
        SubsetsController instance = SubsetsController.getInstance();
        assertNotNull(instance);
        ResponseEntity<JsonNode> response = instance.getSubsets(true, true, false);
        assertNotNull(response);
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