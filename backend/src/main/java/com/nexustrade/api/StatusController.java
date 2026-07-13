package com.nexustrade.api;

import com.nexustrade.connector.ConnectorRegistry;
import com.nexustrade.connector.ConnectorState;
import com.nexustrade.engine.ArbitrageEngine;
import com.nexustrade.model.OrderBook;
import com.nexustrade.risk.CircuitBreaker;
import com.nexustrade.risk.WalletManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*")
public class StatusController {

    private final ConnectorRegistry registry;
    private final ArbitrageEngine engine;
    private final WalletManager walletManager;
    private final CircuitBreaker circuitBreaker;
    private final long startTime = System.currentTimeMillis();

    public StatusController(ConnectorRegistry registry, ArbitrageEngine engine,
                            WalletManager walletManager, CircuitBreaker circuitBreaker) {
        this.registry = registry;
        this.engine = engine;
        this.walletManager = walletManager;
        this.circuitBreaker = circuitBreaker;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, ConnectorState> states = registry.getStates();

        Map<String, Object> connectorDetails = new LinkedHashMap<>();
        states.forEach((exchange, state) -> {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("state", state.name());
            detail.put("healthy", state.isHealthy());
            detail.put("wsActive", state.isWebSocketActive());
            connectorDetails.put(exchange, detail);
        });

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("uptimeSeconds", (System.currentTimeMillis() - startTime) / 1000);
        response.put("liveConnectors", registry.getLiveCount());
        response.put("allHealthy", registry.allHealthy());
        response.put("connectors", connectorDetails);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/orderbooks")
    public ResponseEntity<List<Map<String, Object>>> getOrderBooks() {
        List<Map<String, Object>> books = registry.getAllOrderBooks().stream()
                .map(this::toSnapshot)
                .collect(Collectors.toList());
        return ResponseEntity.ok(books);
    }

    @GetMapping("/engine")
    public ResponseEntity<Map<String, Object>> getEngineStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalEvaluations", engine.getTotalEvaluations());
        stats.put("totalOpportunities", engine.getTotalOpportunities());
        stats.put("totalExecuted", engine.getTotalExecuted());
        stats.put("totalNetProfit", engine.getTotalNetProfit());
        stats.put("totalRejected", engine.getTotalRejected());
        stats.put("circuitBreakerActive", circuitBreaker.isActive());
        stats.put("circuitBreakerPauseMs", circuitBreaker.getRemainingPauseMs());
        stats.put("wallets", walletManager.getStatus());
        return ResponseEntity.ok(stats);
    }

    private Map<String, Object> toSnapshot(OrderBook ob) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("exchange", ob.getExchange());
        snap.put("symbol", ob.getSymbol());
        snap.put("bestBid", ob.bestBidPrice().map(p -> p.toPlainString()).orElse(null));
        snap.put("bestBidVolume", ob.bestBidVolume().map(v -> v.toPlainString()).orElse(null));
        snap.put("bestAsk", ob.bestAskPrice().map(p -> p.toPlainString()).orElse(null));
        snap.put("bestAskVolume", ob.bestAskVolume().map(v -> v.toPlainString()).orElse(null));
        snap.put("stalenessMs", ob.stalenessMs());
        snap.put("stale", ob.isStale(5000));
        return snap;
    }
}
