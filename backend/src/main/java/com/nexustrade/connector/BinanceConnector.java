package com.nexustrade.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Binance WebSocket Connector for BTC/USDT Order Book.
 *
 * Uses the partial book depth stream: btcusdt@depth5@100ms
 * Format: https://binance-docs.github.io/apidocs/spot/en/#partial-book-depth-streams
 *
 * Message format:
 * {
 *   "lastUpdateId": 160,
 *   "bids": [["0.0024", "10"]],   // [price, quantity]
 *   "asks": [["0.0026", "100"]]
 * }
 */
@Component
public class BinanceConnector extends AbstractExchangeConnector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public BinanceConnector(
            @Value("${nexustrade.exchanges.binance.ws-url}") String wsUrl,
            @Value("${nexustrade.exchanges.binance.rest-url}") String restUrl) {
        super("BINANCE", wsUrl, restUrl);
    }

    @Override
    protected void onWebSocketOpen() {
        // Binance depth stream subscribes automatically via URL — no message needed
        log.info("[BINANCE] Subscribed to btcusdt@depth5@100ms");
    }

    @Override
    protected void handleWebSocketMessage(String message, long ingestNanos) {
        Optional<JsonNode> rootOpt = parseJson(message);
        if (rootOpt.isEmpty()) return;

        JsonNode root = rootOpt.get();

        JsonNode bidsNode = root.path("bids");
        JsonNode asksNode = root.path("asks");

        if (bidsNode.isMissingNode() || asksNode.isMissingNode()) {
            log.warn("[BINANCE] Missing bids/asks in message, skipping.");
            return;
        }

        // Process bids — replace snapshot
        orderBook.replaceBids(parseOrderBookSide(bidsNode));

        // Process asks — replace snapshot
        orderBook.replaceAsks(parseOrderBookSide(asksNode));

        logBestPrices();
    }

    @Override
    protected void handleRestResponse(String responseBody, long ingestNanos) {
        // REST response has same format as WS for this endpoint
        handleWebSocketMessage(responseBody, ingestNanos);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing utilities
    // ─────────────────────────────────────────────────────────────────────────

    private java.util.Map<BigDecimal, BigDecimal> parseOrderBookSide(JsonNode levelsNode) {
        var levels = new java.util.HashMap<BigDecimal, BigDecimal>();
        if (levelsNode == null || !levelsNode.isArray()) return levels;

        for (JsonNode level : levelsNode) {
            try {
                if (level.size() < 2) continue;
                BigDecimal price = new BigDecimal(level.get(0).asText());
                BigDecimal volume = new BigDecimal(level.get(1).asText());
                if (price.compareTo(BigDecimal.ZERO) > 0 && volume.compareTo(BigDecimal.ZERO) >= 0) {
                    levels.put(price, volume);
                }
            } catch (NumberFormatException e) {
                log.warn("[BINANCE] Skipping malformed level: {}", level);
            }
        }
        return levels;
    }

    private Optional<JsonNode> parseJson(String message) {
        try {
            return Optional.of(MAPPER.readTree(message));
        } catch (Exception e) {
            log.warn("[BINANCE] Failed to parse JSON: {} | Raw: {}", e.getMessage(),
                    message.length() > 200 ? message.substring(0, 200) + "..." : message);
            return Optional.empty();
        }
    }

    private void logBestPrices() {
        orderBook.bestBidPrice().ifPresent(bid ->
                orderBook.bestAskPrice().ifPresent(ask ->
                        log.debug("[BINANCE] Bid={} Ask={} Spread={}",
                                bid.toPlainString(),
                                ask.toPlainString(),
                                ask.subtract(bid).toPlainString())
                )
        );
    }
}
