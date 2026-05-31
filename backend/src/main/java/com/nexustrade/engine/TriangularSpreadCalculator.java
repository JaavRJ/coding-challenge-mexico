package com.nexustrade.engine;

import com.nexustrade.model.OrderBookSnapshot;
import com.nexustrade.model.TradeStatus;
import com.nexustrade.model.TriangularOpportunity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
public class TriangularSpreadCalculator {

    private final EngineConfig config;
    private final SlippageEstimator slippageEstimator;

    public TriangularSpreadCalculator(EngineConfig config, SlippageEstimator slippageEstimator) {
        this.config = config;
        this.slippageEstimator = slippageEstimator;
    }

    /**
     * Evaluates USDT -> BTC -> ETH -> USDT triangular arbitrage on a single exchange.
     * @param btcUsdt Book for BTC/USDT
     * @param ethBtc  Book for ETH/BTC
     * @param ethUsdt Book for ETH/USDT
     * @return TriangularOpportunity if detected (even if rejected)
     */
    public Optional<TriangularOpportunity> calculate(
            OrderBookSnapshot btcUsdt,
            OrderBookSnapshot ethBtc,
            OrderBookSnapshot ethUsdt,
            BigDecimal startUsdt) {

        if (btcUsdt == null || ethBtc == null || ethUsdt == null || startUsdt.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        long ingestNanos = Math.max(btcUsdt.timestampMs(), Math.max(ethBtc.timestampMs(), ethUsdt.timestampMs()));
        String exchange = btcUsdt.exchange();
        BigDecimal feeRate = config.getTakerFee(exchange); // usually 0.001 (0.1%)

        // Step 1: USDT -> BTC (Buy BTC)
        // We evaluate against the Ask of BTC/USDT. We need to find how much BTC we can buy with 1000 USDT.
        // For simplicity, we use the best ask to determine approximate volume, then use VWAP.
        BigDecimal btcUsdtBestAsk = btcUsdt.bestAskPrice();
        if (btcUsdtBestAsk == null) return Optional.empty();

        BigDecimal approxBtcVolume = startUsdt.divide(btcUsdtBestAsk, 8, RoundingMode.HALF_UP);
        Optional<BigDecimal> btcUsdtVwap = slippageEstimator.estimateBuyVwap(btcUsdt.topAsks(), approxBtcVolume);
        
        if (btcUsdtVwap.isEmpty()) {
            return Optional.of(createRejected(exchange, ingestNanos, "Insufficient liquidity for USDT->BTC", startUsdt));
        }

        BigDecimal btcAcquired = startUsdt.divide(btcUsdtVwap.get(), 8, RoundingMode.DOWN)
                .multiply(BigDecimal.ONE.subtract(feeRate)); // deduct fee in BTC
        
        BigDecimal fee1Usdt = startUsdt.multiply(feeRate); // Approx fee in USDT for logging

        // Step 2: BTC -> ETH (Buy ETH)
        // We evaluate against the Ask of ETH/BTC. How much ETH can we buy with btcAcquired?
        BigDecimal ethBtcBestAsk = ethBtc.bestAskPrice();
        if (ethBtcBestAsk == null) return Optional.empty();

        BigDecimal approxEthVolume = btcAcquired.divide(ethBtcBestAsk, 8, RoundingMode.HALF_UP);
        Optional<BigDecimal> ethBtcVwap = slippageEstimator.estimateBuyVwap(ethBtc.topAsks(), approxEthVolume);

        if (ethBtcVwap.isEmpty()) {
            return Optional.of(createRejected(exchange, ingestNanos, "Insufficient liquidity for BTC->ETH", startUsdt));
        }

        BigDecimal ethAcquired = btcAcquired.divide(ethBtcVwap.get(), 8, RoundingMode.DOWN)
                .multiply(BigDecimal.ONE.subtract(feeRate)); // deduct fee in ETH

        BigDecimal fee2Usdt = btcAcquired.multiply(btcUsdtVwap.get()).multiply(feeRate);

        // Step 3: ETH -> USDT (Sell ETH)
        // We evaluate against the Bid of ETH/USDT. We sell all ethAcquired.
        Optional<BigDecimal> ethUsdtVwap = slippageEstimator.estimateSellVwap(ethUsdt.topBids(), ethAcquired);

        if (ethUsdtVwap.isEmpty()) {
            return Optional.of(createRejected(exchange, ingestNanos, "Insufficient liquidity for ETH->USDT", startUsdt));
        }

        BigDecimal finalUsdt = ethAcquired.multiply(ethUsdtVwap.get())
                .multiply(BigDecimal.ONE.subtract(feeRate)); // deduct fee in USDT
        
        BigDecimal fee3Usdt = ethAcquired.multiply(ethUsdtVwap.get()).multiply(feeRate);

        BigDecimal totalFeesUsdt = fee1Usdt.add(fee2Usdt).add(fee3Usdt);
        BigDecimal netProfit = finalUsdt.subtract(startUsdt);
        BigDecimal spreadPct = netProfit.divide(startUsdt, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

        long latencyMs = System.currentTimeMillis() - ingestNanos;

        TradeStatus status;
        String reason = null;

        BigDecimal requiredProfit = startUsdt.multiply(BigDecimal.valueOf(config.getEngine().getMinRoiPct() / 100.0)).setScale(8, RoundingMode.HALF_UP);

        if (latencyMs > config.getEngine().getDecisionTimeoutMs()) {
            status = TradeStatus.REJECTED_LATENCY;
            reason = "Latency " + latencyMs + "ms > limit " + config.getEngine().getDecisionTimeoutMs() + "ms";
        } else if (netProfit.compareTo(requiredProfit) > 0) {
            status = TradeStatus.EXECUTED;
        } else {
            status = TradeStatus.REJECTED_FEES;
            reason = "Net profit " + netProfit.setScale(2, RoundingMode.HALF_UP) + " < " + config.getEngine().getMinRoiPct() + "% ROI target ($" + requiredProfit.setScale(2, RoundingMode.HALF_UP) + ")";
        }

        return Optional.of(new TriangularOpportunity(
                System.currentTimeMillis(), exchange, startUsdt, btcAcquired, ethAcquired, finalUsdt,
                totalFeesUsdt, netProfit, spreadPct, status, reason, latencyMs
        ));
    }

    private TriangularOpportunity createRejected(String exchange, long ingestNanos, String reason, BigDecimal startUsdt) {
        return new TriangularOpportunity(
                System.currentTimeMillis(), exchange, startUsdt, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                TradeStatus.REJECTED_LIQUIDITY, reason, System.currentTimeMillis() - ingestNanos
        );
    }
}
