package com.nexustrade.connector;

import com.nexustrade.model.OrderBook;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Manages all exchange connectors.
 * Starts each in its own Virtual Thread (Java 21 Project Loom).
 * Provides a unified callback interface for the ArbitrageEngine.
 */
@Service
public class ConnectorRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConnectorRegistry.class);

    private final List<AbstractExchangeConnector> connectors;
    private Consumer<OrderBook> globalUpdateCallback;

    // Executor service — compatible with Java 17+
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ConnectorRegistry(
            BinanceConnector binance,
            KrakenConnector kraken,
            CoinbaseConnector coinbase,
            BitfinexConnector bitfinex,
            OkxConnector okx) {
        this.connectors = List.of(binance, kraken, coinbase, bitfinex, okx);
    }

    public void setGlobalUpdateCallback(Consumer<OrderBook> callback) {
        this.globalUpdateCallback = callback;
        connectors.forEach(c -> c.setOnUpdateCallback(callback));
    }

    @PostConstruct
    public void startAll() {
        log.info("🚀 Starting {} exchange connectors...", connectors.size());
        for (AbstractExchangeConnector connector : connectors) {
            executor.submit(() -> {
                try {
                    connector.start();
                } catch (Exception e) {
                    log.error("[{}] Fatal connector error: {}", connector.getExchangeName(), e.getMessage(), e);
                }
            });
        }
        log.info("✅ All connectors started (using Virtual Threads)");
    }

    @PreDestroy
    public void stopAll() {
        log.info("Stopping all connectors...");
        connectors.forEach(AbstractExchangeConnector::stop);
        executor.shutdownNow();
    }

    public Optional<OrderBook> getOrderBook(String exchangeName) {
        return getOrderBook(exchangeName, "BTC/USDT");
    }

    public Optional<OrderBook> getOrderBook(String exchangeName, String symbol) {
        return connectors.stream()
                .filter(c -> c.getExchangeName().equalsIgnoreCase(exchangeName))
                .findFirst()
                .map(c -> c.getOrderBook(symbol));
    }

    public List<OrderBook> getAllOrderBooks() {
        return connectors.stream()
                .flatMap(c -> c.getOrderBooks().stream())
                .collect(Collectors.toList());
    }

    public Map<String, ConnectorState> getStates() {
        return connectors.stream()
                .collect(Collectors.toMap(
                        AbstractExchangeConnector::getExchangeName,
                        AbstractExchangeConnector::getState
                ));
    }

    public boolean allHealthy() {
        return connectors.stream().allMatch(c -> c.getState().isHealthy());
    }

    public long getLiveCount() {
        return connectors.stream().filter(c -> c.getState() == ConnectorState.LIVE).count();
    }

    /**
     * Stops a specific connector by exchange name and clears its order books.
     */
    public void stopConnector(String exchangeName) {
        connectors.stream()
                .filter(c -> c.getExchangeName().equalsIgnoreCase(exchangeName))
                .findFirst()
                .ifPresent(c -> {
                    log.info("⏹ Stopping connector: {}", exchangeName);
                    c.stop();
                    c.getOrderBooks().forEach(OrderBook::clear);
                });
    }

    /**
     * Starts a previously stopped connector in a new virtual thread.
     */
    public void startConnector(String exchangeName) {
        connectors.stream()
                .filter(c -> c.getExchangeName().equalsIgnoreCase(exchangeName))
                .filter(c -> c.getState() == ConnectorState.DEAD || c.getState() == ConnectorState.INITIALIZING)
                .findFirst()
                .ifPresent(c -> {
                    log.info("▶ Starting connector: {}", exchangeName);
                    if (globalUpdateCallback != null) {
                        c.setOnUpdateCallback(globalUpdateCallback);
                    }
                    executor.submit(() -> {
                        try { c.start(); } catch (Exception e) {
                            log.error("[{}] Fatal connector error: {}", exchangeName, e.getMessage(), e);
                        }
                    });
                });
    }

    /**
     * Returns list of exchange names that are not DEAD.
     */
    public List<String> getActiveExchanges() {
        return connectors.stream()
                .filter(c -> c.getState() != ConnectorState.DEAD)
                .map(AbstractExchangeConnector::getExchangeName)
                .collect(Collectors.toList());
    }
}
