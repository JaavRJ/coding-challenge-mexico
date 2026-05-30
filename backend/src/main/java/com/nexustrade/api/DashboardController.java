package com.nexustrade.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexustrade.engine.ArbitrageEngine;
import com.nexustrade.model.ArbitrageOpportunity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE endpoint for real-time event streaming to the dashboard.
 * GET /api/stream — pushes ArbitrageOpportunity events as they occur.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${FRONTEND_URL:http://localhost:3000}")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 min

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        log.info("SSE client connected. Total: {}", emitters.size());
        return emitter;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Send a ping event every 15 seconds to prevent reverse proxies (e.g. Railway, Nginx)
     * from dropping the SSE connection due to inactivity.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) return;
        
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("ping").data(""));
            } catch (Exception e) {
                emitter.complete();
                emitters.remove(emitter);
            }
        }
    }

    /**
     * Called by ArbitrageEngine to broadcast events to all connected SSE clients.
     */
    public void broadcast(ArbitrageOpportunity opp) {
        if (emitters.isEmpty()) return;

        try {
            String json = MAPPER.writeValueAsString(opp);
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("opportunity")
                            .data(json));
                } catch (IOException e) {
                    emitters.remove(emitter);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to serialize opportunity: {}", e.getMessage());
        }
    }

    public void broadcastTriangular(com.nexustrade.model.TriangularOpportunity opp) {
        if (emitters.isEmpty()) return;

        try {
            String json = MAPPER.writeValueAsString(opp);
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("triangular_opportunity")
                            .data(json));
                } catch (IOException e) {
                    emitters.remove(emitter);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to serialize triangular opportunity: {}", e.getMessage());
        }
    }

    public int getConnectedClients() {
        return emitters.size();
    }
}
