package com.nexustrade.metrics;

import com.nexustrade.model.ArbitrageOpportunity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Append-only JSONL writer for all arbitrage events (executed + rejected).
 * Each line is a self-contained JSON object for streaming analysis.
 */
@Component
public class ShadowLearningRecorder {

    private static final Logger log = LoggerFactory.getLogger(ShadowLearningRecorder.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PrintWriter writer;

    public ShadowLearningRecorder(
            @Value("${nexustrade.persistence.events-file:./data/events.jsonl}") String eventsFile) {
        try {
            Path path = Path.of(eventsFile);
            Files.createDirectories(path.getParent());
            writer = new PrintWriter(new BufferedWriter(new FileWriter(eventsFile, true)), true);
            log.info("📝 ShadowLearningRecorder writing to: {}", path.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to open events file: {}", e.getMessage());
            writer = null;
        }
    }

    public void record(ArbitrageOpportunity opp) {
        if (writer == null) return;

        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ts", opp.timestampMs());
            node.put("type", "OPPORTUNITY");
            node.put("buyExchange", opp.buyExchange());
            node.put("sellExchange", opp.sellExchange());
            node.put("buyPrice", opp.buyPrice().doubleValue());
            node.put("sellPrice", opp.sellPrice().doubleValue());
            node.put("volume", opp.volume().doubleValue());
            node.put("grossSpread", opp.grossSpread().doubleValue());
            node.put("feesTotal", opp.feesTotal().doubleValue());
            node.put("netProfit", opp.netProfit().doubleValue());
            node.put("spreadPct", opp.spreadPct().doubleValue());
            node.put("status", opp.status().name());
            node.put("rejectionReason", opp.rejectionReason());
            node.put("decisionLatencyMs", opp.decisionLatencyMs());

            writer.println(MAPPER.writeValueAsString(node));
        } catch (Exception e) {
            log.warn("Failed to write event: {}", e.getMessage());
        }
    }

    public void record(com.nexustrade.model.TriangularOpportunity opp) {
        if (writer == null) return;

        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("ts", opp.timestampMs());
            node.put("type", "TRIANGULAR");
            node.put("exchange", opp.exchange());
            node.put("startUsdt", opp.startUsdt().doubleValue());
            node.put("btcAmount", opp.btcAmount().doubleValue());
            node.put("ethAmount", opp.ethAmount().doubleValue());
            node.put("finalUsdt", opp.finalUsdt().doubleValue());
            node.put("feesTotal", opp.feesTotal().doubleValue());
            node.put("netProfit", opp.netProfit().doubleValue());
            node.put("spreadPct", opp.spreadPct().doubleValue());
            node.put("status", opp.status().name());
            node.put("rejectionReason", opp.rejectionReason());
            node.put("decisionLatencyMs", opp.decisionLatencyMs());

            writer.println(MAPPER.writeValueAsString(node));
        } catch (Exception e) {
            log.warn("Failed to write event: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        if (writer != null) {
            writer.flush();
            writer.close();
            log.info("📝 ShadowLearningRecorder closed");
        }
    }
}
