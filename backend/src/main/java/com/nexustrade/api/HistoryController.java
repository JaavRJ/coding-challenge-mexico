package com.nexustrade.api;

import com.nexustrade.persistence.TradeEntity;
import com.nexustrade.persistence.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST endpoint for authoritative trade history from database.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*")
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);
    private final TradeRepository tradeRepo;

    public HistoryController(TradeRepository tradeRepo) {
        this.tradeRepo = tradeRepo;
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            List<TradeEntity> trades = tradeRepo.findTop50ByOrderByTsDesc();
            List<Map<String, Object>> response = trades.stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to read history from DB: {}", e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    @GetMapping("/history/executed")
    public ResponseEntity<List<Map<String, Object>>> getExecutedHistory(
            @RequestParam(defaultValue = "100") int limit) {
        try {
            List<TradeEntity> trades = tradeRepo.findTop50ByStatusOrderByTsDesc("EXECUTED");
            List<Map<String, Object>> response = trades.stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("Failed to read executed history from DB: {}", e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    private Map<String, Object> toMap(TradeEntity t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", t.getId() != null ? String.valueOf(t.getId()) : UUID.randomUUID().toString());
        map.put("timestampMs", t.getTs() != null ? t.getTs().toEpochMilli() : System.currentTimeMillis());
        map.put("ts", t.getTs() != null ? t.getTs().toEpochMilli() : System.currentTimeMillis());
        map.put("type", t.getType() != null ? t.getType() : "DIRECT");
        map.put("buyExchange", t.getBuyExchange());
        map.put("sellExchange", t.getSellExchange());
        map.put("exchange", t.getExchange());
        map.put("buyPrice", t.getBuyPrice() != null ? t.getBuyPrice().doubleValue() : null);
        map.put("sellPrice", t.getSellPrice() != null ? t.getSellPrice().doubleValue() : null);
        map.put("volume", t.getVolume() != null ? t.getVolume().doubleValue() : 0.0);
        map.put("grossSpread", t.getGrossSpread() != null ? t.getGrossSpread().doubleValue() : 0.0);
        map.put("feesTotal", t.getFeesTotal() != null ? t.getFeesTotal().doubleValue() : 0.0);
        map.put("netProfit", t.getNetProfit() != null ? t.getNetProfit().doubleValue() : 0.0);
        map.put("spreadPct", t.getSpreadPct() != null ? t.getSpreadPct().doubleValue() : null);
        map.put("decisionLatencyMs", t.getDecisionLatencyMs());
        map.put("status", t.getStatus() != null ? t.getStatus() : "EXECUTED");
        map.put("aiConfidence", t.getAiConfidence() != null ? t.getAiConfidence().doubleValue() : null);
        return map;
    }
}
