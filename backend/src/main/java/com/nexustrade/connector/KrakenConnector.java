package com.nexustrade.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Kraken WebSocket v2 Connector for XBT/USD Order Book.
 *
 * Protocol: https://docs.kraken.com/api/docs/websocket-v2/book
 *
 * Subscribe message:
 * {
 *   "method": "subscribe",
 *   "params": {
 *     "channel": "book",
 *     "symbol": ["BTC/USD"],
 *     "depth": 10
 *   }
 * }
 *
 * Response format (snapshot):
 * {
 *   "channel": "book",
 *   "type": "snapshot",
 *   "data": [{
 *     "symbol": "BTC/USD",
 *     "bids": [{ "price": 65000.0, "qty": 1.5 }],
 *     "asks": [{ "price": 65001.0, "qty": 2.0 }]
 *   }]
 * }
 */
@Component
public class KrakenConnector extends AbstractExchangeConnector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public KrakenConnector(
            @Value("${nexustrade.exchanges.kraken.ws-url}") String wsUrl,
            @Value("${nexustrade.exchanges.kraken.rest-url}") String restUrl) {
        super("KRAKEN", wsUrl, restUrl);
    }

    @Override
    protected void onWebSocketOpen() {
        String subscribeMsg = """
                {
                  "method": "subscribe",
                  "params": {
                    "channel": "book",
                    "symbol": ["BTC/USD"],
                    "depth": 10
                  }
                }
                """;
        sendWsMessage(subscribeMsg);
        log.info("[KRAKEN] Sent subscription for book/BTC/USD depth=10");
    }

    @Override
    protected void handleWebSocketMessage(String message, long ingestNanos) {
        Optional<JsonNode> rootOpt = parseJson(message);
        if (rootOpt.isEmpty()) return;

        JsonNode root = rootOpt.get();

        // Ignore non-book messages (heartbeat, status, subscriptions)
        String channel = root.path("channel").asText("");
        if (!"book".equals(channel)) return;

        String type = root.path("type").asText("");
        JsonNode dataArray = root.path("data");
        if (dataArray.isMissingNode() || !dataArray.isArray() || dataArray.isEmpty()) return;

        JsonNode data = dataArray.get(0);
        if (data == null) return;

        JsonNode bidsNode = data.path("bids");
        JsonNode asksNode = data.path("asks");

        if ("snapshot".equals(type)) {
            // Full snapshot — replace entire order book
            if (!bidsNode.isMissingNode()) orderBook.replaceBids(parseLevels(bidsNode));
            if (!asksNode.isMissingNode()) orderBook.replaceAsks(parseLevels(asksNode));
            log.info("[KRAKEN] Order book snapshot received");
        } else if ("update".equals(type)) {
            // Incremental update
            if (!bidsNode.isMissingNode()) applyUpdates(bidsNode, true);
            if (!asksNode.isMissingNode()) applyUpdates(asksNode, false);
        }

        logBestPrices();
    }

    @Override
    protected void handleRestResponse(String responseBody, long ingestNanos) {
        // Kraken REST: { "result": { "XXBTZUSD": { "bids": [[price, vol, ts]], "asks": [...] } } }
        Optional<JsonNode> rootOpt = parseJson(responseBody);
        if (rootOpt.isEmpty()) return;

        JsonNode root = rootOpt.get();
        JsonNode result = root.path("result");
        if (result.isMissingNode()) return;

        // The key is dynamic (XXBTZUSD), iterate to find it
        result.fields().forEachRemaining(entry -> {
            JsonNode bookData = entry.getValue();
            JsonNode bids = bookData.path("bids");
            JsonNode asks = bookData.path("asks");

            if (!bids.isMissingNode()) orderBook.replaceBids(parseLegacyLevels(bids));
            if (!asks.isMissingNode()) orderBook.replaceAsks(parseLegacyLevels(asks));
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing utilities (WS v2 format: { "price": 65000, "qty": 1.5 })
    // ─────────────────────────────────────────────────────────────────────────

    private Map<BigDecimal, BigDecimal> parseLevels(JsonNode levelsNode) {
        Map<BigDecimal, BigDecimal> levels = new HashMap<>();
        if (!levelsNode.isArray()) return levels;

        for (JsonNode level : levelsNode) {
            try {
                JsonNode priceNode = level.path("price");
                JsonNode qtyNode = level.path("qty");
                if (priceNode.isMissingNode() || qtyNode.isMissingNode()) continue;

                BigDecimal price = new BigDecimal(priceNode.asText());
                BigDecimal qty = new BigDecimal(qtyNode.asText());
                if (price.compareTo(BigDecimal.ZERO) > 0 && qty.compareTo(BigDecimal.ZERO) >= 0) {
                    levels.put(price, qty);
                }
            } catch (NumberFormatException e) {
                log.warn("[KRAKEN] Malformed level: {}", level);
            }
        }
        return levels;
    }

    private void applyUpdates(JsonNode levelsNode, boolean isBid) {
        if (!levelsNode.isArray()) return;
        for (JsonNode level : levelsNode) {
            try {
                BigDecimal price = new BigDecimal(level.path("price").asText());
                BigDecimal qty = new BigDecimal(level.path("qty").asText());
                if (isBid) orderBook.updateBid(price, qty);
                else orderBook.updateAsk(price, qty);
            } catch (Exception e) {
                log.warn("[KRAKEN] Skipping malformed update: {}", level);
            }
        }
    }

    /** Kraken REST legacy format: [[price, volume, timestamp], ...] */
    private Map<BigDecimal, BigDecimal> parseLegacyLevels(JsonNode levelsNode) {
        Map<BigDecimal, BigDecimal> levels = new HashMap<>();
        if (!levelsNode.isArray()) return levels;
        for (JsonNode level : levelsNode) {
            try {
                if (level.size() < 2) continue;
                BigDecimal price = new BigDecimal(level.get(0).asText());
                BigDecimal volume = new BigDecimal(level.get(1).asText());
                if (price.compareTo(BigDecimal.ZERO) > 0 && volume.compareTo(BigDecimal.ZERO) >= 0) {
                    levels.put(price, volume);
                }
            } catch (Exception e) {
                log.warn("[KRAKEN] Skipping malformed REST level: {}", level);
            }
        }
        return levels;
    }

    private Optional<JsonNode> parseJson(String message) {
        try {
            return Optional.of(MAPPER.readTree(message));
        } catch (Exception e) {
            log.warn("[KRAKEN] Failed to parse JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void logBestPrices() {
        orderBook.bestBidPrice().ifPresent(bid ->
                orderBook.bestAskPrice().ifPresent(ask ->
                        log.debug("[KRAKEN] Bid={} Ask={}", bid.toPlainString(), ask.toPlainString())
                )
        );
    }
}
