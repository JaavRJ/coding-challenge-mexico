package com.nexustrade.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.DistributionSummary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer-based latency and spread metrics.
 * Exposes P95/P99 via /actuator/metrics and /actuator/prometheus.
 */
@Component
public class LatencyTracker {

    private final Timer decisionLatency;
    private final DistributionSummary orderbookStaleness;
    private final DistributionSummary grossSpread;

    public LatencyTracker(MeterRegistry registry) {
        this.decisionLatency = Timer.builder("nexustrade.decision.latency")
                .description("Time from order book ingestion to engine decision")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        this.orderbookStaleness = DistributionSummary.builder("nexustrade.orderbook.staleness")
                .description("Order book data age in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.grossSpread = DistributionSummary.builder("nexustrade.spread.gross")
                .description("Gross spread distribution in USD")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordDecisionLatency(long latencyMs) {
        decisionLatency.record(Duration.ofMillis(latencyMs));
    }

    public void recordStaleness(long stalenessMs) {
        orderbookStaleness.record(stalenessMs);
    }

    public void recordGrossSpread(double spreadUsd) {
        grossSpread.record(spreadUsd);
    }
}
