package com.nexustrade.api;

import java.util.List;
import java.util.Map;

/**
 * DTO for live configuration updates from the dashboard.
 * Supports engine parameters, risk parameters, and per-exchange fee overrides.
 * All fields are optional (null = no change).
 */
public record ConfigUpdateRequest(
    // Engine parameters
    Double walletExposurePct,
    Double minRoiPct,
    List<String> activeExchanges,
    Long decisionTimeoutMs,

    // Risk parameters
    Integer circuitBreakerLosses,
    Integer circuitBreakerPauseSeconds,
    Double maxBalanceDrawdownPct,
    Double rebalanceThresholdPct,

    // Per-exchange fee overrides
    Map<String, ExchangeFeeOverride> feeOverrides
) {
    /**
     * Per-exchange fee override. Any field left null will not be updated.
     */
    public record ExchangeFeeOverride(
        Double feeTaker,
        Double feeMaker,
        Double withdrawalFeeBtc
    ) {}
}
