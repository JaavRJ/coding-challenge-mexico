package com.nexustrade.model;

import java.math.BigDecimal;
import java.util.NavigableMap;

/**
 * Immutable snapshot of an order book at a point in time.
 * Passed to the ArbitrageEngine to evaluate opportunities
 * without holding any locks on the live order book.
 */
public record OrderBookSnapshot(
        String exchange,
        String symbol,
        BigDecimal bestBidPrice,
        BigDecimal bestBidVolume,
        BigDecimal bestAskPrice,
        BigDecimal bestAskVolume,
        long timestampMs,
        NavigableMap<BigDecimal, BigDecimal> topBids,  // up to 5 levels
        NavigableMap<BigDecimal, BigDecimal> topAsks   // up to 5 levels
) {
    public boolean isValid() {
        return bestBidPrice != null
                && bestAskPrice != null
                && bestBidPrice.compareTo(BigDecimal.ZERO) > 0
                && bestAskPrice.compareTo(BigDecimal.ZERO) > 0;
    }

    public long ageMs() {
        return System.currentTimeMillis() - timestampMs;
    }

    @Override
    public String toString() {
        return String.format("[%s] Bid=%.2f(%.4f BTC) Ask=%.2f(%.4f BTC) age=%dms",
                exchange,
                bestBidPrice.doubleValue(), bestBidVolume.doubleValue(),
                bestAskPrice.doubleValue(), bestAskVolume.doubleValue(),
                ageMs());
    }
}
