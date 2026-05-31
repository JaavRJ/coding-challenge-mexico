package com.nexustrade.api;

import java.util.List;

/**
 * DTO for live configuration updates from the dashboard.
 */
public record ConfigUpdateRequest(
    Double walletExposurePct,
    Double minRoiPct,
    List<String> activeExchanges
) {}
