package com.nexustrade.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * Estimates Volume-Weighted Average Price (VWAP) across multiple order book levels.
 *
 * Instead of assuming we can buy/sell at the best price, this walks the order book
 * to calculate the realistic average price for a given volume, accounting for
 * the fact that large orders consume multiple price levels.
 *
 * VWAP formula:
 *   VWAP = Σ(price_i × min(available_i, remaining)) / totalFilled
 *
 * Example: buying 0.1 BTC when the ask side has:
 *   Level 1: 67,400 × 0.03 BTC
 *   Level 2: 67,410 × 0.05 BTC  
 *   Level 3: 67,425 × 0.10 BTC
 *   VWAP = (67400×0.03 + 67410×0.05 + 67425×0.02) / 0.10 = 67,411.50
 */
@Component
public class SlippageEstimator {

    private static final Logger log = LoggerFactory.getLogger(SlippageEstimator.class);
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    /**
     * Calculates the VWAP for buying {@code desiredVolume} BTC from the ask side.
     *
     * @param topAsks   Ask levels sorted ascending (lowest price first = best ask first)
     * @param desiredVolume  How much BTC we want to buy
     * @return VWAP buy price, or empty if insufficient liquidity
     */
    public Optional<BigDecimal> estimateBuyVwap(NavigableMap<BigDecimal, BigDecimal> topAsks,
                                                 BigDecimal desiredVolume) {
        return computeVwap(topAsks, desiredVolume);
    }

    /**
     * Calculates the VWAP for selling {@code desiredVolume} BTC into the bid side.
     *
     * @param topBids   Bid levels sorted descending (highest price first = best bid first)
     * @param desiredVolume  How much BTC we want to sell
     * @return VWAP sell price, or empty if insufficient liquidity
     */
    public Optional<BigDecimal> estimateSellVwap(NavigableMap<BigDecimal, BigDecimal> topBids,
                                                  BigDecimal desiredVolume) {
        return computeVwap(topBids, desiredVolume);
    }

    /**
     * Walks price levels to compute VWAP for a desired volume.
     * Works for both bid and ask sides — the map iteration order determines
     * which levels are consumed first (best prices first).
     */
    private Optional<BigDecimal> computeVwap(NavigableMap<BigDecimal, BigDecimal> levels,
                                              BigDecimal desiredVolume) {
        if (levels == null || levels.isEmpty() || desiredVolume == null
                || desiredVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal totalCost = BigDecimal.ZERO;      // Σ(price × filled_at_this_level)
        BigDecimal totalFilled = BigDecimal.ZERO;     // Σ(filled_at_this_level)
        BigDecimal remaining = desiredVolume;

        for (Map.Entry<BigDecimal, BigDecimal> level : levels.entrySet()) {
            BigDecimal price = level.getKey();
            BigDecimal available = level.getValue();

            if (price == null || available == null
                    || price.compareTo(BigDecimal.ZERO) <= 0
                    || available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // Fill as much as possible at this level
            BigDecimal fill = remaining.min(available);
            totalCost = totalCost.add(price.multiply(fill, MC));
            totalFilled = totalFilled.add(fill);
            remaining = remaining.subtract(fill);

            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break; // Fully filled
            }
        }

        // Check if we could fill the entire volume
        if (totalFilled.compareTo(desiredVolume) < 0) {
            log.debug("Insufficient liquidity: wanted {} BTC, available {} BTC across {} levels",
                    desiredVolume.toPlainString(), totalFilled.toPlainString(), levels.size());
            return Optional.empty();
        }

        // VWAP = totalCost / totalFilled
        BigDecimal vwap = totalCost.divide(totalFilled, MC);
        return Optional.of(vwap);
    }

    /**
     * Calculates the maximum volume that can be traded given the available
     * liquidity on both sides, capped by maxVolume.
     *
     * @return The tradeable volume, or empty if either side has zero liquidity
     */
    public Optional<BigDecimal> maxTradeableVolume(NavigableMap<BigDecimal, BigDecimal> asks,
                                                    NavigableMap<BigDecimal, BigDecimal> bids,
                                                    BigDecimal maxVolume) {
        BigDecimal askLiquidity = totalVolume(asks);
        BigDecimal bidLiquidity = totalVolume(bids);

        BigDecimal available = askLiquidity.min(bidLiquidity).min(maxVolume);

        if (available.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(available);
    }

    private BigDecimal totalVolume(NavigableMap<BigDecimal, BigDecimal> levels) {
        if (levels == null || levels.isEmpty()) return BigDecimal.ZERO;
        return levels.values().stream()
                .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
