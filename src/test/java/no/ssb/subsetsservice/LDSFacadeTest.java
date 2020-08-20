package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class LDSFacadeTest {

    @Test
    void existsLdsApiEnv(){
        System.out.println("ENV variable API_LDS "+System.getenv().getOrDefault("API_LDS", "was not present"));
    }

    @Test
    void getLastUpdatedVersionOfAllSubsets() {
        ResponseEntity<JsonNode> allSubsets = new LDSFacade().getLastUpdatedVersionOfAllSubsets();
        assertTrue(allSubsets.hasBody());
        if (allSubsets.hasBody())
            assertTrue(allSubsets.getBody().isArray());
    }

    @Test
    void healthReady() {
        boolean ready = new LDSFacade().healthReady();
        assertTrue(ready);
    }
}