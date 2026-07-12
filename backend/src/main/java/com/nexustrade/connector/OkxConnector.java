package com.nexustrade.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexustrade.model.OrderBook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * OKX WebSocket connector for BTC/USDT order book.
 * Uses OKX WebSocket API v5:
 * Channel: books5 (top-5 depth snapshot-only feed), instId: BTC-USDT
 *
 * Protocol reference:
 *   https://www.okx.com/docs-v5/en/#order-book-trading-market-data-ws-order-book
 *
 * Subscribe message:
 *   {"op":"subscribe","args":[{"channel":"books5","instId":"BTC-USDT"}]}
 *
 * Response format (snapshot / push):
 *   {
 *     "arg":  {"channel":"books5","instId":"BTC-USDT"},
 *     "action": "snapshot",  -- or "update" for books channel; books5 always sends full state
 *     "data": [{
 *       "bids": [["price","qty","liquidated","orders"], ...],
 *       "asks": [["price","qty","liquidated","orders"], ...],
 *       "ts":   "1616998200000"
 *     }]
 *   }
 *
 * REST fallback: GET https://www.okx.com/api/v5/market/books?instId=BTC-USDT
 *   Returns the same data/bids/asks structure.
 */
@Component
public class OkxConnector extends AbstractExchangeConnector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SYMBOL = "BTC/USDT";

    public OkxConnector(
            @Value("${nexustrade.exchanges.okx.ws-url:wss://ws.okx.com:8443/ws/v5/public}") String wsUrl,
            @Value("${nexustrade.exchanges.okx.rest-url:https://www.okx.com/api/v5/market/books?instId=BTC-USDT}") String restUrl) {
        super("OKX", wsUrl, restUrl, SYMBOL);
    }

    // -------------------------------------------------------------------------
    // WebSocket lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onWebSocketOpen() {
        String sub = "{\"op\":\"subscribe\",\"args\":[{\"channel\":\"books5\",\"instId\":\"BTC-USDT\"}]}";
        sendWsMessage(sub);
        log.info("[OKX] Sent books5 subscription for BTC-USDT");
    }

    @Override
    protected void handleWebSocketMessage(String message, long ingestNanos) {
        try {
            JsonNode root = MAPPER.readTree(message);

            // -- Event messages (subscribe confirmation, error, pong) ---------
            if (root.has("event")) {
                String event = root.path("event").asText("");
                if ("subscribe".equals(event)) {
                    log.info("[OKX] Subscribed to books5 channel for BTC-USDT");
                } else if ("error".equals(event)) {
                    log.warn("[OKX] Subscription error {}: {}",
                            root.path("code").asText(), root.path("msg").asText());
                }
                return;
            }

            // -- Data push messages ------------------------------------------
            JsonNode dataArr = root.path("data");
            if (dataArr.isMissingNode() || !dataArr.isArray() || dataArr.isEmpty()) return;

            JsonNode data = dataArr.get(0);
            if (data == null) return;

            Map<BigDecimal, BigDecimal> bids = parseEntries(data.path("bids"));
            Map<BigDecimal, BigDecimal> asks = parseEntries(data.path("asks"));

            OrderBook book = orderBooks.get(SYMBOL);
            if (book == null) return;

            book.replaceBids(bids);
            book.replaceAsks(asks);

            logBestPrices(book);
            notifyUpdate(book);

        } catch (Exception e) {
            log.warn("[OKX] Parse error: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // REST fallback
    // -------------------------------------------------------------------------

    @Override
    protected void handleRestResponse(String responseBody, long ingestNanos) {
        // REST: {"code":"0","data":[{"bids":[...],"asks":[...]}]}
        try {
            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode dataArr = root.path("data");
            if (dataArr.isMissingNode() || !dataArr.isArray() || dataArr.isEmpty()) return;

            JsonNode data = dataArr.get(0);
            if (data == null) return;

            Map<BigDecimal, BigDecimal> bids = parseEntries(data.path("bids"));
            Map<BigDecimal, BigDecimal> asks = parseEntries(data.path("asks"));

            OrderBook book = orderBooks.get(SYMBOL);
            if (book == null) return;

            book.replaceBids(bids);
            book.replaceAsks(asks);

            notifyUpdate(book);
            log.debug("[OKX] REST fallback book parsed -- bids={}, asks={}", bids.size(), asks.size());

        } catch (Exception e) {
            log.warn("[OKX] REST parse error: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parses OKX level array: each entry is ["price", "qty", "liquidated", "orders"].
     * Only the first two elements (price, qty) are used.
     * Entries with zero quantity are excluded (they signal level removal on the
     * books channel; books5 always sends the current full top-5 state).
     */
    private Map<BigDecimal, BigDecimal> parseEntries(JsonNode arr) {
        Map<BigDecimal, BigDecimal> levels = new LinkedHashMap<>();
        if (!arr.isArray()) return levels;

        for (JsonNode entry : arr) {
            try {
                if (entry.size() < 2) continue;
                BigDecimal price = new BigDecimal(entry.get(0).asText());
                BigDecimal qty   = new BigDecimal(entry.get(1).asText());
                if (price.compareTo(BigDecimal.ZERO) > 0 && qty.compareTo(BigDecimal.ZERO) >= 0) {
                    levels.put(price, qty);
                }
            } catch (NumberFormatException e) {
                log.warn("[OKX] Malformed level skipped: {}", entry);
            }
        }
        return levels;
    }

    private void logBestPrices(OrderBook book) {
        book.bestBidPrice().ifPresent(bid ->
                book.bestAskPrice().ifPresent(ask ->
                        log.debug("[OKX] Bid={} Ask={}", bid.toPlainString(), ask.toPlainString())
                )
        );
    }
}
