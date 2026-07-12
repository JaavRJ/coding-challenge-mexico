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
import java.math.BigDecimal;
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
    private static final String[] EXCHANGES = {"BINANCE", "KRAKEN", "COINBASE", "BITFINEX", "OKX"};

    private final ConnectorRegistry registry;
    private final SpreadCalculator spreadCalculator;
    private final EngineConfig config;
    private final WalletManager walletManager;
    private final CircuitBreaker circuitBreaker;
    private final ShadowLearningRecorder recorder;
    private final LatencyTracker latencyTracker;
    private final DashboardController dashboardController;
    private final TriangularSpreadCalculator triangularCalculator;

    private final AtomicLong totalEvaluations = new AtomicLong(0);
    private final AtomicLong totalOpportunities = new AtomicLong(0);
    private final AtomicLong totalExecuted = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);

    private volatile long lastLogTimeMs = 0;
    private static final long LOG_INTERVAL_MS = 5000;

    private final com.nexustrade.persistence.DatabasePersistenceService dbPersistence;
    private final com.nexustrade.websocket.TradeBroadcaster wsBroadcaster;
    private final com.nexustrade.persistence.TradeRepository tradeRepo;
    private final AnomalyDetector anomalyDetector;
    private final ConfidenceScorer confidenceScorer;
    private final com.nexustrade.notifications.TelegramNotificationService telegramService;

    public ArbitrageEngine(ConnectorRegistry registry,
                           SpreadCalculator spreadCalculator,
                           EngineConfig config,
                           WalletManager walletManager,
                           CircuitBreaker circuitBreaker,
                           ShadowLearningRecorder recorder,
                           LatencyTracker latencyTracker,
                           DashboardController dashboardController,
                           TriangularSpreadCalculator triangularCalculator,
                           com.nexustrade.persistence.DatabasePersistenceService dbPersistence,
                           com.nexustrade.websocket.TradeBroadcaster wsBroadcaster,
                           com.nexustrade.persistence.TradeRepository tradeRepo,
                           AnomalyDetector anomalyDetector,
                           ConfidenceScorer confidenceScorer,
                           com.nexustrade.notifications.TelegramNotificationService telegramService) {
        this.registry = registry;
        this.spreadCalculator = spreadCalculator;
        this.config = config;
        this.walletManager = walletManager;
        this.circuitBreaker = circuitBreaker;
        this.recorder = recorder;
        this.latencyTracker = latencyTracker;
        this.dashboardController = dashboardController;
        this.triangularCalculator = triangularCalculator;
        this.dbPersistence = dbPersistence;
        this.wsBroadcaster = wsBroadcaster;
        this.tradeRepo = tradeRepo;
        this.anomalyDetector = anomalyDetector;
        this.confidenceScorer = confidenceScorer;
        this.telegramService = telegramService;
    }

    @PostConstruct
    public void init() {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("  🧠 ArbitrageEngine starting (Phase 3)");
        log.info("  Min ROI target: {}%", config.getEngine().getMinRoiPct());
        log.info("  Wallet exposure: {}%", config.getEngine().getWalletExposurePct());
        log.info("  Circuit breaker: {} losses → {}s pause",
                config.getRisk().getCircuitBreakerLosses(),
                config.getRisk().getCircuitBreakerPauseSeconds());
        log.info("  Wallet: {} USDT + {} BTC per exchange",
                config.getWallet().getInitialUsdt(), config.getWallet().getInitialBtc());
        try {
            long count = tradeRepo.countExecuted();
            totalExecuted.set(count);
            log.info("  📊 Restored {} executed trades from database", count);
        } catch (Exception e) {}
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

        // DIRECT ARBITRAGE (Inter-Exchange)
        for (int i = 0; i < snapshots.size(); i++) {
            for (int j = 0; j < snapshots.size(); j++) {
                if (i == j) continue;

                OrderBookSnapshot buyOn = snapshots.get(i);
                OrderBookSnapshot sellOn = snapshots.get(j);
                if (!buyOn.symbol().equals("BTC/USDT") || !sellOn.symbol().equals("BTC/USDT")) continue;

                if (sellOn.bestBidPrice().compareTo(buyOn.bestAskPrice()) <= 0) continue;

                BigDecimal maxVolume = BigDecimal.ZERO;
                com.nexustrade.risk.Wallet buyWallet = walletManager.getWallet(buyOn.exchange());
                com.nexustrade.risk.Wallet sellWallet = walletManager.getWallet(sellOn.exchange());
                
                if (buyWallet != null && sellWallet != null) {
                    double exposurePct = config.getEngine().getWalletExposurePct() / 100.0;
                    BigDecimal usdtAvailable = buyWallet.getUsdt().multiply(BigDecimal.valueOf(exposurePct));
                    BigDecimal btcAvailable = sellWallet.getBtc().multiply(BigDecimal.valueOf(exposurePct));
                    
                    BigDecimal buyPrice = buyOn.bestAskPrice();
                    if (buyPrice.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal affordableBtc = usdtAvailable.divide(buyPrice, 8, java.math.RoundingMode.HALF_DOWN);
                        maxVolume = affordableBtc.min(btcAvailable);
                    }
                }

                // Cap maxVolume to whatever is available on the order books
                maxVolume = maxVolume.min(buyOn.bestAskVolume()).min(sellOn.bestBidVolume());
                
                if (maxVolume.compareTo(BigDecimal.ZERO) <= 0) continue;

                Optional<ArbitrageOpportunity> oppOpt =
                        spreadCalculator.evaluate(buyOn, sellOn, startNanos, maxVolume);
                oppOpt.ifPresent(opportunities::add);
            }
        }

        // TRIANGULAR ARBITRAGE (Intra-Exchange on Binance)
        Optional<com.nexustrade.model.OrderBook> binanceBtcUsdt = registry.getOrderBook("BINANCE", "BTC/USDT");
        Optional<com.nexustrade.model.OrderBook> binanceEthBtc = registry.getOrderBook("BINANCE", "ETH/BTC");
        Optional<com.nexustrade.model.OrderBook> binanceEthUsdt = registry.getOrderBook("BINANCE", "ETH/USDT");

        if (binanceBtcUsdt.isPresent() && binanceEthBtc.isPresent() && binanceEthUsdt.isPresent()) {
            Optional<com.nexustrade.model.OrderBookSnapshot> btcUsdtSnap = binanceBtcUsdt.get().snapshot();
            Optional<com.nexustrade.model.OrderBookSnapshot> ethBtcSnap = binanceEthBtc.get().snapshot();
            Optional<com.nexustrade.model.OrderBookSnapshot> ethUsdtSnap = binanceEthUsdt.get().snapshot();

            if (btcUsdtSnap.isPresent() && ethBtcSnap.isPresent() && ethUsdtSnap.isPresent()) {
                BigDecimal startUsdt = BigDecimal.valueOf(0); // Default if wallet missing
                com.nexustrade.risk.Wallet binanceWallet = walletManager.getWallet("BINANCE");
                if (binanceWallet != null) {
                    double exposurePct = config.getEngine().getWalletExposurePct() / 100.0;
                    startUsdt = binanceWallet.getUsdt().multiply(BigDecimal.valueOf(exposurePct));
                }

                if (startUsdt.compareTo(BigDecimal.ZERO) <= 0) {
                    return; // No funds exposed
                }

                Optional<com.nexustrade.model.TriangularOpportunity> triOpt = triangularCalculator.calculate(
                        btcUsdtSnap.get(),
                        ethBtcSnap.get(),
                        ethUsdtSnap.get(),
                        startUsdt
                );
                
                triOpt.ifPresent(tri -> {
                com.nexustrade.model.TriangularOpportunity finalTri = tri;
                if (tri.isProfitable() && circuitBreaker.isActive()) {
                    finalTri = tri.withStatus(com.nexustrade.model.TradeStatus.REJECTED_CIRCUIT_BREAKER, "Circuit breaker is active");
                }
                
                latencyTracker.recordDecisionLatency(finalTri.decisionLatencyMs());
                recorder.record(finalTri);
                dashboardController.broadcastTriangular(finalTri);
                wsBroadcaster.broadcastTrade(finalTri);
                
                if (finalTri.isProfitable()) {
                    boolean traded = walletManager.executeTriangularTrade(finalTri);
                    if (traded) {
                        totalExecuted.incrementAndGet();
                        circuitBreaker.recordProfit();
                        dbPersistence.recordTriangularTrade(finalTri);
                        log.info("[ArbitrageEngine] {}", finalTri);
                    } else {
                        totalRejected.incrementAndGet();
                        circuitBreaker.recordLoss();
                    }
                } else {
                    totalRejected.incrementAndGet();
                }
            });
            } // Close the inner if (btcUsdtSnap.isPresent() ...)
        } // Close the outer if (binanceBtcUsdt.isPresent() ...)

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

            // Record to JSONL + SSE + WS
            recorder.record(finalOpp);
            dashboardController.broadcast(finalOpp);
            wsBroadcaster.broadcastTrade(finalOpp);

            if (finalOpp.isProfitable()) {
                // Calculate AI confidence score and anomaly level
                AnomalyDetector.AnomalyResult anomaly = anomalyDetector.analyze(
                        finalOpp.buyExchange(), finalOpp.sellExchange(),
                        finalOpp.grossSpread().doubleValue());
                int aiScore = confidenceScorer.score(
                        finalOpp.grossSpread().doubleValue(),
                        config.getEngine().getMinRoiPct(),
                        finalOpp.volume().doubleValue() * 60000.0, // approx USDT
                        10, 10, // depth levels approximation
                        finalOpp.decisionLatencyMs(), anomaly.level());

                // Execute simulated trade
                boolean traded = walletManager.executeTrade(finalOpp);
                if (traded) {
                    totalExecuted.incrementAndGet();
                    circuitBreaker.recordProfit();
                    dbPersistence.recordDirectTrade(finalOpp);
                    dbPersistence.setAiScore(finalOpp, aiScore);
                    // Telegram alert
                    telegramService.sendTradeAlert(
                            finalOpp.buyExchange(), finalOpp.sellExchange(),
                            finalOpp.netProfit(), finalOpp.grossSpread().doubleValue(),
                            finalOpp.decisionLatencyMs(), aiScore,
                            getTotalNetProfit());
                    log.info("[ArbitrageEngine] ✅ Trade | AI={}/100 | Anomaly={} | {}",
                            aiScore, anomaly.level(), finalOpp);
                } else {
                    totalRejected.incrementAndGet();
                    circuitBreaker.recordLoss();
                }
            } else {
                totalRejected.incrementAndGet();
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
    public long getTotalExecuted() {
        try { return tradeRepo.countExecuted(); } catch (Exception e) { return totalExecuted.get(); }
    }
    public java.math.BigDecimal getTotalNetProfit() {
        try { return tradeRepo.sumNetProfit(); } catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }
    public long getTotalRejected() { return totalRejected.get(); }
}
