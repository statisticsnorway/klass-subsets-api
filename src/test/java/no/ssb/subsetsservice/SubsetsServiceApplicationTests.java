package no.ssb.subsetsservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
class SubsetsServiceApplicationTests {

	private static final Logger LOG = LoggerFactory.getLogger(SubsetsServiceApplicationTests.class);
	String ldsURL = SubsetsController.LDS_LOCAL;

	@Test
	void logTest(){
		LOG.trace("TRACE");
		LOG.debug("DEBUG");
		LOG.info("INFO");
		LOG.warn("WARN");
		LOG.error("ERROR");
	}

	@Test
	void getAllSubsets() {
		ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubsets();

		assertEquals(200, response.getStatusCodeValue());
		System.out.println("RESPONSE HEADERS:");
		System.out.println(response.getHeaders());
		System.out.println("RESPONSE BODY");
		System.out.println(response.getBody());
		assertNotEquals(null, response.getBody());
		assertNotEquals("[]", response.getBody());
	}

	@Test
	void getIllegalIdSubset() {
		ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubset("this-id-is-not-legal-¤%&#!§|`^¨~'*=)(/\\£$@{[]}", false);

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
		ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubset("this-id-does-not-exist", false);

		System.out.println("STATUS CODE");
		System.out.println(response.getStatusCodeValue());
		assertEquals(404, response.getStatusCodeValue());
		System.out.println("RESPONSE HEADERS:");
		System.out.println(response.getHeaders());
		System.out.println("RESPONSE BODY");
		System.out.println(response.getBody());
	}

	@Test
	void getNonExistantSubsetVersions() {
		ResponseEntity<JsonNode> response = SubsetsController.getInstance().getVersions("this-id-does-not-exist");

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
		ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubsets();

		System.out.println("All subsets:");
		System.out.println(response.getBody());
		System.out.println("IDs:");
		for (JsonNode jsonNode : response.getBody()) {
			JsonNode subset = SubsetsController.getInstance().getSubset(jsonNode.get("id").asText(), false).getBody();
			assertEquals(subset.get("id").asText(), jsonNode.get("id").asText());
			System.out.println(subset.get("id"));
		}
	}

	@Test
	void getAllVersionsOfAllSubsets() {
		ResponseEntity<JsonNode> response = SubsetsController.getInstance().getSubsets();

		System.out.println("All subsets:");
		System.out.println(response.getBody());
		System.out.println("IDs:");
		for (JsonNode jsonNode : response.getBody()) {
			JsonNode subset = SubsetsController.getInstance().getSubset(jsonNode.get("id").asText(), false).getBody();
			assertEquals(subset.get("id").asText(), jsonNode.get("id").asText());
			System.out.println("ID: "+subset.get("id"));

			ArrayNode versions = (ArrayNode) SubsetsController.getInstance().getVersions(subset.get("id").asText()).getBody();
			assertNotEquals(null, versions);
			assertNotEquals(0, versions.size());
			for (JsonNode version : versions) {
				System.out.println("Version: "+version.get("version").asText()+" adminstatus: "+version.get("administrativeStatus").asText()+" name:"+version.get("name").get(0).get("languageText").asText());
			}

		}
	}

}
