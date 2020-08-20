package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static no.ssb.subsetsservice.LDSConsumer.LDS_LOCAL;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SubsetsControllerTest {

    @Test
    void getSubsetsUrnOnly() {
        SubsetsController instance = SubsetsController.getInstance();
        assertNotNull(instance);
        ResponseEntity<JsonNode> subsets = instance.getSubsets(true, true, true);
        assertEquals(HttpStatus.OK, subsets.getStatusCode());
    }

    @Test
    void getSubsets() {
        SubsetsController instance = SubsetsController.getInstance();
        assertNotNull(instance);
        ResponseEntity<JsonNode> subsets = instance.getSubsets(true, true, false);
        assertEquals(HttpStatus.OK, subsets.getStatusCode());
    }
}