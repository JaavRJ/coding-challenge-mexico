package com.nexustrade.model;

import java.math.BigDecimal;

/**
 * Immutable record representing a detected arbitrage opportunity.
 * 
 * Every spread detected by the engine generates one of these, regardless
 * of whether it was profitable (EXECUTED) or rejected (REJECTED_*).
 * This enables Shadow Learning: analyzing rejected opportunities to
 * tune parameters and understand market microstructure.
 *
 * Profitability equation:
 *   NetProfit = [sellPrice × volume × (1 - feeSell)] 
 *             - [buyPrice × volume × (1 + feeBuy)] 
 *             - withdrawalFee
 */
public record ArbitrageOpportunity(
        /** Epoch millis when the opportunity was detected */
        long timestampMs,

        /** Exchange where we buy (lower ask price) */
        String buyExchange,

        /** Exchange where we sell (higher bid price) */
        String sellExchange,

        /** VWAP buy price (weighted across order book levels) */
        BigDecimal buyPrice,

        /** VWAP sell price (weighted across order book levels) */
        BigDecimal sellPrice,

        /** Volume in BTC to trade */
        BigDecimal volume,

        /** Gross spread: sellPrice - buyPrice (before fees) */
        BigDecimal grossSpread,

        /** Total fees from both exchanges + withdrawal */
        BigDecimal feesTotal,

        /** Net profit after all costs */
        BigDecimal netProfit,

        /** Percentage spread: grossSpread / buyPrice × 100 */
        BigDecimal spreadPct,

        /** Trade classification for Shadow Learning */
        TradeStatus status,

        /** Human-readable rejection reason, null if EXECUTED */
        String rejectionReason,

        /** Milliseconds from order book ingestion to decision */
        long decisionLatencyMs
) {
    public boolean isProfitable() {
        return status == TradeStatus.EXECUTED;
    }

    @Override
    public String toString() {
        if (status.isExecuted()) {
            return String.format(
                    "✅ EXECUTED: Buy@%s(%.2f) Sell@%s(%.2f) Vol=%.4f Gross=%.2f Fees=%.2f Net=%.2f (%.4f%%) Latency=%dms",
                    buyExchange, buyPrice, sellExchange, sellPrice, volume,
                    grossSpread, feesTotal, netProfit, spreadPct, decisionLatencyMs);
        } else {
            return String.format(
                    "❌ %s: Buy@%s(%.2f) Sell@%s(%.2f) Gross=%.2f Fees=%.2f Net=%.2f | %s",
                    status, buyExchange, buyPrice, sellExchange, sellPrice,
                    grossSpread, feesTotal, netProfit,
                    rejectionReason != null ? rejectionReason : "");
        }
    }
}
