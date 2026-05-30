package com.nexustrade.risk;

import com.nexustrade.engine.EngineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;
    private EngineConfig config;

    @BeforeEach
    void setUp() {
        config = new EngineConfig();
        // Default risk config: 3 losses, 60s pause, 2.0% max drawdown
        circuitBreaker = new CircuitBreaker(config);
    }

    @Test
    void testLossStreakTriggersPause() {
        assertFalse(circuitBreaker.isActive(), "Should start inactive");

        // Record 2 losses, should still be inactive
        circuitBreaker.recordLoss();
        circuitBreaker.recordLoss();
        assertFalse(circuitBreaker.isActive(), "Should be inactive after 2 losses");

        // 3rd loss triggers pause
        circuitBreaker.recordLoss();
        assertTrue(circuitBreaker.isActive(), "Should be active after 3 losses");
        
        // Pause should be roughly 60 seconds
        long remaining = circuitBreaker.getRemainingPauseMs();
        assertTrue(remaining > 59000 && remaining <= 60000, "Pause should be ~60s");
    }

    @Test
    void testDrawdownTriggersPause() {
        assertFalse(circuitBreaker.isActive(), "Should start inactive");

        // 1% drawdown is safe
        circuitBreaker.checkDrawdown(1.0);
        assertFalse(circuitBreaker.isActive(), "Should be inactive after 1% drawdown");

        // 2.5% drawdown triggers pause
        circuitBreaker.checkDrawdown(2.5);
        assertTrue(circuitBreaker.isActive(), "Should be active after 2.5% drawdown");
    }

    @Test
    void testCooldownReset() throws InterruptedException {
        // Customize config to have a 1-second pause for the test
        config.getRisk().setCircuitBreakerPauseSeconds(1);
        
        circuitBreaker.recordLoss();
        circuitBreaker.recordLoss();
        circuitBreaker.recordLoss();
        
        assertTrue(circuitBreaker.isActive(), "Should be active after losses");

        // Wait for cooldown to expire
        Thread.sleep(1100);

        assertFalse(circuitBreaker.isActive(), "Should be inactive after cooldown expires");
    }
}
