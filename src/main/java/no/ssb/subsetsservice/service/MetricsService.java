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

    public AtomicInteger getGetCounter() {
        return getCounter;
    }

    public AtomicInteger getPutCounter() {
        return putCounter;
    }

    public AtomicInteger getPostCounter() {
        return postCounter;
    }

    public AtomicInteger getTestCounter() {
        return testCounter;
    }

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

    public int incrementGETCounter(){
        return getCounter.incrementAndGet();
    }

    public int incrementPUTCounter(){
        return putCounter.incrementAndGet();
    }

    public int incrementPOSTCounter(){
        return postCounter.incrementAndGet();
    }

    public int incrementTestCounter(){
        return testCounter.incrementAndGet();
    }
}
