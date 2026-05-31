package com.nexustrade.engine;

import com.nexustrade.model.ArbitrageOpportunity;
import com.nexustrade.model.OrderBookSnapshot;
import com.nexustrade.model.TradeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Calculates arbitrage profitability between two exchanges.
 *
 * Profitability equation (from PROJECT_PLAN.md):
 *   Net = [P_bid × V × (1 - f_sell)] - [P_ask × V × (1 + f_buy)] - withdrawal_fee_usd
 *
 * Where:
 *   P_ask = VWAP buy price on the "buy" exchange (lower ask)
 *   P_bid = VWAP sell price on the "sell" exchange (higher bid)
 *   f_buy, f_sell = taker fees (decimal) of the respective exchanges
 *   withdrawal_fee = BTC withdrawal cost converted to USD
 *   V = volume in BTC
 */
@Component
public class SpreadCalculator {

    private static final Logger log = LoggerFactory.getLogger(SpreadCalculator.class);
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private final SlippageEstimator slippageEstimator;
    private final EngineConfig config;

    public SpreadCalculator(SlippageEstimator slippageEstimator, EngineConfig config) {
        this.slippageEstimator = slippageEstimator;
        this.config = config;
    }

    /**
     * Evaluates a potential arbitrage: buy on {@code buySnapshot} (lower ask),
     * sell on {@code sellSnapshot} (higher bid).
     *
     * @param buySnapshot   Order book where we BUY (consume the ask side)
     * @param sellSnapshot  Order book where we SELL (consume the bid side)
     * @param startNanos    System.nanoTime() when evaluation started (for latency tracking)
     * @return The opportunity with classification, or empty if snapshots are invalid
     */
    public Optional<ArbitrageOpportunity> evaluate(OrderBookSnapshot buySnapshot,
                                                    OrderBookSnapshot sellSnapshot,
                                                    long startNanos,
                                                    BigDecimal maxVolume) {
        if (!buySnapshot.isValid() || !sellSnapshot.isValid()) {
            return Optional.empty();
        }

        // 1. Determine tradeable volume (limited by both sides' liquidity and max config)
        Optional<BigDecimal> volumeOpt = slippageEstimator.maxTradeableVolume(
                buySnapshot.topAsks(), sellSnapshot.topBids(), maxVolume);

        if (volumeOpt.isEmpty()) {
            return buildRejected(buySnapshot, sellSnapshot, startNanos,
                    TradeStatus.REJECTED_LIQUIDITY, "No tradeable volume available",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal volume = volumeOpt.get();

        // 2. Calculate VWAP prices across order book levels
        Optional<BigDecimal> buyVwapOpt = slippageEstimator.estimateBuyVwap(
                buySnapshot.topAsks(), volume);
        Optional<BigDecimal> sellVwapOpt = slippageEstimator.estimateSellVwap(
                sellSnapshot.topBids(), volume);

        if (buyVwapOpt.isEmpty() || sellVwapOpt.isEmpty()) {
            return buildRejected(buySnapshot, sellSnapshot, startNanos,
                    TradeStatus.REJECTED_LIQUIDITY, "Insufficient depth for VWAP calculation",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal buyVwap = buyVwapOpt.get();
        BigDecimal sellVwap = sellVwapOpt.get();

        // 3. Gross spread (before fees)
        BigDecimal grossSpread = sellVwap.subtract(buyVwap);

        // 4. Calculate fees
        BigDecimal feeBuy = config.getTakerFee(buySnapshot.exchange());   // e.g., 0.001
        BigDecimal feeSell = config.getTakerFee(sellSnapshot.exchange()); // e.g., 0.0026

        // Cost of buying: P_ask × V × f_buy
        BigDecimal buyCost = buyVwap.multiply(volume, MC).multiply(feeBuy, MC);
        // Cost of selling: P_bid × V × f_sell
        BigDecimal sellCost = sellVwap.multiply(volume, MC).multiply(feeSell, MC);
        // Withdrawal fee in USD: withdrawal_fee_btc × buy_price
        BigDecimal withdrawalBtc = config.getWithdrawalFee(buySnapshot.exchange());
        BigDecimal withdrawalUsd = withdrawalBtc.multiply(buyVwap, MC);

        BigDecimal feesTotal = buyCost.add(sellCost).add(withdrawalUsd);

        // 5. Net profit
        // Net = [sellVwap × V × (1 - feeSell)] - [buyVwap × V × (1 + feeBuy)] - withdrawalUsd
        BigDecimal sellRevenue = sellVwap.multiply(volume, MC)
                .multiply(BigDecimal.ONE.subtract(feeSell), MC);
        BigDecimal buyCostTotal = buyVwap.multiply(volume, MC)
                .multiply(BigDecimal.ONE.add(feeBuy), MC);
        BigDecimal netProfit = sellRevenue.subtract(buyCostTotal).subtract(withdrawalUsd);

        // 6. Spread percentage
        BigDecimal spreadPct = BigDecimal.ZERO;
        if (buyVwap.compareTo(BigDecimal.ZERO) > 0) {
            spreadPct = grossSpread.divide(buyVwap, MC)
                    .multiply(new BigDecimal("100"), MC);
        }

        // 7. Decision latency
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

        // 8. Classify the opportunity
        TradeStatus status;
        String rejectionReason = null;

        BigDecimal requiredProfit = buyCostTotal.multiply(BigDecimal.valueOf(config.getEngine().getMinRoiPct() / 100.0), MC);

        if (latencyMs > config.getEngine().getDecisionTimeoutMs()) {
            status = TradeStatus.REJECTED_LATENCY;
            rejectionReason = String.format("Latency %dms > %dms timeout",
                    latencyMs, config.getEngine().getDecisionTimeoutMs());
        } else if (grossSpread.compareTo(BigDecimal.ZERO) <= 0) {
            status = TradeStatus.REJECTED_FEES;
            rejectionReason = String.format("Negative gross spread: %.2f", grossSpread);
        } else if (netProfit.compareTo(BigDecimal.ZERO) <= 0) {
            status = TradeStatus.REJECTED_FEES;
            rejectionReason = String.format("Net profit %.2f after fees %.2f",
                    netProfit, feesTotal);
        } else if (netProfit.compareTo(requiredProfit) < 0) {
            status = TradeStatus.REJECTED_FEES;
            rejectionReason = String.format("Net profit $%.2f below %.3f%% ROI target ($%.2f)",
                    netProfit, config.getEngine().getMinRoiPct(), requiredProfit);
        } else {
            status = TradeStatus.EXECUTED;
        }

        return Optional.of(new ArbitrageOpportunity(
                System.currentTimeMillis(),
                buySnapshot.exchange(),
                sellSnapshot.exchange(),
                buyVwap,
                sellVwap,
                volume,
                grossSpread,
                feesTotal,
                netProfit,
                spreadPct,
                status,
                rejectionReason,
                latencyMs
        ));
    }

    private Optional<ArbitrageOpportunity> buildRejected(
            OrderBookSnapshot buy, OrderBookSnapshot sell, long startNanos,
            TradeStatus status, String reason,
            BigDecimal grossSpread, BigDecimal fees, BigDecimal net) {
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        return Optional.of(new ArbitrageOpportunity(
                System.currentTimeMillis(),
                buy.exchange(),
                sell.exchange(),
                buy.bestAskPrice(),
                sell.bestBidPrice(),
                BigDecimal.ZERO,
                grossSpread, fees, net, BigDecimal.ZERO,
                status, reason, latencyMs
        ));
    }
}
