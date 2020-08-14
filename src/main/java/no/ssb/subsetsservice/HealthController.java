package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @RequestMapping("/health/alive")
    public ResponseEntity<String> alive() {
        return new ResponseEntity<>("The service is alive!", HttpStatus.OK);
    }

    @RequestMapping("/health/ready")
    public ResponseEntity<String> ready() {
        ResponseEntity<JsonNode> getSubsetsResponseEntity = SubsetsController.getInstance().getSubsets(false, false);
        boolean klassReady = new KlassURNResolver().pingKLASSClassifications();
        boolean ldsReady = new LDSFacade().pingLDSSubsets();
        if (klassReady && ldsReady && getSubsetsResponseEntity.getStatusCode().equals(HttpStatus.OK))
            return new ResponseEntity<>("The service is ready!", HttpStatus.OK);
        return new ResponseEntity<>("The service is not ready yet.\n KLASS ready: "+klassReady+" \n", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
