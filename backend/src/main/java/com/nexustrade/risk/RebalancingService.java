package com.nexustrade.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks wallet asymmetry across exchanges and executes virtual BTC rebalancing.
 *
 * <p>The service keeps a bounded history of up to 100 {@link RebalancingEvent} entries
 * and exposes both automatic (threshold-based) and manual rebalancing triggers.</p>
 */
@Service
public class RebalancingService {

    private static final Logger log = LoggerFactory.getLogger(RebalancingService.class);
    private static final int MAX_HISTORY = 100;
    private static final int STATUS_HISTORY_LIMIT = 20;

    /** Circular history buffer – access is synchronised on {@code history}. */
    private final Deque<RebalancingEvent> history = new ArrayDeque<>(MAX_HISTORY);
    private final AtomicInteger totalRebalances = new AtomicInteger(0);

    // ──────────────────────────────────────────────────────────────────────────
    // Inner record
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Immutable record of a single rebalancing event.
     *
     * @param timestampMs      epoch time when the event occurred
     * @param trigger          {@code "AUTO"} or {@code "MANUAL"}
     * @param exchangeFrom     exchange that donated BTC (highest excess)
     * @param exchangeTo       exchange that received BTC (highest deficit)
     * @param btcAmount        total BTC redistributed
     * @param usdtEquivalent   USDT value of the redistributed BTC at execution time
     * @param asymmetryPctBefore maximum asymmetry percentage before rebalancing
     * @param asymmetryPctAfter  maximum asymmetry percentage after rebalancing
     */
    public record RebalancingEvent(
            long timestampMs,
            String trigger,
            String exchangeFrom,
            String exchangeTo,
            BigDecimal btcAmount,
            BigDecimal usdtEquivalent,
            double asymmetryPctBefore,
            double asymmetryPctAfter
    ) {}

    // ──────────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Checks whether any wallet's BTC deviation from the average exceeds
     * {@code thresholdPct}, and if so executes a rebalance.
     *
     * @param wallets      map of exchange name → Wallet
     * @param btcPrice     current BTC price in USDT (used for USDT equivalent logging)
     * @param thresholdPct rebalance trigger threshold (e.g. {@code 40.0} = 40%)
     * @return {@code true} if a rebalance was executed
     */
    public boolean checkAndRebalance(Map<String, Wallet> wallets, BigDecimal btcPrice, double thresholdPct) {
        if (wallets == null || wallets.isEmpty()) return false;

        double maxAsymmetryPct = computeMaxAsymmetryPct(wallets);
        if (maxAsymmetryPct <= thresholdPct) return false;

        log.info("🔄 [AUTO] Rebalance triggered – max asymmetry {}% > threshold {}%",
                String.format("%.1f", maxAsymmetryPct), String.format("%.1f", thresholdPct));
        executeRebalance(wallets, btcPrice, "AUTO", maxAsymmetryPct);
        return true;
    }

    /**
     * Unconditionally redistributes BTC evenly across all wallets.
     * Intended for manual triggers via the REST API.
     *
     * @param wallets  map of exchange name → Wallet
     * @param btcPrice current BTC price in USDT
     */
    public void forceRebalance(Map<String, Wallet> wallets, BigDecimal btcPrice) {
        if (wallets == null || wallets.isEmpty()) return;
        double asymmetryBefore = computeMaxAsymmetryPct(wallets);
        log.info("\uD83D\uDD04 [MANUAL] Force rebalance requested \u2013 current asymmetry {}%",
                String.format("%.1f", asymmetryBefore));
        executeRebalance(wallets, btcPrice, "MANUAL", asymmetryBefore);
    }

    /**
     * Builds a comprehensive status map suitable for the REST API response.
     *
     * @param wallets      current wallet state
     * @param btcPrice     current BTC price in USDT
     * @param thresholdPct configured rebalance threshold
     * @return map with keys: {@code balanced}, {@code maxAsymmetryPct},
     *         {@code thresholdPct}, {@code walletBalances}, {@code history},
     *         {@code totalRebalances}
     */
    public Map<String, Object> getStatus(Map<String, Wallet> wallets, BigDecimal btcPrice, double thresholdPct) {
        Map<String, Object> status = new LinkedHashMap<>();

        double maxAsymmetryPct = (wallets == null || wallets.isEmpty())
                ? 0.0 : computeMaxAsymmetryPct(wallets);

        status.put("balanced", maxAsymmetryPct <= thresholdPct);
        status.put("maxAsymmetryPct", round2(maxAsymmetryPct));
        status.put("thresholdPct", thresholdPct);
        status.put("totalRebalances", totalRebalances.get());

        // Per-wallet breakdown
        Map<String, Object> walletBalances = new LinkedHashMap<>();
        if (wallets != null && !wallets.isEmpty()) {
            BigDecimal grandTotal = wallets.values().stream()
                    .map(w -> w.totalValueUsdt(btcPrice))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            for (Map.Entry<String, Wallet> entry : wallets.entrySet()) {
                Wallet w = entry.getValue();
                BigDecimal walletTotal = w.totalValueUsdt(btcPrice);
                double pctOfTotal = grandTotal.compareTo(BigDecimal.ZERO) > 0
                        ? walletTotal.divide(grandTotal, 8, RoundingMode.HALF_UP)
                              .multiply(BigDecimal.valueOf(100)).doubleValue()
                        : 0.0;

                Map<String, Object> wb = new LinkedHashMap<>();
                wb.put("usdt", w.getUsdt().setScale(2, RoundingMode.HALF_UP));
                wb.put("btc", w.getBtc().setScale(8, RoundingMode.HALF_UP));
                wb.put("usdtValue", walletTotal.setScale(2, RoundingMode.HALF_UP));
                wb.put("pctOfTotal", round2(pctOfTotal));
                walletBalances.put(entry.getKey(), wb);
            }
        }
        status.put("walletBalances", walletBalances);

        // Last 20 events (most recent first)
        List<RebalancingEvent> recentHistory;
        synchronized (history) {
            recentHistory = new ArrayList<>(history);
        }
        // Deque is insertion-ordered (FIFO), so reverse for newest-first
        List<RebalancingEvent> newestFirst = new ArrayList<>(recentHistory.subList(
                Math.max(0, recentHistory.size() - STATUS_HISTORY_LIMIT),
                recentHistory.size()
        ));
        java.util.Collections.reverse(newestFirst);
        status.put("history", newestFirst);

        return status;
    }

