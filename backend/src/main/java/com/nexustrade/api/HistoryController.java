package com.nexustrade.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST endpoint for trade history from JSONL events file.
 * GET /api/history?limit=50 — returns last N events
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${FRONTEND_URL:http://localhost:3000}")
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String eventsFile;

    public HistoryController(
            @Value("${nexustrade.persistence.events-file:./data/events.jsonl}") String eventsFile) {
        this.eventsFile = eventsFile;
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            Path path = Path.of(eventsFile);
            if (!Files.exists(path)) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // Read last N lines efficiently
            List<String> allLines = Files.readAllLines(path);
            int start = Math.max(0, allLines.size() - limit);
            List<Map<String, Object>> events = new ArrayList<>();

            for (int i = allLines.size() - 1; i >= start; i--) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = MAPPER.readValue(allLines.get(i), Map.class);
                    events.add(event);
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }

            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.warn("Failed to read history: {}", e.getMessage());
            return ResponseEntity.ok(Collections.emptyList());
        }
    }
}
