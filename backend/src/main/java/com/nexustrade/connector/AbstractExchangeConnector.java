package com.nexustrade.connector;

import com.nexustrade.model.OrderBook;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Base class for all exchange WebSocket connectors.
 * Implements:
 *  - Exponential backoff reconnection (1s → 2s → 4s → 8s → 16s)
 *  - REST fallback after 5 consecutive WS failures
 *  - Scheduled WS reconnection every 10s from REST fallback
 *  - Strict exception isolation (no connector failure crashes the JVM)
 */
public abstract class AbstractExchangeConnector {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final int MAX_WS_FAILURES_BEFORE_FALLBACK = 5;
    private static final long REST_POLL_INTERVAL_MS = 500;
    private static final long WS_RECONNECT_FROM_REST_INTERVAL_S = 10;

    protected final String exchangeName;
    protected final String wsUrl;
    protected final String restUrl;
    protected final OrderBook orderBook;

    private final AtomicReference<ConnectorState> state =
            new AtomicReference<>(ConnectorState.INITIALIZING);
    private final AtomicInteger consecutiveWsFailures = new AtomicInteger(0);

    private WebSocketClient wsClient;
    private final ScheduledExecutorService scheduler;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // Callback when order book updates (notifies the engine)
    private Consumer<OrderBook> onUpdateCallback;

    protected AbstractExchangeConnector(String exchangeName, String wsUrl, String restUrl) {
        this.exchangeName = exchangeName;
        this.wsUrl = wsUrl;
        this.restUrl = restUrl;
        this.orderBook = new OrderBook(exchangeName, "BTC/USD");
        // Initialize scheduler here (not as field initializer) because
        // the lambda captures exchangeName which must be assigned first.
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, exchangeName + "-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public void setOnUpdateCallback(Consumer<OrderBook> callback) {
        this.onUpdateCallback = callback;
    }

    public void start() {
        log.info("[{}] Starting connector...", exchangeName);
        setState(ConnectorState.CONNECTING);
        connectWebSocket(0);
    }

    public void stop() {
        log.info("[{}] Stopping connector...", exchangeName);
        scheduler.shutdownNow();
        if (wsClient != null) {
            try { wsClient.closeBlocking(); } catch (Exception e) { /* ignore */ }
        }
        setState(ConnectorState.DEAD);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WebSocket Connection Management
    // ─────────────────────────────────────────────────────────────────────────

    private void connectWebSocket(int attemptNumber) {
        try {
            URI uri = URI.create(wsUrl);
            wsClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("[{}] ✅ WebSocket connected (attempt #{})", exchangeName, attemptNumber + 1);
                    consecutiveWsFailures.set(0);
                    setState(ConnectorState.LIVE);
                    onWebSocketOpen();
                }

                @Override
                public void onMessage(String message) {
                    try {
                        long ingestNanos = System.nanoTime();
                        handleWebSocketMessage(message, ingestNanos);
                        notifyUpdate();
                    } catch (Exception e) {
                        // STRICT: never let a bad message crash the connector
                        log.warn("[{}] Discarded malformed WS message: {}", exchangeName, e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("[{}] WebSocket closed. Code={} Reason='{}' Remote={}",
                            exchangeName, code, reason, remote);
                    handleWsFailure("Connection closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    log.warn("[{}] WebSocket error: {}", exchangeName, ex.getMessage());
                    handleWsFailure("WS error: " + ex.getMessage());
                }
            };
            wsClient.setConnectionLostTimeout(30);
            wsClient.connectBlocking(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[{}] Failed to establish WebSocket: {}", exchangeName, e.getMessage());
            handleWsFailure("Connection exception: " + e.getMessage());
        }
    }

    private void handleWsFailure(String reason) {
        int failures = consecutiveWsFailures.incrementAndGet();
        log.warn("[{}] WS failure #{}/{}: {}", exchangeName, failures, MAX_WS_FAILURES_BEFORE_FALLBACK, reason);

        if (failures >= MAX_WS_FAILURES_BEFORE_FALLBACK) {
            log.warn("[{}] ⚠️  Switching to REST fallback after {} consecutive WS failures",
                    exchangeName, failures);
            switchToRestFallback();
        } else {
            setState(ConnectorState.RECONNECTING);
            long delaySeconds = (long) Math.pow(2, Math.min(failures - 1, 4)); // max 16s
            log.info("[{}] Reconnecting in {}s (attempt #{})...", exchangeName, delaySeconds, failures + 1);
            scheduler.schedule(() -> connectWebSocket(failures), delaySeconds, TimeUnit.SECONDS);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REST Fallback
    // ─────────────────────────────────────────────────────────────────────────

    private void switchToRestFallback() {
        setState(ConnectorState.FALLBACK_REST);

        // Poll REST every 500ms
        scheduler.scheduleAtFixedRate(this::pollRest, 0, REST_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Try to reconnect WS every 10s
        scheduler.scheduleAtFixedRate(() -> {
            if (getState() == ConnectorState.FALLBACK_REST) {
                log.info("[{}] Attempting WS reconnection from REST fallback...", exchangeName);
                consecutiveWsFailures.set(0);
                setState(ConnectorState.CONNECTING);
                connectWebSocket(0);
            }
        }, WS_RECONNECT_FROM_REST_INTERVAL_S, WS_RECONNECT_FROM_REST_INTERVAL_S, TimeUnit.SECONDS);
    }

    private void pollRest() {
        if (getState() != ConnectorState.FALLBACK_REST) return;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(restUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                long ingestNanos = System.nanoTime();
                handleRestResponse(response.body(), ingestNanos);
                notifyUpdate();
            } else {
                log.warn("[{}] REST poll returned HTTP {}", exchangeName, response.statusCode());
            }
        } catch (Exception e) {
            log.warn("[{}] REST poll failed: {}", exchangeName, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Abstract methods — exchange-specific parsing
    // ─────────────────────────────────────────────────────────────────────────

    /** Called after WS connects — send subscription messages here */
    protected abstract void onWebSocketOpen();

    /** Parse an incoming WS message and update the orderBook */
    protected abstract void handleWebSocketMessage(String message, long ingestNanos);

    /** Parse a REST response and update the orderBook */
    protected abstract void handleRestResponse(String responseBody, long ingestNanos);

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    protected void sendWsMessage(String message) {
        try {
            if (wsClient != null && wsClient.isOpen()) {
                wsClient.send(message);
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to send WS message: {}", exchangeName, e.getMessage());
        }
    }

    private void notifyUpdate() {
        if (onUpdateCallback != null) {
            try {
                onUpdateCallback.accept(orderBook);
            } catch (Exception e) {
                log.warn("[{}] Update callback threw exception: {}", exchangeName, e.getMessage());
            }
        }
    }

    private void setState(ConnectorState newState) {
        ConnectorState old = state.getAndSet(newState);
        if (old != newState) {
            log.debug("[{}] State: {} → {}", exchangeName, old, newState);
        }
    }

    public ConnectorState getState() { return state.get(); }
    public OrderBook getOrderBook() { return orderBook; }
    public String getExchangeName() { return exchangeName; }
    public int getConsecutiveFailures() { return consecutiveWsFailures.get(); }
}
