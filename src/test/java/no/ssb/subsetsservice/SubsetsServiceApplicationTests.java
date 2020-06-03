package no.ssb.subsetsservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
	void postToLDSLocal() {
		System.out.println("TESTING POST SUBSET BY ID '1' TO LDS LOCAL INSTANCE");
		try {
			String filename = "subset1.json";
			String path = new File("").getAbsolutePath();
			File myObj = new File(path +"\\src\\test\\java\\no\\ssb\\subsetsservice\\"+filename);
			Scanner myReader = new Scanner(myObj);
			StringBuilder sb = new StringBuilder();
			while (myReader.hasNextLine()) {
				sb.append(myReader.nextLine());
			}
			myReader.close();
			String subsetJSONString = sb.toString();
			ObjectMapper mapper = new ObjectMapper();
			JsonNode jsonNode = mapper.readTree(subsetJSONString);
			System.out.println(subsetJSONString);
			ResponseEntity<String> response = SubsetsController.putTo(ldsURL, "/1", jsonNode);

			System.out.println("RESPONSE HEADERS:");
			System.out.println(response.getHeaders());
			System.out.println("RESPONSE BODY");
			System.out.println(response.getBody());

			assertEquals(response.getStatusCodeValue(), 201);
		} catch (FileNotFoundException e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
	}

	@Test
	void getFromLDSLocal() {
		System.out.println("TESTING GET SUBSET BY ID 1 FROM LDS LOCAL INSTANCE");
		ResponseEntity<String> response = SubsetsController.getFrom(ldsURL, "/1");

		System.out.println("GET "+ldsURL+"/1");
		System.out.println("RESPONSE HEADERS:");
		System.out.println(response.getHeaders());
		System.out.println("RESPONSE BODY");

		System.out.println(response.getBody());
		assertEquals(response.getStatusCodeValue(), 200);
	}

	@Test
	void getAllFromLDSLocal() {
		System.out.println("TESTING GET ALL SUBSETS FROM LDS LOCAL INSTANCE");
		ResponseEntity<String> response = SubsetsController.getFrom(ldsURL, "");
		System.out.println("GET "+ldsURL);
		System.out.println("RESPONSE HEADERS:");
		System.out.println(response.getHeaders());
		System.out.println("RESPONSE BODY");
		System.out.println(response.getBody());
		assertEquals(response.getStatusCodeValue(), 200);
	}

}
