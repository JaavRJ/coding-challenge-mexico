package com.nexustrade.api;

import java.util.List;

/**
 * DTO for live configuration updates from the dashboard.
 */
public record ConfigUpdateRequest(
    Double minProfitUsd,
    List<String> activeExchanges
) {}
