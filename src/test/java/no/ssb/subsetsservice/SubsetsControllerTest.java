package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static no.ssb.subsetsservice.LDSConsumer.LDS_LOCAL;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SubsetsControllerTest {


    private static final Logger LOG = LoggerFactory.getLogger(SubsetsServiceApplicationTests.class);

    @Test
    void getSubsetsUrnOnly() {
        SubsetsController instance = SubsetsController.getInstance();
        assertNotNull(instance);
        ResponseEntity<JsonNode> subsets = instance.getSubsets(true, true, true);
        assertEquals(HttpStatus.OK, subsets.getStatusCode());
    }

    @Test
    void getSubsets() {

    }

    @Test
    void getAllSubsets() {
        SubsetsController instance = SubsetsController.getInstance();
        assertNotNull(instance);
        ResponseEntity<JsonNode> response = instance.getSubsets(true, true, false);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());

        JsonNode body = response.getBody();
        assertNotNull(body);
        assertTrue(body.isArray());
        assertTrue(body.isEmpty());
        assertNotEquals("[]", response.getBody().asText());
    }

    @Test
    void getIllegalIdSubset() {
        ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubset("this-id-is-not-legal-¤%&#!§|`^¨~'*=)(/\\£$@{[]}", false, false, false);

        System.out.println("STATUS CODE");
        System.out.println(response.getStatusCodeValue());
        assertEquals(400, response.getStatusCodeValue());
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
        assertEquals(404, response.getStatusCodeValue());
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
        assertEquals(404, response.getStatusCodeValue());
        System.out.println("RESPONSE HEADERS:");
        System.out.println(response.getHeaders());
        System.out.println("RESPONSE BODY");
        System.out.println(response.getBody());
    }

    @Test
    void getAllIndividualSubsetsCompareIDs() {
        ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubsets(true, true, false);

        System.out.println("All subsets:");
        System.out.println(response.getBody());
        System.out.println("IDs:");
        for (JsonNode jsonNode : response.getBody()) {
            JsonNode subset = SubsetsController.getInstance().getSubset(jsonNode.get("id").asText(), true, true, false).getBody();
            assertTrue(subset.has("id"));
            assertEquals(subset.get("id").asText(), jsonNode.get("id").asText());
            System.out.println(subset.get("id"));
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
            assertTrue(subset.has("id"));
            assertEquals(subset.get("id").asText(), jsonNode.get("id").asText());
            System.out.println("ID: "+subset.get("id"));

            ArrayNode versions = (ArrayNode) SubsetsController.getInstance().getVersions(subset.get("id").asText(), true, true, false).getBody();
            assertNotNull(versions);
            assertNotEquals(0, versions.size());
            for (JsonNode version : versions) {
                System.out.println("Version: "+version.get("version").asText()+" adminstatus: "+version.get("administrativeStatus").asText()+" name:"+version.get("name").get(0).get("languageText").asText());
            }
        }
    }
}