    /**
     * Returns the full rebalancing history (up to 100 entries), oldest first.
     */
    public List<RebalancingEvent> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Computes the maximum percentage deviation of any single wallet's BTC
     * from the fleet average, expressed as a fraction of total BTC (× 100).
     */
    private double computeMaxAsymmetryPct(Map<String, Wallet> wallets) {
        BigDecimal totalBtc = wallets.values().stream()
                .map(Wallet::getBtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalBtc.compareTo(BigDecimal.ZERO) <= 0) return 0.0;

        BigDecimal avgBtc = totalBtc.divide(
                BigDecimal.valueOf(wallets.size()), 8, RoundingMode.HALF_UP);

        double max = 0.0;
        for (Wallet w : wallets.values()) {
            BigDecimal diff = w.getBtc().subtract(avgBtc).abs();
            double pct = diff.divide(totalBtc, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
            if (pct > max) max = pct;
        }
        return max;
    }

    /**
     * Identifies the wallet with the most excess BTC and the one with the
     * largest deficit, then redistributes BTC evenly.
     */
    private void executeRebalance(Map<String, Wallet> wallets, BigDecimal btcPrice,
                                   String trigger, double asymmetryBefore) {
        BigDecimal totalBtc = wallets.values().stream()
                .map(Wallet::getBtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalBtc.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal targetBtc = totalBtc.divide(
                BigDecimal.valueOf(wallets.size()), 8, RoundingMode.HALF_UP);

        // Determine "from" and "to" exchanges for event logging
        Wallet fromWallet = null;
        Wallet toWallet = null;
        BigDecimal maxExcess = BigDecimal.ZERO;
        BigDecimal maxDeficit = BigDecimal.ZERO;

        for (Wallet w : wallets.values()) {
            BigDecimal diff = w.getBtc().subtract(targetBtc);
            if (diff.compareTo(maxExcess) > 0) {
                maxExcess = diff;
                fromWallet = w;
            } else if (diff.negate().compareTo(maxDeficit) > 0) {
                maxDeficit = diff.negate();
                toWallet = w;
            }
        }

        // Step 1: drain all excess
        for (Wallet w : wallets.values()) {
            BigDecimal diff = w.getBtc().subtract(targetBtc);
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                w.transferBtcOut(diff);
            }
        }

        // Step 2: top up all deficits
        for (Wallet w : wallets.values()) {
            BigDecimal diff = targetBtc.subtract(w.getBtc());
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                w.receiveBtc(diff);
            }
        }

        double asymmetryAfter = computeMaxAsymmetryPct(wallets);

        // Build and store event
        String fromName = fromWallet != null ? fromWallet.getExchange() : "N/A";
        String toName   = toWallet   != null ? toWallet.getExchange()   : "N/A";
        BigDecimal moved = maxExcess.max(BigDecimal.ZERO);
        BigDecimal usdtEquivalent = moved.multiply(btcPrice).setScale(2, RoundingMode.HALF_UP);

        RebalancingEvent event = new RebalancingEvent(
                System.currentTimeMillis(),
                trigger,
                fromName,
                toName,
                moved.setScale(8, RoundingMode.HALF_UP),
                usdtEquivalent,
                round2(asymmetryBefore),
                round2(asymmetryAfter)
        );
        recordEvent(event);
        totalRebalances.incrementAndGet();

        log.info("🔄 [{}] Rebalance complete – {} BTC moved (≈ {} USDT) | asymmetry {} → {}%",
                trigger, moved.toPlainString(), usdtEquivalent,
                round2(asymmetryBefore), round2(asymmetryAfter));
    }

    /** Appends an event, evicting the oldest if at capacity. */
    private void recordEvent(RebalancingEvent event) {
        synchronized (history) {
            if (history.size() >= MAX_HISTORY) {
                history.pollFirst();
            }
            history.addLast(event);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
