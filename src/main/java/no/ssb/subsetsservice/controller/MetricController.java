package no.ssb.subsetsservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import no.ssb.subsetsservice.service.MetricsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
public class MetricController {

    private MetricsService metricsService;

    @Autowired
    public MetricController(MetricsService metricsService){
        this.metricsService = metricsService;
    }

    @GetMapping("/customMetrics")
    public ResponseEntity<JsonNode> getMetrics() {
        ObjectNode metricsNode = new ObjectMapper().createObjectNode();
        metricsNode.put("GET counter", metricsService.getGetCounter().intValue());
        metricsNode.put("PUT counter", metricsService.getPutCounter().intValue());
        metricsNode.put("POST counter", metricsService.getPostCounter().intValue());
        metricsNode.put("test counter", metricsService.incrementTestCounter());
        JsonNode body = metricsNode.deepCopy();
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    @GetMapping("/testCounter")
    public ResponseEntity<JsonNode> getTestCounter() {
        ObjectNode metricsNode = new ObjectMapper().createObjectNode();
        metricsNode.put("test counter", metricsService.incrementTestCounter());
        JsonNode body = metricsNode.deepCopy();
        return new ResponseEntity<>(body, HttpStatus.OK);
    }
}
