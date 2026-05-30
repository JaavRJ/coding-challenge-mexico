package com.nexustrade.engine;

import com.nexustrade.api.DashboardController;
import com.nexustrade.connector.ConnectorRegistry;
import com.nexustrade.metrics.LatencyTracker;
import com.nexustrade.metrics.ShadowLearningRecorder;
import com.nexustrade.model.ArbitrageOpportunity;
import com.nexustrade.model.OrderBookSnapshot;
import com.nexustrade.model.TradeStatus;
import com.nexustrade.risk.CircuitBreaker;
import com.nexustrade.risk.WalletManager;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Master arbitrage engine. Evaluates 6 directional pairs per tick.
 * Integrates: SpreadCalculator, WalletManager, CircuitBreaker,
 * ShadowLearningRecorder, LatencyTracker.
 */
@Component
public class ArbitrageEngine {

    private static final Logger log = LoggerFactory.getLogger(ArbitrageEngine.class);
    private static final String[] EXCHANGES = {"BINANCE", "KRAKEN", "COINBASE"};

    private final ConnectorRegistry registry;
    private final SpreadCalculator spreadCalculator;
    private final EngineConfig config;
    private final WalletManager walletManager;
    private final CircuitBreaker circuitBreaker;
    private final ShadowLearningRecorder recorder;
    private final LatencyTracker latencyTracker;
    private final DashboardController dashboardController;

    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalOpportunities = new AtomicLong(0);
    private final AtomicLong totalExecuted = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);

    private volatile long lastLogTimeMs = 0;
    private static final long LOG_INTERVAL_MS = 5000;

    public ArbitrageEngine(ConnectorRegistry registry,
                           SpreadCalculator spreadCalculator,
                           EngineConfig config,
                           WalletManager walletManager,
                           CircuitBreaker circuitBreaker,
                           ShadowLearningRecorder recorder,
                           LatencyTracker latencyTracker,
                           DashboardController dashboardController) {
        this.registry = registry;
        this.spreadCalculator = spreadCalculator;
        this.config = config;
        this.walletManager = walletManager;
        this.circuitBreaker = circuitBreaker;
        this.recorder = recorder;
        this.latencyTracker = latencyTracker;
        this.dashboardController = dashboardController;
    }

    @PostConstruct
    public void init() {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("  🧠 ArbitrageEngine starting (Phase 3)");
        log.info("  Min profit: ${}", config.getEngine().getMinProfitUsd());
        log.info("  Max volume: {} BTC", config.getEngine().getMaxVolumeBtc());
        log.info("  Circuit breaker: {} losses → {}s pause",
                config.getRisk().getCircuitBreakerLosses(),
                config.getRisk().getCircuitBreakerPauseSeconds());
        log.info("  Wallet: {} USDT + {} BTC per exchange",
                config.getWallet().getInitialUsdt(), config.getWallet().getInitialBtc());
        log.info("═══════════════════════════════════════════════════════════");

        registry.setGlobalUpdateCallback(orderBook -> {
            try { evaluateAll(); } catch (Exception e) {
                log.warn("Engine callback error: {}", e.getMessage());
            }
        });
    }

    @Scheduled(fixedDelayString = "${nexustrade.engine.evaluation-interval-ms:50}")
    public void scheduledEvaluation() {
        try { evaluateAll(); } catch (Exception e) {
            log.warn("Scheduled evaluation error: {}", e.getMessage());
        }
    }

    private void evaluateAll() {
        long startNanos = System.nanoTime();

        // Circuit breaker check
        if (circuitBreaker.isActive()) {
            return;
        }

        List<OrderBookSnapshot> snapshots = new ArrayList<>();
        for (String exchange : EXCHANGES) {
            registry.getOrderBook(exchange).ifPresent(ob -> {
                ob.snapshot().ifPresent(snap -> {
                    snapshots.add(snap);
                    latencyTracker.recordStaleness(ob.stalenessMs());
                });
            });
        }

        if (snapshots.size() < 2) return;

        long evalCount = totalEvaluations.incrementAndGet();
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();

        for (int i = 0; i < snapshots.size(); i++) {
            for (int j = 0; j < snapshots.size(); j++) {
                if (i == j) continue;

                OrderBookSnapshot buyOn = snapshots.get(i);
                OrderBookSnapshot sellOn = snapshots.get(j);

                if (sellOn.bestBidPrice().compareTo(buyOn.bestAskPrice()) <= 0) continue;

                Optional<ArbitrageOpportunity> oppOpt =
                        spreadCalculator.evaluate(buyOn, sellOn, startNanos);
                oppOpt.ifPresent(opportunities::add);
            }
        }

        for (ArbitrageOpportunity originalOpp : opportunities) {
            totalOpportunities.incrementAndGet();
            
            ArbitrageOpportunity finalOpp = originalOpp;

            // Check circuit breaker before deciding to execute
            if (originalOpp.isProfitable() && circuitBreaker.isActive()) {
                finalOpp = originalOpp.withStatus(TradeStatus.REJECTED_CIRCUIT_BREAKER, "Circuit breaker is active");
            }

            // Record metrics
            latencyTracker.recordDecisionLatency(finalOpp.decisionLatencyMs());
            latencyTracker.recordGrossSpread(finalOpp.grossSpread().doubleValue());

            // Record to JSONL + SSE broadcast
            recorder.record(finalOpp);
            dashboardController.broadcast(finalOpp);

            if (finalOpp.isProfitable()) {
                // Execute simulated trade
                boolean traded = walletManager.executeTrade(finalOpp);
                if (traded) {
                    totalExecuted.incrementAndGet();
                    circuitBreaker.recordProfit();
                    log.info("[ArbitrageEngine] {}", finalOpp);
                } else {
                    totalRejected.incrementAndGet();
                    circuitBreaker.recordLoss();
                }
            } else {
                totalRejected.incrementAndGet();
                if (finalOpp.netProfit().doubleValue() < 0 && finalOpp.status() != TradeStatus.REJECTED_CIRCUIT_BREAKER) {
                    circuitBreaker.recordLoss();
                }
            }
        }

        // Check drawdown
        if (!snapshots.isEmpty()) {
            double drawdown = walletManager.getDrawdownPct(snapshots.get(0).bestBidPrice());
            circuitBreaker.checkDrawdown(drawdown);
        }

        // Periodic summary
        long now = System.currentTimeMillis();
        if (now - lastLogTimeMs > LOG_INTERVAL_MS) {
            lastLogTimeMs = now;
            long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("[ArbitrageEngine] 📊 Eval #{} | Exchanges={}/{} | " +
                            "Opps={} | executed={} rejected={} | Latency={}ms",
                    evalCount, snapshots.size(), EXCHANGES.length,
                    opportunities.size(), totalExecuted.get(), totalRejected.get(), elapsed);
        }
    }

    public long getTotalEvaluations() { return totalEvaluations.get(); }
    public long getTotalOpportunities() { return totalOpportunities.get(); }
    public long getTotalExecuted() { return totalExecuted.get(); }
    public long getTotalRejected() { return totalRejected.get(); }
}
