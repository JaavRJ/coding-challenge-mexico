package com.nexustrade.connector;

/**
 * State machine for exchange connectors.
 *
 * Transitions:
 *   INITIALIZING → CONNECTING → LIVE
 *   LIVE → RECONNECTING (on WS failure)
 *   RECONNECTING → FALLBACK_REST (after 5 consecutive failures)
 *   FALLBACK_REST → CONNECTING (on scheduled reconnect attempt)
 *   Any → DEAD (on unrecoverable error)
 */
public enum ConnectorState {
    INITIALIZING,
    CONNECTING,
    LIVE,
    RECONNECTING,
    FALLBACK_REST,
    DEAD;

    public boolean isHealthy() {
        return this == LIVE || this == FALLBACK_REST;
    }

    public boolean isWebSocketActive() {
        return this == CONNECTING || this == LIVE;
    }
}
