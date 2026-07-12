package com.nexustrade.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight anomaly detection using rolling Z-score per arbitrage route.
 * No external ML dependencies needed.
 */
@Service
public class AnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetector.class);
    private static final int WINDOW_SIZE = 100;

    public enum AnomalyLevel { NORMAL, ELEVATED, ANOMALOUS, EXTREME }

    // Map: routeKey -> rolling spread values
    private final Map<String, Deque<Double>> spreadWindows = new ConcurrentHashMap<>();

    /**
     * Analyze a spread for a given route (e.g. "BINANCE->KRAKEN").
     * Returns the anomaly level based on Z-score.
     */
    public AnomalyResult analyze(String buyExchange, String sellExchange, double spreadPct) {
        String key = buyExchange + "->" + sellExchange;
        Deque<Double> window = spreadWindows.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (window) {
            window.addLast(spreadPct);
            if (window.size() > WINDOW_SIZE) window.pollFirst();

            if (window.size() < 10) {
                // Not enough data yet
                return new AnomalyResult(AnomalyLevel.NORMAL, 0.0, spreadPct);
            }

            double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = window.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0);
            double stdDev = Math.sqrt(variance);

            double zScore = stdDev > 0 ? Math.abs((spreadPct - mean) / stdDev) : 0.0;

            AnomalyLevel level;
            if (zScore < 1.5) level = AnomalyLevel.NORMAL;
            else if (zScore < 2.0) level = AnomalyLevel.ELEVATED;
            else if (zScore < 3.0) level = AnomalyLevel.ANOMALOUS;
            else level = AnomalyLevel.EXTREME;

            if (level == AnomalyLevel.ANOMALOUS || level == AnomalyLevel.EXTREME) {
                log.info("[ANOMALY] Route {} | spread={:.4f}% | z={:.2f} | level={}",
                        key, String.format("%.4f", spreadPct), String.format("%.2f", zScore), level);
            }

            return new AnomalyResult(level, zScore, mean);
        }
    }

    public record AnomalyResult(AnomalyLevel level, double zScore, double meanSpread) {
        public boolean isAnomaly() { return level == AnomalyLevel.ANOMALOUS || level == AnomalyLevel.EXTREME; }
    }

    /** Returns a snapshot of current window stats for all routes */
    public Map<String, Map<String, Object>> getStats() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        spreadWindows.forEach((route, window) -> {
            synchronized (window) {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("sampleCount", window.size());
                if (window.size() >= 2) {
                    double mean = window.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double variance = window.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0);
                    stats.put("meanSpreadPct", mean);
                    stats.put("stdDevPct", Math.sqrt(variance));
                }
                result.put(route, stats);
            }
        });
        return result;
    }
}
