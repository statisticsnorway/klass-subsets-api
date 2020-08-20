package no.ssb.subsetsservice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KlassURNResolverTest {

    @Test
    void pingKLASSClassifications() {
        boolean ping = new KlassURNResolver().pingKLASSClassifications();
        assertTrue(ping);
    }
}