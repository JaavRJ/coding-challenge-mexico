package com.nexustrade.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.nexustrade.model.OrderBook;

/**
 * Coinbase Advanced Trade WebSocket Connector for BTC-USD.
 *
 * Docs: https://docs.cdp.coinbase.com/advanced-trade/docs/ws-channels#level2-channel
 *
 * Subscribe:
 * {
 *   "type": "subscribe",
 *   "product_ids": ["BTC-USD"],
 *   "channel": "level2"
 * }
 *
 * Response (snapshot):
 * {
 *   "channel": "l2_data",
 *   "events": [{
 *     "type": "snapshot",
 *     "updates": [
 *       { "side": "bid", "price_level": "65000.00", "new_quantity": "1.5" }
 *     ]
 *   }]
 * }
 */
@Component
public class CoinbaseConnector extends AbstractExchangeConnector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public CoinbaseConnector(
            @Value("${nexustrade.exchanges.coinbase.ws-url}") String wsUrl,
            @Value("${nexustrade.exchanges.coinbase.rest-url}") String restUrl) {
        super("COINBASE", wsUrl, restUrl, "BTC/USDT");
    }

    @Override
    protected void onWebSocketOpen() {
        String subscribeMsg = """
                {
                  "type": "subscribe",
                  "product_ids": ["BTC-USD"],
                  "channel": "level2"
                }
                """;
        sendWsMessage(subscribeMsg);
        log.info("[COINBASE] Sent subscription for level2/BTC-USD");
    }

    @Override
    protected void handleWebSocketMessage(String message, long ingestNanos) {
        Optional<JsonNode> rootOpt = parseJson(message);
        if (rootOpt.isEmpty()) return;

        JsonNode root = rootOpt.get();
        String channel = root.path("channel").asText("");
        if (!"l2_data".equals(channel)) return;

        JsonNode events = root.path("events");
        if (events.isMissingNode() || !events.isArray()) return;

        OrderBook orderBook = orderBooks.get("BTC/USDT");
        if (orderBook == null) return;

        for (JsonNode event : events) {
            String eventType = event.path("type").asText("");
            JsonNode updates = event.path("updates");
            if (updates.isMissingNode() || !updates.isArray()) continue;

            if ("snapshot".equals(eventType)) {
                // For snapshot, collect all bids/asks then replace
                Map<BigDecimal, BigDecimal> newBids = new HashMap<>();
                Map<BigDecimal, BigDecimal> newAsks = new HashMap<>();

                for (JsonNode update : updates) {
                    parseUpdate(update, newBids, newAsks);
                }
                orderBook.replaceBids(newBids);
                orderBook.replaceAsks(newAsks);
                log.info("[COINBASE] Order book snapshot received ({} levels)", updates.size());

            } else if ("update".equals(eventType)) {
                // Incremental — apply each update
                for (JsonNode update : updates) {
                    applyIncrementalUpdate(orderBook, update);
                }
            }
        }

        logBestPrices(orderBook);
        notifyUpdate(orderBook);
    }

    @Override
    protected void handleRestResponse(String responseBody, long ingestNanos) {
        // Coinbase REST best_bid_ask endpoint
        // { "pricebooks": [{ "product_id": "BTC-USD", "bids": [{"price":"65000","size":"1.5"}], "asks": [...] }] }
        Optional<JsonNode> rootOpt = parseJson(responseBody);
        if (rootOpt.isEmpty()) return;

        JsonNode pricebooks = rootOpt.get().path("pricebooks");
        if (pricebooks.isMissingNode() || !pricebooks.isArray() || pricebooks.isEmpty()) return;

        JsonNode book = pricebooks.get(0);
        if (book == null) return;

        JsonNode bids = book.path("bids");
        JsonNode asks = book.path("asks");

        OrderBook orderBook = orderBooks.get("BTC/USDT");
        if (orderBook != null) {
            if (!bids.isMissingNode()) orderBook.replaceBids(parseRestSide(bids));
            if (!asks.isMissingNode()) orderBook.replaceAsks(parseRestSide(asks));
            notifyUpdate(orderBook);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing utilities
    // ─────────────────────────────────────────────────────────────────────────

    private void parseUpdate(JsonNode update, Map<BigDecimal, BigDecimal> bids, Map<BigDecimal, BigDecimal> asks) {
        try {
            String side = update.path("side").asText("");
            BigDecimal price = new BigDecimal(update.path("price_level").asText());
            BigDecimal qty = new BigDecimal(update.path("new_quantity").asText());

            if (price.compareTo(BigDecimal.ZERO) <= 0) return;

            if ("bid".equals(side)) {
                bids.put(price, qty);
            } else if ("offer".equals(side)) {
                asks.put(price, qty);
            }
        } catch (Exception e) {
            log.warn("[COINBASE] Skipping malformed snapshot update: {}", update);
        }
    }

    private void applyIncrementalUpdate(OrderBook orderBook, JsonNode update) {
        try {
            String side = update.path("side").asText("");
            BigDecimal price = new BigDecimal(update.path("price_level").asText());
            BigDecimal qty = new BigDecimal(update.path("new_quantity").asText());

            if ("bid".equals(side)) {
                orderBook.updateBid(price, qty);
            } else if ("offer".equals(side)) {
                orderBook.updateAsk(price, qty);
            }
        } catch (Exception e) {
            log.warn("[COINBASE] Skipping malformed incremental update: {}", update);
        }
    }

    private Map<BigDecimal, BigDecimal> parseRestSide(JsonNode levelsNode) {
        Map<BigDecimal, BigDecimal> levels = new HashMap<>();
        if (!levelsNode.isArray()) return levels;
        for (JsonNode level : levelsNode) {
            try {
                BigDecimal price = new BigDecimal(level.path("price").asText());
                BigDecimal size = new BigDecimal(level.path("size").asText());
                if (price.compareTo(BigDecimal.ZERO) > 0 && size.compareTo(BigDecimal.ZERO) >= 0) {
                    levels.put(price, size);
                }
            } catch (Exception e) {
                log.warn("[COINBASE] Skipping malformed REST level: {}", level);
            }
        }
        return levels;
    }

    private Optional<JsonNode> parseJson(String message) {
        try {
            return Optional.of(MAPPER.readTree(message));
        } catch (Exception e) {
            log.warn("[COINBASE] Failed to parse JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void logBestPrices(OrderBook orderBook) {
        orderBook.bestBidPrice().ifPresent(bid ->
                orderBook.bestAskPrice().ifPresent(ask ->
                        log.debug("[COINBASE] Bid={} Ask={}", bid.toPlainString(), ask.toPlainString())
                )
        );
    }
}
