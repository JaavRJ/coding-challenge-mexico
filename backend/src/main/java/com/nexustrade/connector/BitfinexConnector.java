package com.nexustrade.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexustrade.model.OrderBook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Bitfinex WebSocket connector for BTC/USDT order book.
 * Uses Bitfinex WebSocket API v2:
 * Channel: book, symbol: tBTCUSD, precision: P0, len: 25
 *
 * Protocol reference:
 *   https://docs.bitfinex.com/reference/ws-public-books
 *
 * Snapshot: [chanId, [[price, count, amount], ...]]
 * Update:   [chanId, [price, count, amount]]
 *   - count > 0, amount > 0  => bid level
 *   - count > 0, amount < 0  => ask level
 *   - count = 0              => remove price level
 *
 * REST fallback: GET https://api-pub.bitfinex.com/v2/book/tBTCUSD/P0
 *   Returns [[price, count, amount], ...]
 */
@Component
public class BitfinexConnector extends AbstractExchangeConnector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SYMBOL = "BTC/USDT";

    // Local mirrors used to rebuild the OrderBook on every update
    private final ConcurrentSkipListMap<BigDecimal, BigDecimal> bids =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    private final ConcurrentSkipListMap<BigDecimal, BigDecimal> asks =
            new ConcurrentSkipListMap<>();

    private volatile boolean snapshotReceived = false;

    public BitfinexConnector(
            @Value("${nexustrade.exchanges.bitfinex.ws-url:wss://api-pub.bitfinex.com/ws/2}") String wsUrl,
            @Value("${nexustrade.exchanges.bitfinex.rest-url:https://api-pub.bitfinex.com/v2/book/tBTCUSD/P0}") String restUrl) {
        super("BITFINEX", wsUrl, restUrl, SYMBOL);
    }

    // -------------------------------------------------------------------------
    // WebSocket lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onWebSocketOpen() {
        snapshotReceived = false;
        bids.clear();
        asks.clear();
        String sub = "{\"event\":\"subscribe\",\"channel\":\"book\",\"symbol\":\"tBTCUSD\","
                + "\"prec\":\"P0\",\"freq\":\"F0\",\"len\":\"25\"}";
        sendWsMessage(sub);
        log.info("[BITFINEX] Sent book subscription for tBTCUSD");
    }

    @Override
    protected void handleWebSocketMessage(String message, long ingestNanos) {
        try {
            JsonNode root = MAPPER.readTree(message);

            // -- Object messages: events (info, subscribed, error, pong) ------
            if (root.isObject()) {
                String event = root.path("event").asText("");
                if ("subscribed".equals(event)) {
                    log.info("[BITFINEX] Subscribed to book channel, chanId={}",
                            root.path("chanId").asInt(-1));
                } else if ("error".equals(event)) {
                    log.warn("[BITFINEX] Subscription error {}: {}",
                            root.path("code").asInt(), root.path("msg").asText());
                }
                // info, pong, etc. -- ignore
                return;
            }

            // -- Array messages: [chanId, data] --------------------------------
            if (!root.isArray() || root.size() < 2) return;
            JsonNode payload = root.get(1);

            // Heartbeat
            if (payload.isTextual() && "hb".equals(payload.asText())) return;

            if (!snapshotReceived) {
                // Snapshot: payload is array of [price, count, amount] arrays
                bids.clear();
                asks.clear();
                for (JsonNode entry : payload) {
                    applyEntry(entry);
                }
                snapshotReceived = true;
                log.info("[BITFINEX] Book snapshot received -- bids={}, asks={}", bids.size(), asks.size());
            } else {
                // Delta update: payload is a single [price, count, amount]
                applyEntry(payload);
            }

            pushOrderBook();

        } catch (Exception e) {
            log.warn("[BITFINEX] Parse error: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // REST fallback
    // -------------------------------------------------------------------------

    @Override
    protected void handleRestResponse(String responseBody, long ingestNanos) {
        // REST returns [[price, count, amount], ...] -- same layout as WS snapshot
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            if (!root.isArray()) return;

            bids.clear();
            asks.clear();
            for (JsonNode entry : root) {
                applyEntry(entry);
            }

            pushOrderBook();
            log.debug("[BITFINEX] REST fallback book parsed -- bids={}, asks={}", bids.size(), asks.size());

        } catch (Exception e) {
            log.warn("[BITFINEX] REST parse error: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Applies a single [price, count, amount] entry to the local bid/ask maps.
     * Rules per Bitfinex protocol:
     *   count > 0, amount > 0  => bid
     *   count > 0, amount < 0  => ask  (stored as positive quantity)
     *   count = 0              => remove price level from both sides
     */
    private void applyEntry(JsonNode entry) {
        if (!entry.isArray() || entry.size() < 3) return;

        BigDecimal price  = entry.get(0).decimalValue();
        int        count  = entry.get(1).asInt();
        BigDecimal amount = entry.get(2).decimalValue();

        if (count > 0) {
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                bids.put(price, amount);
                asks.remove(price);
            } else {
                asks.put(price, amount.negate());
                bids.remove(price);
            }
        } else {
            // count == 0 => remove level
            bids.remove(price);
            asks.remove(price);
        }
    }

    /**
     * Rebuilds the OrderBook from the local maps and fires the update callback.
     */
    private void pushOrderBook() {
        OrderBook book = orderBooks.get(SYMBOL);
        if (book == null) return;

        Map<BigDecimal, BigDecimal> bidSnapshot = new LinkedHashMap<>();
        bids.entrySet().stream().limit(20)
                .forEach(e -> bidSnapshot.put(e.getKey(), e.getValue()));

        Map<BigDecimal, BigDecimal> askSnapshot = new LinkedHashMap<>();
        asks.entrySet().stream().limit(20)
                .forEach(e -> askSnapshot.put(e.getKey(), e.getValue()));

        book.replaceBids(bidSnapshot);
        book.replaceAsks(askSnapshot);

        logBestPrices(book);
        notifyUpdate(book);
    }

    private void logBestPrices(OrderBook book) {
        book.bestBidPrice().ifPresent(bid ->
                book.bestAskPrice().ifPresent(ask ->
                        log.debug("[BITFINEX] Bid={} Ask={}", bid.toPlainString(), ask.toPlainString())
                )
        );
    }
}
