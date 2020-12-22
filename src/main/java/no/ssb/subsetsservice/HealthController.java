package no.ssb.subsetsservice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static HealthController instance;

    public static HealthController getInstance(){
        if (instance == null)
            instance = new HealthController();
        return instance;
    }

    private HealthController(){}

    @GetMapping("/health/alive")
    public ResponseEntity<String> alive() {
        return new ResponseEntity<>("The service is alive!", HttpStatus.OK);
    }

    @GetMapping("/health/ready")
    public ResponseEntity<String> ready() {
        boolean klassReady = new KlassURNResolver().pingKLASSClassifications();
        boolean ldsReady = new LDSFacade().healthReady();
        boolean schemaPresent = new LDSFacade().getSubsetSeriesSchema().getStatusCode().equals(HttpStatus.OK);
        if (klassReady && ldsReady && schemaPresent)
            return new ResponseEntity<>("The service is ready!", HttpStatus.OK);
        return new ResponseEntity<>("The service is not ready yet.\n KLASS ready: "+klassReady+" \n", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
