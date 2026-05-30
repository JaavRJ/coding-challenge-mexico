package com.nexustrade.api;

import com.nexustrade.risk.CircuitBreaker;
import com.nexustrade.risk.WalletManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stress test endpoint — injects simulated flash crash losses
 * to trigger the CircuitBreaker and demonstrate risk controls.
 * POST /api/risk/shock
 */
@RestController
@RequestMapping("/api/risk")
@CrossOrigin(origins = "${FRONTEND_URL:http://localhost:3000}")
public class RiskController {

    private static final Logger log = LoggerFactory.getLogger(RiskController.class);

    private final CircuitBreaker circuitBreaker;
    private final WalletManager walletManager;

    public RiskController(CircuitBreaker circuitBreaker, WalletManager walletManager) {
        this.circuitBreaker = circuitBreaker;
        this.walletManager = walletManager;
    }

    @PostMapping("/shock")
    public ResponseEntity<Map<String, Object>> injectShock() {
        log.warn("⚡ STRESS TEST: Injecting flash crash simulation...");

        // Force 4 consecutive losses to guarantee circuit breaker activation
        // (default threshold is 3)
        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordLoss();
        }

        log.warn("🛑 STRESS TEST COMPLETE: Circuit breaker should now be active");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", "FLASH_CRASH_SIMULATED");
        result.put("circuitBreakerActive", circuitBreaker.isActive());
        result.put("pauseRemainingMs", circuitBreaker.getRemainingPauseMs());
        return ResponseEntity.ok(result);
    }
}
