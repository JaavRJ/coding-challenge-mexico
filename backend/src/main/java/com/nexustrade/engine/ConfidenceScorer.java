package com.nexustrade.engine;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Lightweight rule-based confidence scorer for arbitrage opportunities.
 * Outputs a score 0-100. Higher = more confident in the opportunity.
 */
@Service
public class ConfidenceScorer {

    /**
     * Score an arbitrage opportunity based on multiple features.
     *
     * @param spreadPct         Actual spread percentage
     * @param minRoiPct         Configured minimum ROI threshold
     * @param volumeUsdt        Trade volume in USDT
     * @param bidDepthLevels    Available bid depth levels in the order book
     * @param askDepthLevels    Available ask depth levels in the order book
     * @param decisionLatencyMs How long the decision took (lower = fresher data)
     * @param anomalyLevel      Result from AnomalyDetector
     * @return Score 0-100
     */
    public int score(double spreadPct, double minRoiPct, double volumeUsdt,
                     int bidDepthLevels, int askDepthLevels,
                     long decisionLatencyMs, AnomalyDetector.AnomalyLevel anomalyLevel) {
        int score = 0;

        // 1. Spread quality (30 pts max)
        if (spreadPct >= minRoiPct * 3) score += 30;
        else if (spreadPct >= minRoiPct * 2) score += 20;
        else if (spreadPct >= minRoiPct * 1.5) score += 10;
        else score += 5;

        // 2. Volume available (20 pts max)
        if (volumeUsdt >= 5000) score += 20;
        else if (volumeUsdt >= 2000) score += 15;
        else if (volumeUsdt >= 1000) score += 10;
        else score += 5;

        // 3. Order book depth (15 pts max)
        int minDepth = Math.min(bidDepthLevels, askDepthLevels);
        if (minDepth >= 15) score += 15;
        else if (minDepth >= 10) score += 10;
        else if (minDepth >= 5) score += 5;

        // 4. Decision latency — fresher = better (15 pts max)
        if (decisionLatencyMs < 50) score += 15;
        else if (decisionLatencyMs < 100) score += 10;
        else if (decisionLatencyMs < 200) score += 5;

        // 5. Time of day — high liquidity hours 14:00-22:00 UTC (10 pts)
        int hourUtc = Instant.now().atZone(ZoneOffset.UTC).getHour();
        if (hourUtc >= 14 && hourUtc <= 22) score += 10;
        else if (hourUtc >= 8 && hourUtc < 14) score += 5;

        // 6. Anomaly boost/penalty (10 pts)
        switch (anomalyLevel) {
            case NORMAL    -> score += 10;
            case ELEVATED  -> score += 7;
            case ANOMALOUS -> score += 3; // suspicious — might be stale data
            case EXTREME   -> score -= 5; // very suspicious
        }

        return Math.max(0, Math.min(100, score));
    }

    /** Simple overload without anomaly level */
    public int score(double spreadPct, double minRoiPct, double volumeUsdt,
                     int bidDepthLevels, int askDepthLevels, long decisionLatencyMs) {
        return score(spreadPct, minRoiPct, volumeUsdt, bidDepthLevels, askDepthLevels,
                decisionLatencyMs, AnomalyDetector.AnomalyLevel.NORMAL);
    }
}
