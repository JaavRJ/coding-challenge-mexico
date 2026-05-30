package com.nexustrade.model;

import java.math.BigDecimal;

/**
 * Immutable record representing a detected triangular arbitrage opportunity.
 * (e.g., USDT -> BTC -> ETH -> USDT).
 */
public record TriangularOpportunity(
        /** Epoch millis when the opportunity was detected */
        long timestampMs,

        /** Exchange where the triangular arbitrage occurs */
        String exchange,

        /** Starting USDT amount */
        BigDecimal startUsdt,

        /** BTC amount acquired after Step 1 (USDT -> BTC) */
        BigDecimal btcAmount,

        /** ETH amount acquired after Step 2 (BTC -> ETH) */
        BigDecimal ethAmount,

        /** Final USDT amount acquired after Step 3 (ETH -> USDT) */
        BigDecimal finalUsdt,

        /** Total fees paid across all 3 trades */
        BigDecimal feesTotal,

        /** Net profit after all costs (finalUsdt - startUsdt) */
        BigDecimal netProfit,

        /** Percentage spread (netProfit / startUsdt * 100) */
        BigDecimal spreadPct,

        /** Trade classification */
        TradeStatus status,

        /** Human-readable rejection reason, null if EXECUTED */
        String rejectionReason,

        /** Milliseconds from order book ingestion to decision */
        long decisionLatencyMs
) {
    public boolean isProfitable() {
        return status == TradeStatus.EXECUTED;
    }

    public TriangularOpportunity withStatus(TradeStatus newStatus, String newReason) {
        return new TriangularOpportunity(timestampMs, exchange, startUsdt, btcAmount, ethAmount,
                finalUsdt, feesTotal, netProfit, spreadPct, newStatus, newReason, decisionLatencyMs);
    }

    @Override
    public String toString() {
        if (status.isExecuted()) {
            return String.format(
                    "✅ TRIANGULAR EXECUTED [%s]: 1000USDT -> %.4f BTC -> %.4f ETH -> %.2f USDT | Net=%.2f (%.4f%%) Latency=%dms",
                    exchange, btcAmount, ethAmount, finalUsdt, netProfit, spreadPct, decisionLatencyMs);
        } else {
            return String.format(
                    "❌ TRIANGULAR %s [%s]: 1000USDT -> %.4f BTC -> %.4f ETH -> %.2f USDT | Net=%.2f | %s",
                    status, exchange, btcAmount, ethAmount, finalUsdt, netProfit,
                    rejectionReason != null ? rejectionReason : "");
        }
    }
}
