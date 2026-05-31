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

        int[] counts = new int[3]; // totalEvaluated, originalExecuted, newExecuted
        double[] pnls = new double[2]; // originalPnl, newPnl
        Map<String, Integer> originalRejections = new HashMap<>();
        Map<String, Integer> newRejections = new HashMap<>();

        try (java.util.stream.Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = MAPPER.readValue(line, Map.class);
                    
                    String status = (String) event.get("status");
                    if (status == null) return;
                    
                    int[] localCounts = new int[3];
                    localCounts[0] = 1;
                    
                    double grossSpread = getDouble(event, "grossSpread");
                    double feesTotal = getDouble(event, "feesTotal");
                    double netProfit = getDouble(event, "netProfit");
                    double volume = getDouble(event, "volume");
                    double buyPrice = getDouble(event, "buyPrice");
                    
                    double localOrigPnl = 0.0;
                    if ("EXECUTED".equals(status)) {
                        localCounts[1] = 1;
                        localOrigPnl = netProfit;
                    } else if ("REJECTED_FEES".equals(status)) {
                        originalRejections.merge("FEES_ROI", 1, Integer::sum);
                    } else {
                        originalRejections.merge(status, 1, Integer::sum);
                    }

                    double buyCost = buyPrice * volume;
                    if (buyCost <= 0 && "TRIANGULAR".equals(event.get("type"))) {
                        buyCost = getDouble(event, "startUsdt");
                    }
                    
                    double newFeesTotal = feesTotal * req.feeMultiplier();
                    double newNetProfit = grossSpread - newFeesTotal;
                    double requiredProfit = buyCost * (req.minRoiPct() / 100.0);

                    String newStatus = status;
                    if ("EXECUTED".equals(status) || "REJECTED_FEES".equals(status)) {
                        if (newNetProfit > requiredProfit) {
                            newStatus = "EXECUTED";
                        } else {
                            newStatus = "REJECTED_FEES";
                        }
                    }

                    double localNewPnl = 0.0;
                    if ("EXECUTED".equals(newStatus)) {
                        localCounts[2] = 1;
                        localNewPnl = newNetProfit;
                    } else if ("REJECTED_FEES".equals(newStatus)) {
                        newRejections.merge("FEES_ROI", 1, Integer::sum);
                    } else {
                        newRejections.merge(newStatus, 1, Integer::sum);
                    }

                    counts[0] += localCounts[0];
                    counts[1] += localCounts[1];
                    counts[2] += localCounts[2];
                    pnls[0] += localOrigPnl;
                    pnls[1] += localNewPnl;

                } catch (Exception e) { }
            });
        } catch (Exception e) {
            log.error("Replay engine failed", e);
            return ResponseEntity.internalServerError().build();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalEvaluated", counts[0]);
        result.put("originalExecuted", counts[1]);
        result.put("newExecuted", counts[2]);
        result.put("originalPnl", pnls[0]);
        result.put("newPnl", pnls[1]);
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

        try (java.util.stream.Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = MAPPER.readValue(line, Map.class);
                    
                    Number ts = (Number) event.get("ts");
                    if (ts == null) return;
                    
                    String buyEx = (String) event.get("buyExchange");
                    String sellEx = (String) event.get("sellExchange");
                    if (buyEx == null || sellEx == null) return;
                    
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
                } catch (Exception e) {}
            });
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
