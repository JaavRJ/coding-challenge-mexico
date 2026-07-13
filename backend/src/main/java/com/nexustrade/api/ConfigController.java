package com.nexustrade.api;

import com.nexustrade.connector.ConnectorRegistry;
import com.nexustrade.engine.EngineConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoint for live configuration updates.
 * POST /api/config — applies config changes in-flight without restart.
 * GET  /api/config — returns current live configuration including all risk params and fees.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);
    private static final List<String> ALL_EXCHANGES = List.of("BINANCE", "KRAKEN", "COINBASE", "BITFINEX", "OKX");

    private final EngineConfig config;
    private final ConnectorRegistry registry;

    public ConfigController(EngineConfig config, ConnectorRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(buildConfigResponse());
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody ConfigUpdateRequest request) {
        log.info("⚙ Config update received: {}", request);

        // ── Engine parameters ─────────────────────────────────────────────────
        if (request.walletExposurePct() != null
                && request.walletExposurePct() > 0
                && request.walletExposurePct() <= 100) {
            double old = config.getEngine().getWalletExposurePct();
            config.getEngine().setWalletExposurePct(request.walletExposurePct());
            log.info("  ✓ walletExposurePct: {} → {}", old, request.walletExposurePct());
        }

        if (request.minRoiPct() != null && request.minRoiPct() > 0) {
            double old = config.getEngine().getMinRoiPct();
            config.getEngine().setMinRoiPct(request.minRoiPct());
            log.info("  ✓ minRoiPct: {} → {}", old, request.minRoiPct());
        }

        if (request.decisionTimeoutMs() != null && request.decisionTimeoutMs() > 0) {
            long old = config.getEngine().getDecisionTimeoutMs();
            config.getEngine().setDecisionTimeoutMs(request.decisionTimeoutMs());
            log.info("  ✓ decisionTimeoutMs: {} → {}", old, request.decisionTimeoutMs());
        }

        // ── Active exchanges ──────────────────────────────────────────────────
        if (request.activeExchanges() != null) {
            List<String> desired = request.activeExchanges().stream()
                    .map(String::toUpperCase)
                    .filter(ALL_EXCHANGES::contains)
                    .toList();

            List<String> currentActive = registry.getActiveExchanges();

            for (String active : currentActive) {
                if (!desired.contains(active)) {
                    registry.stopConnector(active);
                    log.info("  ✓ Stopped connector: {}", active);
                }
            }

            for (String want : desired) {
                if (!currentActive.contains(want)) {
                    registry.startConnector(want);
                    log.info("  ✓ Started connector: {}", want);
                }
            }
        }

        // ── Risk parameters ───────────────────────────────────────────────────
        if (request.circuitBreakerLosses() != null && request.circuitBreakerLosses() > 0) {
            int old = config.getRisk().getCircuitBreakerLosses();
            config.getRisk().setCircuitBreakerLosses(request.circuitBreakerLosses());
            log.info("  ✓ circuitBreakerLosses: {} → {}", old, request.circuitBreakerLosses());
        }

        if (request.circuitBreakerPauseSeconds() != null && request.circuitBreakerPauseSeconds() > 0) {
            int old = config.getRisk().getCircuitBreakerPauseSeconds();
            config.getRisk().setCircuitBreakerPauseSeconds(request.circuitBreakerPauseSeconds());
            log.info("  ✓ circuitBreakerPauseSeconds: {} → {}", old, request.circuitBreakerPauseSeconds());
        }

        if (request.maxBalanceDrawdownPct() != null && request.maxBalanceDrawdownPct() > 0) {
            double old = config.getRisk().getMaxBalanceDrawdownPct();
            config.getRisk().setMaxBalanceDrawdownPct(request.maxBalanceDrawdownPct());
            log.info("  ✓ maxBalanceDrawdownPct: {} → {}", old, request.maxBalanceDrawdownPct());
        }

        if (request.rebalanceThresholdPct() != null && request.rebalanceThresholdPct() > 0) {
            double old = config.getRisk().getRebalanceThresholdPct();
            config.getRisk().setRebalanceThresholdPct(request.rebalanceThresholdPct());
            log.info("  ✓ rebalanceThresholdPct: {} → {}", old, request.rebalanceThresholdPct());
        }

        // ── Per-exchange fee overrides ─────────────────────────────────────────
        if (request.feeOverrides() != null) {
            request.feeOverrides().forEach((exchangeKey, override) -> {
                String key = exchangeKey.toLowerCase();
                // Ensure the entry exists; create a minimal one if missing
                config.getExchanges().computeIfAbsent(key, k -> new EngineConfig.ExchangeProps());
                EngineConfig.ExchangeProps props = config.getExchanges().get(key);

                if (override.feeTaker() != null && override.feeTaker() >= 0) {
                    double old = props.getFeeTaker();
                    props.setFeeTaker(override.feeTaker());
                    log.info("  ✓ [{}] feeTaker: {} → {}", key, old, override.feeTaker());
                }
                if (override.feeMaker() != null && override.feeMaker() >= 0) {
                    double old = props.getFeeMaker();
                    props.setFeeMaker(override.feeMaker());
                    log.info("  ✓ [{}] feeMaker: {} → {}", key, old, override.feeMaker());
                }
                if (override.withdrawalFeeBtc() != null && override.withdrawalFeeBtc() >= 0) {
                    double old = props.getWithdrawalFeeBtc();
                    props.setWithdrawalFeeBtc(override.withdrawalFeeBtc());
                    log.info("  ✓ [{}] withdrawalFeeBtc: {} → {}", key, old, override.withdrawalFeeBtc());
                }
            });
        }

        return ResponseEntity.ok(buildConfigResponse());
    }

    private Map<String, Object> buildConfigResponse() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Engine parameters
        result.put("walletExposurePct", config.getEngine().getWalletExposurePct());
        result.put("minRoiPct", config.getEngine().getMinRoiPct());
        result.put("decisionTimeoutMs", config.getEngine().getDecisionTimeoutMs());

        // Active exchanges
        result.put("activeExchanges", registry.getActiveExchanges());

        // Risk parameters
        Map<String, Object> risk = new LinkedHashMap<>();
        risk.put("circuitBreakerLosses", config.getRisk().getCircuitBreakerLosses());
        risk.put("circuitBreakerPauseSeconds", config.getRisk().getCircuitBreakerPauseSeconds());
        risk.put("maxBalanceDrawdownPct", config.getRisk().getMaxBalanceDrawdownPct());
        risk.put("rebalanceThresholdPct", config.getRisk().getRebalanceThresholdPct());
        result.put("risk", risk);

        // Per-exchange fees
        Map<String, Object> fees = new LinkedHashMap<>();
        for (String exchange : ALL_EXCHANGES) {
            String key = exchange.toLowerCase();
            EngineConfig.ExchangeProps props = config.getExchanges().get(key);
            Map<String, Object> feeEntry = new LinkedHashMap<>();
            if (props != null) {
                feeEntry.put("feeTaker", props.getFeeTaker());
                feeEntry.put("feeMaker", props.getFeeMaker());
                feeEntry.put("withdrawalFeeBtc", props.getWithdrawalFeeBtc());
            } else {
                feeEntry.put("feeTaker", 0.001);
                feeEntry.put("feeMaker", 0.001);
                feeEntry.put("withdrawalFeeBtc", 0.0);
            }
            fees.put(exchange, feeEntry);
        }
        result.put("fees", fees);

        return result;
    }
}
