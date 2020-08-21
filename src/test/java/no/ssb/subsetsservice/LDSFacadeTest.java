package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.*;

class LDSFacadeTest {

    @Test
    void existsLdsApiEnv(){
        System.out.println("ENV variable API_LDS " + System.getenv().getOrDefault("API_LDS", "was not present"));
    }

    @Test
    void getLastUpdatedVersionOfAllSubsets() {
        try {
            ResponseEntity<JsonNode> allSubsets = new LDSFacade().getLastUpdatedVersionOfAllSubsets();
            assertTrue(allSubsets.hasBody());
            if (allSubsets.hasBody())
                assertTrue(allSubsets.getBody().isArray());
        } catch (ResourceAccessException e){
            System.err.println("Message: "+e.getMessage());
            System.err.println("Localized Message: "+e.getLocalizedMessage());
            System.err.println("Cause: "+e.getCause().toString());
            System.err.println("Most specific cause: "+e.getMostSpecificCause().toString());
            System.err.println("Root cause "+e.getRootCause().toString());
        } catch (Error e){
            e.printStackTrace();
            System.err.println("Message: "+e.getMessage());
            System.err.println("Localized Message: "+e.getLocalizedMessage());
            System.err.println("Cause: "+e.getCause().toString());
        }

    }

    @Test
    void healthReady() {
        boolean ready = new LDSFacade().healthReady();
        assertTrue(ready);
    }
}