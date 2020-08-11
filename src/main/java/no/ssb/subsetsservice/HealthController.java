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
        ResponseEntity<JsonNode> responseEntity = SubsetsController.getInstance().getSubsets(false, false);
        if (responseEntity.getStatusCodeValue() == 200)
            return new ResponseEntity<>("The service is ready!", HttpStatus.OK);
        return new ResponseEntity<>("The service is not ready yet.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
