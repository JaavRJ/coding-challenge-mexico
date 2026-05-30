package com.nexustrade.risk;

import com.nexustrade.engine.EngineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pauses trading after consecutive losses or balance drawdown.
 * 
 * Rules:
 *   - 3 consecutive net losses → pause 60s
 *   - Balance drawdown > 2% → pause 60s
 */
@Component
public class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    private final EngineConfig config;
    private final AtomicInteger consecutiveLosses = new AtomicInteger(0);
    private final AtomicLong pauseUntilMs = new AtomicLong(0);

    public CircuitBreaker(EngineConfig config) {
        this.config = config;
    }

    public boolean isActive() {
        return System.currentTimeMillis() < pauseUntilMs.get();
    }

    public void recordProfit() {
        consecutiveLosses.set(0);
    }

    public void recordLoss() {
        int losses = consecutiveLosses.incrementAndGet();
        int maxLosses = config.getRisk().getCircuitBreakerLosses();

        if (losses >= maxLosses) {
            long pauseMs = config.getRisk().getCircuitBreakerPauseSeconds() * 1000L;
            pauseUntilMs.set(System.currentTimeMillis() + pauseMs);
            log.warn("🛑 CIRCUIT BREAKER ACTIVATED: {} consecutive losses. Pausing for {}s",
                    losses, config.getRisk().getCircuitBreakerPauseSeconds());
            consecutiveLosses.set(0);
        }
    }

    public void checkDrawdown(double drawdownPct) {
        double maxDrawdown = config.getRisk().getMaxBalanceDrawdownPct();
        if (Math.abs(drawdownPct) > maxDrawdown) {
            long pauseMs = config.getRisk().getCircuitBreakerPauseSeconds() * 1000L;
            pauseUntilMs.set(System.currentTimeMillis() + pauseMs);
            log.warn("🛑 CIRCUIT BREAKER (DRAWDOWN): {}% exceeds max {}%. Pausing {}s",
                    String.format("%.2f", drawdownPct), 
                    String.format("%.2f", maxDrawdown), 
                    config.getRisk().getCircuitBreakerPauseSeconds());
        }
    }

    public long getRemainingPauseMs() {
        long remaining = pauseUntilMs.get() - System.currentTimeMillis();
        return Math.max(0, remaining);
    }
}
