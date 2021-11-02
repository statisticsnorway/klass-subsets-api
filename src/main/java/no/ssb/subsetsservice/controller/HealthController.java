package no.ssb.subsetsservice.controller;

import no.ssb.subsetsservice.service.DatabaseFactory;
import no.ssb.subsetsservice.service.DatabaseInterface;
import no.ssb.subsetsservice.util.KlassURNResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {


    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);

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
        LOG.debug("starting ready check. Pinging KLASS");
        boolean klassReady = new KlassURNResolver().pingKLASSClassifications();
        LOG.debug("klass ready: "+klassReady);
        LOG.debug("getting database instance");
        DatabaseInterface database = DatabaseFactory.getDatabase(DatabaseFactory.DEFAULT_DATABASE);
        LOG.debug("checking readiness of database");
        boolean databaseReady = database.healthReady();
        LOG.debug("database ready: "+databaseReady+". Now checking schema presence.");
        boolean schemaPresent = database.getSubsetSeriesSchema().getStatusCode().equals(HttpStatus.OK);
        LOG.debug("schema present: "+schemaPresent);
        if (klassReady && databaseReady && schemaPresent)
            return new ResponseEntity<>("The service is ready!", HttpStatus.OK);
        String readinessDescription = "The service is not ready yet. "+
                "KLASS ready: "+klassReady+". "+
                "databaseReady: "+databaseReady+". "+
                "schemaPresent: "+schemaPresent+". ";
        LOG.warn(readinessDescription);
        return new ResponseEntity<>(readinessDescription, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
