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
 * GET  /api/config — returns current live configuration.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);
    private static final List<String> ALL_EXCHANGES = List.of("BINANCE", "KRAKEN", "COINBASE");

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

        // Update minProfitUsd
        if (request.minProfitUsd() != null && request.minProfitUsd() > 0) {
            double old = config.getEngine().getMinProfitUsd();
            config.getEngine().setMinProfitUsd(request.minProfitUsd());
            log.info("  ✓ minProfitUsd: {} → {}", old, request.minProfitUsd());
        }

        // Update active exchanges
        if (request.activeExchanges() != null) {
            List<String> desired = request.activeExchanges().stream()
                    .map(String::toUpperCase)
                    .filter(ALL_EXCHANGES::contains)
                    .toList();

            List<String> currentActive = registry.getActiveExchanges();

            // Stop connectors that are no longer in the desired list
            for (String active : currentActive) {
                if (!desired.contains(active)) {
                    registry.stopConnector(active);
                    log.info("  ✓ Stopped connector: {}", active);
                }
            }

            // Start connectors that are in the desired list but currently inactive
            for (String want : desired) {
                if (!currentActive.contains(want)) {
                    registry.startConnector(want);
                    log.info("  ✓ Started connector: {}", want);
                }
            }
        }

        return ResponseEntity.ok(buildConfigResponse());
    }

    private Map<String, Object> buildConfigResponse() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minProfitUsd", config.getEngine().getMinProfitUsd());
        result.put("maxVolumeBtc", config.getEngine().getMaxVolumeBtc());
        result.put("decisionTimeoutMs", config.getEngine().getDecisionTimeoutMs());
        result.put("activeExchanges", registry.getActiveExchanges());
        return result;
    }
}
