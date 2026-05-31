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

            List<String> lastLines = new ArrayList<>();
            try (java.io.RandomAccessFile fileHandler = new java.io.RandomAccessFile(path.toFile(), "r")) {
                long fileLength = fileHandler.length() - 1;
                StringBuilder sb = new StringBuilder();
                int lineCount = 0;

                for (long filePointer = fileLength; filePointer != -1; filePointer--) {
                    fileHandler.seek(filePointer);
                    int readByte = fileHandler.readByte();

                    if (readByte == 0xA) {
                        if (filePointer == fileLength) {
                            continue;
                        }
                        lastLines.add(sb.reverse().toString());
                        sb.setLength(0);
                        lineCount++;
                        if (lineCount == limit) {
                            break;
                        }
                    } else if (readByte == 0xD) {
                        if (filePointer == fileLength - 1) {
                            continue;
                        }
                    } else {
                        sb.append((char) readByte);
                    }
                }
                if (sb.length() > 0 && lineCount < limit) {
                    lastLines.add(sb.reverse().toString());
                }
            }

            List<Map<String, Object>> events = new ArrayList<>();
            // The list is in reverse order (newest first).
            for (String line : lastLines) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> event = MAPPER.readValue(line, Map.class);
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
