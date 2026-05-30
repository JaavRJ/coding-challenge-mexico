package com.nexustrade.risk;

import com.nexustrade.engine.EngineConfig;
import com.nexustrade.model.ArbitrageOpportunity;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages virtual wallets across exchanges.
 * Executes simulated trades and handles rebalancing when asymmetry > 40%.
 */
@Component
public class WalletManager {

    private static final Logger log = LoggerFactory.getLogger(WalletManager.class);
    private static final String[] EXCHANGES = {"BINANCE", "KRAKEN", "COINBASE"};

    private final EngineConfig config;
    private final Map<String, Wallet> wallets = new ConcurrentHashMap<>();

    private BigDecimal initialTotalUsdt;

    public WalletManager(EngineConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        double usdt = config.getWallet().getInitialUsdt();
        double btc = config.getWallet().getInitialBtc();

        for (String ex : EXCHANGES) {
            wallets.put(ex, new Wallet(ex, usdt, btc));
        }

        // Will be updated dynamically on first evaluation
        initialTotalUsdt = null;

        log.info("💰 WalletManager initialized: {} USDT + {} BTC per exchange ({})",
                usdt, btc, EXCHANGES.length);
    }

    /**
     * Execute a simulated trade: buy on buyExchange, sell on sellExchange.
     * @return true if trade was executed successfully
     */
    public boolean executeTrade(ArbitrageOpportunity opp) {
        Wallet buyWallet = wallets.get(opp.buyExchange());
        Wallet sellWallet = wallets.get(opp.sellExchange());

        if (buyWallet == null || sellWallet == null) return false;

        BigDecimal volume = opp.volume();
        BigDecimal buyCost = opp.buyPrice().multiply(volume)
                .multiply(BigDecimal.ONE.add(config.getTakerFee(opp.buyExchange())));
        BigDecimal sellRevenue = opp.sellPrice().multiply(volume)
                .multiply(BigDecimal.ONE.subtract(config.getTakerFee(opp.sellExchange())));

        boolean bought = buyWallet.buy(buyCost, volume);
        if (!bought) return false;

        boolean sold = sellWallet.sell(volume, sellRevenue);
        if (!sold) {
            // Rollback
            buyWallet.sell(volume, buyCost);
            return false;
        }

        log.info("💰 Trade executed: {} | Buy@{} cost={} | Sell@{} rev={} | P&L={}",
                volume.toPlainString(), opp.buyExchange(), buyCost.setScale(2, RoundingMode.HALF_UP),
                opp.sellExchange(), sellRevenue.setScale(2, RoundingMode.HALF_UP),
                sellRevenue.subtract(buyCost).setScale(2, RoundingMode.HALF_UP));

        checkRebalance(opp.buyPrice());
        return true;
    }

    /**
     * Execute a simulated triangular trade on a single exchange.
     * @return true if trade was executed successfully
     */
    public boolean executeTriangularTrade(com.nexustrade.model.TriangularOpportunity opp) {
        Wallet wallet = wallets.get(opp.exchange());
        if (wallet == null) return false;

        BigDecimal netProfit = opp.netProfit();
        if (netProfit.compareTo(BigDecimal.ZERO) > 0) {
            wallet.receiveUsdt(netProfit);
            log.info("💰 Triangular Trade executed: [{}] Net={} USDT | Latency={}ms",
                    opp.exchange(), netProfit.setScale(2, RoundingMode.HALF_UP), opp.decisionLatencyMs());
            return true;
        }
        return false;
    }

    /** Check if wallets need rebalancing (asymmetry > 40%) */
    private void checkRebalance(BigDecimal btcPrice) {
        double threshold = config.getRisk().getRebalanceThresholdPct() / 100.0;
        BigDecimal totalBtc = BigDecimal.ZERO;

        for (Wallet w : wallets.values()) {
            totalBtc = totalBtc.add(w.getBtc());
        }

        if (totalBtc.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal avgBtc = totalBtc.divide(BigDecimal.valueOf(wallets.size()), 8, RoundingMode.HALF_UP);

        for (Wallet w : wallets.values()) {
            BigDecimal diff = w.getBtc().subtract(avgBtc).abs();
            BigDecimal ratio = diff.divide(totalBtc, 8, RoundingMode.HALF_UP);

            if (ratio.doubleValue() > threshold) {
                log.info("🔄 Rebalancing triggered for {} (asymmetry {}%)",
                        w.getExchange(), String.format("%.1f", ratio.doubleValue() * 100));
                rebalance(avgBtc);
                return;
            }
        }
    }

    /** Redistribute BTC evenly across all wallets */
    private void rebalance(BigDecimal targetBtcPerWallet) {
        for (Wallet w : wallets.values()) {
            BigDecimal diff = w.getBtc().subtract(targetBtcPerWallet);
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                w.transferBtcOut(diff);
            }
        }
        for (Wallet w : wallets.values()) {
            BigDecimal diff = targetBtcPerWallet.subtract(w.getBtc());
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                w.receiveBtc(diff);
            }
        }
        log.info("🔄 Rebalance complete. Target: {} BTC per exchange", targetBtcPerWallet);
    }

    /** Total P&L across all wallets */
    public BigDecimal getTotalPnl(BigDecimal btcPrice) {
        BigDecimal currentTotal = BigDecimal.ZERO;
        for (Wallet w : wallets.values()) {
            currentTotal = currentTotal.add(w.totalValueUsdt(btcPrice));
        }
        
        if (initialTotalUsdt == null) {
            initialTotalUsdt = currentTotal;
        }
        
        return currentTotal.subtract(initialTotalUsdt);
    }

    /** Get current balance drawdown percentage */
    public double getDrawdownPct(BigDecimal btcPrice) {
        BigDecimal pnl = getTotalPnl(btcPrice);
        if (initialTotalUsdt.compareTo(BigDecimal.ZERO) <= 0) return 0;
        return pnl.divide(initialTotalUsdt, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        for (Wallet w : wallets.values()) {
            Map<String, Object> ws = new LinkedHashMap<>();
            ws.put("usdt", w.getUsdt().setScale(2, RoundingMode.HALF_UP));
            ws.put("btc", w.getBtc().setScale(6, RoundingMode.HALF_UP));
            status.put(w.getExchange(), ws);
        }
        return status;
    }

    public Wallet getWallet(String exchange) { return wallets.get(exchange); }
}
