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

    // Virtual thread executor — one per connector
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ConnectorRegistry(
            BinanceConnector binance,
            KrakenConnector kraken,
            CoinbaseConnector coinbase) {
        this.connectors = List.of(binance, kraken, coinbase);
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
        return connectors.stream()
                .filter(c -> c.getExchangeName().equalsIgnoreCase(exchangeName))
                .findFirst()
                .map(AbstractExchangeConnector::getOrderBook);
    }

    public List<OrderBook> getAllOrderBooks() {
        return connectors.stream()
                .map(AbstractExchangeConnector::getOrderBook)
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
}
