package no.ssb.subsetsservice;

import org.junit.Before;
import org.junit.jupiter.api.Test;

import static no.ssb.subsetsservice.LDSConsumer.LDS_LOCAL;

class SubsetsControllerTest {

    LDSFacade ldsFacade;

    @Before
    public void setup(){
        String ldsURL = System.getenv().getOrDefault("TEST_LDS_URL", LDS_LOCAL);
        ldsFacade = new LDSFacade(ldsURL);
    }

    @Test
    void getSubsets() {

    }
}