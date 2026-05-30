package com.nexustrade.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Thread-safe Order Book using ConcurrentSkipListMap for O(log n) insertions
 * and O(1) access to Best Bid (highest) and Best Ask (lowest).
 *
 * Bids are sorted DESCENDING (highest price first = best bid at top).
 * Asks are sorted ASCENDING (lowest price first = best ask at top).
 */
public class OrderBook {

    /** Number of top price levels to capture in snapshots for VWAP/slippage */
    private static final int SNAPSHOT_DEPTH = 20;

    private final String exchange;
    private final String symbol;

    // Lock object for atomic replace operations
    private final Object replaceLock = new Object();

    // Bids: highest price first → reversed order
    private final ConcurrentSkipListMap<BigDecimal, BigDecimal> bids =
            new ConcurrentSkipListMap<>(Collections.reverseOrder());

    // Asks: lowest price first → natural order
    private final ConcurrentSkipListMap<BigDecimal, BigDecimal> asks =
            new ConcurrentSkipListMap<>();

    private volatile long lastUpdatedNanos = System.nanoTime();
    private volatile long lastUpdatedEpochMs = System.currentTimeMillis();

    public OrderBook(String exchange, String symbol) {
        this.exchange = exchange;
        this.symbol = symbol;
    }

    /**
     * Updates a bid level. If volume is 0 or null, removes the level.
     * @return true if update was applied, false if input was invalid
     */
    public boolean updateBid(BigDecimal price, BigDecimal volume) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) return false;
        if (volume == null || volume.compareTo(BigDecimal.ZERO) <= 0) {
            bids.remove(price);
        } else {
            bids.put(price, volume);
        }
        touch();
        return true;
    }

    /**
     * Updates an ask level. If volume is 0 or null, removes the level.
     * @return true if update was applied, false if input was invalid
     */
    public boolean updateAsk(BigDecimal price, BigDecimal volume) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) return false;
        if (volume == null || volume.compareTo(BigDecimal.ZERO) <= 0) {
            asks.remove(price);
        } else {
            asks.put(price, volume);
        }
        touch();
        return true;
    }

    /**
     * Replaces the entire bid side atomically (snapshot update).
     * Uses synchronized block to prevent concurrent reads from seeing
     * an empty book between clear() and putAll().
     */
    public boolean replaceBids(Map<BigDecimal, BigDecimal> newBids) {
        if (newBids == null) return false;
        synchronized (replaceLock) {
            bids.clear();
            bids.putAll(newBids);
        }
        touch();
        return true;
    }

    /**
     * Replaces the entire ask side atomically (snapshot update).
     * Uses synchronized block to prevent concurrent reads from seeing
     * an empty book between clear() and putAll().
     */
    public boolean replaceAsks(Map<BigDecimal, BigDecimal> newAsks) {
        if (newAsks == null) return false;
        synchronized (replaceLock) {
            asks.clear();
            asks.putAll(newAsks);
        }
        touch();
        return true;
    }

    /** @return Best Bid price (highest buy price), empty if no bids */
    public Optional<BigDecimal> bestBidPrice() {
        try {
            return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey());
        } catch (NoSuchElementException e) {
            // Race condition: book was emptied between isEmpty() and firstKey()
            return Optional.empty();
        }
    }

    /** @return Best Ask price (lowest sell price), empty if no asks */
    public Optional<BigDecimal> bestAskPrice() {
        try {
            return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey());
        } catch (NoSuchElementException e) {
            // Race condition: book was emptied between isEmpty() and firstKey()
            return Optional.empty();
        }
    }

    /** @return Volume available at best bid, empty if no bids */
    public Optional<BigDecimal> bestBidVolume() {
        return bestBidPrice().map(bids::get);
    }

    /** @return Volume available at best ask, empty if no asks */
    public Optional<BigDecimal> bestAskVolume() {
        return bestAskPrice().map(asks::get);
    }

    /**
     * Creates an immutable snapshot for the engine to evaluate.
     * Captures up to {@link #SNAPSHOT_DEPTH} top levels per side for VWAP/slippage.
     * Handles concurrent modifications gracefully.
     */
    public Optional<OrderBookSnapshot> snapshot() {
        try {
            Optional<BigDecimal> bid = bestBidPrice();
            Optional<BigDecimal> ask = bestAskPrice();
            Optional<BigDecimal> bidVol = bestBidVolume();
            Optional<BigDecimal> askVol = bestAskVolume();

            if (bid.isEmpty() || ask.isEmpty() || bidVol.isEmpty() || askVol.isEmpty()) {
                return Optional.empty();
            }

            // Capture top N levels for VWAP slippage calculation
            NavigableMap<BigDecimal, BigDecimal> topBids = takeTopLevels(bids, SNAPSHOT_DEPTH);
            NavigableMap<BigDecimal, BigDecimal> topAsks = takeTopLevels(asks, SNAPSHOT_DEPTH);

            return Optional.of(new OrderBookSnapshot(
                    exchange,
                    symbol,
                    bid.get(),
                    bidVol.get(),
                    ask.get(),
                    askVol.get(),
                    lastUpdatedEpochMs,
                    Collections.unmodifiableNavigableMap(topBids),
                    Collections.unmodifiableNavigableMap(topAsks)
            ));
        } catch (NoSuchElementException e) {
            // Race condition: book changed during snapshot construction
            return Optional.empty();
        }
    }

    /**
     * Takes the top N levels from a sorted map, creating a defensive copy.
     * Safe against concurrent modification.
     */
    private NavigableMap<BigDecimal, BigDecimal> takeTopLevels(
            ConcurrentSkipListMap<BigDecimal, BigDecimal> source, int maxLevels) {
        NavigableMap<BigDecimal, BigDecimal> result = new TreeMap<>(source.comparator());
        int count = 0;
        for (Map.Entry<BigDecimal, BigDecimal> entry : source.entrySet()) {
            if (count >= maxLevels) break;
            result.put(entry.getKey(), entry.getValue());
            count++;
        }
        return result;
    }

    public long stalenessMs() {
        return System.currentTimeMillis() - lastUpdatedEpochMs;
    }

    public boolean isStale(long thresholdMs) {
        return stalenessMs() > thresholdMs;
    }

    public void clear() {
        synchronized (replaceLock) {
            bids.clear();
            asks.clear();
        }
    }

    private void touch() {
        this.lastUpdatedNanos = System.nanoTime();
        this.lastUpdatedEpochMs = System.currentTimeMillis();
    }

    public String getExchange() { return exchange; }
    public String getSymbol() { return symbol; }
    public long getLastUpdatedEpochMs() { return lastUpdatedEpochMs; }

    @Override
    public String toString() {
        return String.format("[%s] BestBid=%s BestAsk=%s staleness=%dms",
                exchange,
                bestBidPrice().map(BigDecimal::toPlainString).orElse("N/A"),
                bestAskPrice().map(BigDecimal::toPlainString).orElse("N/A"),
                stalenessMs());
    }
}
