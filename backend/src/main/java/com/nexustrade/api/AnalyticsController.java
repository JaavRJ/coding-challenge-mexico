package com.nexustrade.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${FRONTEND_URL:http://localhost:3000}")
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String eventsFile;

    public AnalyticsController(
            @Value("${nexustrade.persistence.events-file:./data/events.jsonl}") String eventsFile) {
        this.eventsFile = eventsFile;
    }

    public record ReplayRequest(double feeMultiplier, double minRoiPct) {}

    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> replayEvents(@RequestBody ReplayRequest req) {
        Path path = Path.of(eventsFile);
        if (!Files.exists(path)) {
            return ResponseEntity.ok(Map.of("error", "No historical data"));
        }

        int totalEvaluated = 0;
        int originalExecuted = 0;
        int newExecuted = 0;
        double originalPnl = 0.0;
        double newPnl = 0.0;

        Map<String, Integer> originalRejections = new HashMap<>();
        Map<String, Integer> newRejections = new HashMap<>();

        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                @SuppressWarnings("unchecked")
                Map<String, Object> event = MAPPER.readValue(line, Map.class);
                
                String status = (String) event.get("status");
                if (status == null) continue;
                totalEvaluated++;

                double grossSpread = getDouble(event, "grossSpread");
                double feesTotal = getDouble(event, "feesTotal");
                double netProfit = getDouble(event, "netProfit");
                double volume = getDouble(event, "volume");
                double buyPrice = getDouble(event, "buyPrice");
                String origReason = (String) event.getOrDefault("rejectionReason", "");
                
                // Track original
                if ("EXECUTED".equals(status)) {
                    originalExecuted++;
                    originalPnl += netProfit;
                } else if ("REJECTED_FEES".equals(status)) {
                    originalRejections.put("FEES_ROI", originalRejections.getOrDefault("FEES_ROI", 0) + 1);
                } else {
                    originalRejections.put(status, originalRejections.getOrDefault(status, 0) + 1);
                }

                // Simulate new scenario
                // Skip triangular arbitrage for now or handle them with startUsdt? 
                // For regular direct arbitrage: Buy Cost = buyPrice * volume
                double buyCost = buyPrice * volume;
                if (buyCost <= 0 && "TRIANGULAR".equals(event.get("type"))) {
                    // Start USDT was logged somewhere? Actually triangular has 'startUsdt'
                    buyCost = getDouble(event, "startUsdt");
                }
                
                double newFeesTotal = feesTotal * req.feeMultiplier();
                double newNetProfit = grossSpread - newFeesTotal;
                
                // Required profit based on new minRoiPct
                double requiredProfit = buyCost * (req.minRoiPct() / 100.0);

                String newStatus = status;
                
                // We only override REJECTED_FEES and EXECUTED statuses. 
                // We assume Latency rejections still happen in replay.
                if ("EXECUTED".equals(status) || "REJECTED_FEES".equals(status)) {
                    if (newNetProfit > requiredProfit) {
                        newStatus = "EXECUTED";
                    } else {
                        newStatus = "REJECTED_FEES";
                    }
                }

                if ("EXECUTED".equals(newStatus)) {
                    newExecuted++;
                    newPnl += newNetProfit;
                } else if ("REJECTED_FEES".equals(newStatus)) {
                    newRejections.put("FEES_ROI", newRejections.getOrDefault("FEES_ROI", 0) + 1);
                } else {
                    newRejections.put(newStatus, newRejections.getOrDefault(newStatus, 0) + 1);
                }
            }

        } catch (Exception e) {
            log.error("Replay engine failed", e);
            return ResponseEntity.internalServerError().build();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalEvaluated", totalEvaluated);
        result.put("originalExecuted", originalExecuted);
        result.put("newExecuted", newExecuted);
        result.put("originalPnl", originalPnl);
        result.put("newPnl", newPnl);
        result.put("originalRejections", originalRejections);
        result.put("newRejections", newRejections);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/analytics")
    public ResponseEntity<List<Map<String, Object>>> getHeatmapAnalytics() {
        Path path = Path.of(eventsFile);
        if (!Files.exists(path)) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        // Key: HourOfDay_Route (e.g. "14_BINANCE->KRAKEN")
        Map<String, HeatmapCell> cells = new HashMap<>();

        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                @SuppressWarnings("unchecked")
                Map<String, Object> event = MAPPER.readValue(line, Map.class);
                
                Number ts = (Number) event.get("ts");
                if (ts == null) continue;
                
                String buyEx = (String) event.get("buyExchange");
                String sellEx = (String) event.get("sellExchange");
                if (buyEx == null || sellEx == null) continue;
                
                String route = buyEx.substring(0, 3) + "->" + sellEx.substring(0, 3);
                
                ZonedDateTime zdt = Instant.ofEpochMilli(ts.longValue()).atZone(ZoneId.systemDefault());
                int hour = zdt.getHour();
                
                String key = hour + "_" + route;
                HeatmapCell cell = cells.computeIfAbsent(key, k -> new HeatmapCell(hour, route));
                
                cell.count++;
                cell.totalGrossSpread += getDouble(event, "grossSpread");
                if ("EXECUTED".equals(event.get("status"))) {
                    cell.executedCount++;
                }
            }
        } catch (Exception e) {
            log.error("Analytics engine failed", e);
            return ResponseEntity.internalServerError().build();
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (HeatmapCell cell : cells.values()) {
            Map<String, Object> map = new HashMap<>();
            map.put("hour", cell.hour);
            map.put("route", cell.route);
            map.put("count", cell.count);
            map.put("executed", cell.executedCount);
            map.put("avgGrossSpread", cell.count > 0 ? (cell.totalGrossSpread / cell.count) : 0);
            response.add(map);
        }

        return ResponseEntity.ok(response);
    }
    
    private double getDouble(Map<String, Object> event, String key) {
        Object val = event.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    private static class HeatmapCell {
        int hour;
        String route;
        int count = 0;
        int executedCount = 0;
        double totalGrossSpread = 0.0;

        HeatmapCell(int hour, String route) {
            this.hour = hour;
            this.route = route;
        }
    }
}
