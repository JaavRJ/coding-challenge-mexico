package com.nexustrade.api;

import com.nexustrade.connector.ConnectorRegistry;
import com.nexustrade.engine.EngineConfig;
import com.nexustrade.risk.RebalancingService;
import com.nexustrade.risk.Wallet;
import com.nexustrade.risk.WalletManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST endpoints for wallet rebalancing operations.
 *
 * <pre>
 *   GET  /api/rebalancing        – returns full rebalancing status
 *   POST /api/rebalancing/force  – triggers an immediate manual rebalance
 * </pre>
 */
@RestController
@RequestMapping("/api/rebalancing")
@CrossOrigin(originPatterns = "*")
public class RebalancingController {

    private static final Logger log = LoggerFactory.getLogger(RebalancingController.class);

    /** Fallback BTC price when no live order-book data is available. */
    private static final BigDecimal FALLBACK_BTC_PRICE = BigDecimal.valueOf(65_000);

    private final WalletManager walletManager;
    private final RebalancingService rebalancingService;
    private final ConnectorRegistry connectorRegistry;
    private final EngineConfig engineConfig;

    public RebalancingController(WalletManager walletManager,
                                 RebalancingService rebalancingService,
                                 ConnectorRegistry connectorRegistry,
                                 EngineConfig engineConfig) {
        this.walletManager       = walletManager;
        this.rebalancingService  = rebalancingService;
        this.connectorRegistry   = connectorRegistry;
        this.engineConfig        = engineConfig;
    }

    /**
     * Returns the current rebalancing status including wallet balances,
     * asymmetry metrics, and event history.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Wallet> wallets = walletManager.getAllWallets();
        BigDecimal btcPrice = resolveBtcPrice();
        double thresholdPct = engineConfig.getRisk().getRebalanceThresholdPct();

        Map<String, Object> status = rebalancingService.getStatus(wallets, btcPrice, thresholdPct);
        return ResponseEntity.ok(status);
    }

    /**
     * Triggers an immediate manual rebalance across all wallets.
     * Returns the updated status after rebalancing.
     */
    @PostMapping("/force")
    public ResponseEntity<Map<String, Object>> forceRebalance() {
        log.info("🔄 Manual rebalance requested via REST");
        Map<String, Wallet> wallets = walletManager.getAllWallets();
        BigDecimal btcPrice = resolveBtcPrice();
        double thresholdPct = engineConfig.getRisk().getRebalanceThresholdPct();

        rebalancingService.forceRebalance(wallets, btcPrice);

        Map<String, Object> status = rebalancingService.getStatus(wallets, btcPrice, thresholdPct);
        return ResponseEntity.ok(status);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Attempts to read the current BTC/USDT best bid from the Binance order book.
     * Falls back to {@link #FALLBACK_BTC_PRICE} if the book is unavailable or empty.
     */
    private BigDecimal resolveBtcPrice() {
        try {
            return connectorRegistry.getOrderBook("BINANCE", "BTC/USDT")
                    .flatMap(ob -> ob.bestBidPrice())
                    .orElse(FALLBACK_BTC_PRICE);
        } catch (Exception e) {
            log.warn("Could not resolve live BTC price, using fallback {}: {}",
                     FALLBACK_BTC_PRICE, e.getMessage());
            return FALLBACK_BTC_PRICE;
        }
    }
}
