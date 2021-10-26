package no.ssb.subsetsservice.controller;

import no.ssb.subsetsservice.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HealthControllerTest {


    @Test
    void alive() {
        HealthController instance = HealthController.getInstance();
        ResponseEntity<String> re = instance.alive();
        assertEquals(HttpStatus.OK, re.getStatusCode());
    }

    @Test
    void ready() {
        HealthController instance = HealthController.getInstance();
        ResponseEntity<String> re = instance.ready();
        assertEquals(HttpStatus.OK, re.getStatusCode());
    }
}
