package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsService {

    private MeterRegistry meterRegistry;
    AtomicInteger getCounter;
    AtomicInteger putCounter;
    AtomicInteger postCounter;
    AtomicInteger testCounter;


    public MetricsService(MeterRegistry meterRegistry){
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    @Autowired
    public MetricsService(){
        initMetrics();
    }

    private void initMetrics() {
        getCounter = new AtomicInteger(0);
        putCounter = new AtomicInteger(0);
        postCounter = new AtomicInteger(0);
        testCounter = new AtomicInteger(0);
    }

    int incrementGETCounter(){
        return getCounter.incrementAndGet();
    }

    int incrementPUTCounter(){
        return putCounter.incrementAndGet();
    }

    int incrementPOSTCounter(){
        return postCounter.incrementAndGet();
    }

    int incrementTestCounter(){
        return testCounter.incrementAndGet();
    }
}
