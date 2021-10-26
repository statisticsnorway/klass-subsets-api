package no.ssb.subsetsservice.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsService {

    private MeterRegistry meterRegistry;

    AtomicInteger getCounter;
    AtomicInteger putCounter;
    AtomicInteger postCounter;
    AtomicInteger testCounter;


    @Autowired
    public MetricsService(MeterRegistry meterRegistry){
        this.meterRegistry = meterRegistry;
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
