package com.nexustrade.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

import com.nexustrade.model.OrderBook;

@Component
public class OkxConnector extends AbstractExchangeConnector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final BigDecimal priceOffset;

    public OkxConnector(
            @Value("${nexustrade.exchanges.binance.ws-url}") String wsUrl,
            @Value("${nexustrade.exchanges.binance.rest-url}") String restUrl) {
        // Using binance url for mock data generation
        super("OKX", wsUrl, restUrl, "BTC/USDT");
        this.orderBooks.put("ETH/BTC", new com.nexustrade.model.OrderBook("OKX", "ETH/BTC"));
        this.orderBooks.put("ETH/USDT", new com.nexustrade.model.OrderBook("OKX", "ETH/USDT"));
        this.priceOffset = new BigDecimal("-1.10"); // Synthetic spread
    }

    @Override
    protected void onWebSocketOpen() {
        log.info("[OKX] Subscribed to mock streams via URL: {}", wsUrl);
    }

    @Override
    protected void handleWebSocketMessage(String message, long ingestNanos) {
        Optional<JsonNode> rootOpt = parseJson(message);
        if (rootOpt.isEmpty()) return;

        JsonNode root = rootOpt.get();
        JsonNode dataNode = root.has("data") ? root.get("data") : root;
        String streamName = root.has("stream") ? root.get("stream").asText() : "btcusdt";
        
        String symbol = "BTC/USDT";
        if (streamName.startsWith("ethbtc")) symbol = "ETH/BTC";
        else if (streamName.startsWith("ethusdt")) symbol = "ETH/USDT";

        OrderBook orderBook = orderBooks.get(symbol);
        if (orderBook == null) return;

        JsonNode bidsNode = dataNode.path("bids");
        JsonNode asksNode = dataNode.path("asks");

        if (bidsNode.isMissingNode() || asksNode.isMissingNode()) return;

        orderBook.replaceBids(parseOrderBookSide(bidsNode, symbol.equals("BTC/USDT") ? priceOffset : new BigDecimal("-0.0001")));
        orderBook.replaceAsks(parseOrderBookSide(asksNode, symbol.equals("BTC/USDT") ? priceOffset : new BigDecimal("-0.0001")));

        notifyUpdate(orderBook);
    }

    @Override
    protected void handleRestResponse(String responseBody, long ingestNanos) {
        handleWebSocketMessage(responseBody, ingestNanos);
    }

    private java.util.Map<BigDecimal, BigDecimal> parseOrderBookSide(JsonNode levelsNode, BigDecimal offset) {
        var levels = new java.util.HashMap<BigDecimal, BigDecimal>();
        if (levelsNode == null || !levelsNode.isArray()) return levels;

        for (JsonNode level : levelsNode) {
            try {
                if (level.size() < 2) continue;
                BigDecimal price = new BigDecimal(level.get(0).asText()).add(offset);
                BigDecimal volume = new BigDecimal(level.get(1).asText());
                if (price.compareTo(BigDecimal.ZERO) > 0 && volume.compareTo(BigDecimal.ZERO) >= 0) {
                    levels.put(price, volume);
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return levels;
    }

    private Optional<JsonNode> parseJson(String message) {
        try {
            return Optional.of(MAPPER.readTree(message));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
