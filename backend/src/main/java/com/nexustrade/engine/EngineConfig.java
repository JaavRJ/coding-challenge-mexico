package com.nexustrade.engine;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Binds configuration properties from application.yml under "nexustrade".
 * 
 * Provides engine parameters (min profit, max volume, timeouts) and
 * per-exchange fee structures (maker/taker fees, withdrawal costs).
 *
 * Example YAML:
 *   nexustrade:
 *     engine:
 *       min-profit-usd: 5.0
 *     exchanges:
 *       binance:
 *         fee-taker: 0.001
 */
@Component
@ConfigurationProperties(prefix = "nexustrade")
public class EngineConfig {

    private final Engine engine = new Engine();
    private final Risk risk = new Risk();
    private final WalletConfig wallet = new WalletConfig();
    private final Map<String, ExchangeProps> exchanges = new HashMap<>();

    public Engine getEngine() { return engine; }
    public Risk getRisk() { return risk; }
    public WalletConfig getWallet() { return wallet; }
    public Map<String, ExchangeProps> getExchanges() { return exchanges; }

    public BigDecimal getTakerFee(String exchange) {
        ExchangeProps props = exchanges.get(exchange.toLowerCase());
        if (props == null) return new BigDecimal("0.001");
        return BigDecimal.valueOf(props.getFeeTaker());
    }

    public BigDecimal getWithdrawalFee(String exchange) {
        ExchangeProps props = exchanges.get(exchange.toLowerCase());
        if (props == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(props.getWithdrawalFeeBtc());
    }

    public static class Engine {
        private volatile long decisionTimeoutMs = 200;
        private volatile long evaluationIntervalMs = 50;
        
        // Dynamic Risk properties
        private volatile double walletExposurePct = 100.0;
        private volatile double minRoiPct = 0.01;

        public long getDecisionTimeoutMs() { return decisionTimeoutMs; }
        public void setDecisionTimeoutMs(long v) { this.decisionTimeoutMs = v; }
        public long getEvaluationIntervalMs() { return evaluationIntervalMs; }
        public void setEvaluationIntervalMs(long v) { this.evaluationIntervalMs = v; }
        public double getWalletExposurePct() { return walletExposurePct; }
        public void setWalletExposurePct(double v) { this.walletExposurePct = v; }
        public double getMinRoiPct() { return minRoiPct; }
        public void setMinRoiPct(double v) { this.minRoiPct = v; }
    }

    public static class Risk {
        private int circuitBreakerLosses = 3;
        private int circuitBreakerPauseSeconds = 60;
        private double maxBalanceDrawdownPct = 2.0;
        private double rebalanceThresholdPct = 40.0;

        public int getCircuitBreakerLosses() { return circuitBreakerLosses; }
        public void setCircuitBreakerLosses(int v) { this.circuitBreakerLosses = v; }
        public int getCircuitBreakerPauseSeconds() { return circuitBreakerPauseSeconds; }
        public void setCircuitBreakerPauseSeconds(int v) { this.circuitBreakerPauseSeconds = v; }
        public double getMaxBalanceDrawdownPct() { return maxBalanceDrawdownPct; }
        public void setMaxBalanceDrawdownPct(double v) { this.maxBalanceDrawdownPct = v; }
        public double getRebalanceThresholdPct() { return rebalanceThresholdPct; }
        public void setRebalanceThresholdPct(double v) { this.rebalanceThresholdPct = v; }
    }

    public static class WalletConfig {
        private double initialUsdt = 100000.0;
        private double initialBtc = 1.0;

        public double getInitialUsdt() { return initialUsdt; }
        public void setInitialUsdt(double v) { this.initialUsdt = v; }
        public double getInitialBtc() { return initialBtc; }
        public void setInitialBtc(double v) { this.initialBtc = v; }
    }

    public static class ExchangeProps {
        private String wsUrl;
        private String restUrl;
        private String apiKey = "";
        private String apiSecret = "";
        private double feeMaker;
        private double feeTaker;
        private double withdrawalFeeBtc;

        public String getWsUrl() { return wsUrl; }
        public void setWsUrl(String v) { this.wsUrl = v; }
        public String getRestUrl() { return restUrl; }
        public void setRestUrl(String v) { this.restUrl = v; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
        public double getFeeMaker() { return feeMaker; }
        public void setFeeMaker(double v) { this.feeMaker = v; }
        public double getFeeTaker() { return feeTaker; }
        public void setFeeTaker(double v) { this.feeTaker = v; }
        public double getWithdrawalFeeBtc() { return withdrawalFeeBtc; }
        public void setWithdrawalFeeBtc(double v) { this.withdrawalFeeBtc = v; }
    }
}
