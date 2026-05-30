package com.nexustrade.model;

/**
 * Status of an arbitrage opportunity evaluation.
 * 
 * EXECUTED means the trade was simulated successfully.
 * REJECTED_* variants classify why the opportunity was discarded,
 * enabling Shadow Learning analysis of missed or correctly-avoided trades.
 */
public enum TradeStatus {

    /** Trade was simulated — profitable after all costs */
    EXECUTED,

    /** Gross spread was less than combined exchange fees */
    REJECTED_FEES,

    /** Estimated slippage (VWAP impact) exceeded acceptable threshold */
    REJECTED_SLIPPAGE,

    /** Decision latency exceeded the configured timeout (default: 200ms) */
    REJECTED_LATENCY,

    /** Available volume in the order book was below the minimum operational size */
    REJECTED_LIQUIDITY,

    /** Circuit breaker is active — bot paused due to consecutive losses */
    REJECTED_CIRCUIT_BREAKER;

    public boolean isExecuted() {
        return this == EXECUTED;
    }

    public boolean isRejected() {
        return this != EXECUTED;
    }
}
