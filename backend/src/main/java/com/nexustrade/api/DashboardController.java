package com.nexustrade.api;

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
@CrossOrigin(origins = "*")
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

    /**
     * Called by ArbitrageEngine to broadcast events to all connected SSE clients.
     */
    public void broadcast(ArbitrageOpportunity opp) {
        if (emitters.isEmpty()) return;

        String json = String.format(
                "{\"ts\":%d,\"buyExchange\":\"%s\",\"sellExchange\":\"%s\"," +
                "\"buyPrice\":%.2f,\"sellPrice\":%.2f,\"volume\":%.6f," +
                "\"grossSpread\":%.2f,\"feesTotal\":%.2f,\"netProfit\":%.2f," +
                "\"spreadPct\":%.4f,\"status\":\"%s\",\"rejectionReason\":%s," +
                "\"decisionLatencyMs\":%d}",
                opp.timestampMs(), opp.buyExchange(), opp.sellExchange(),
                opp.buyPrice(), opp.sellPrice(), opp.volume(),
                opp.grossSpread(), opp.feesTotal(), opp.netProfit(),
                opp.spreadPct(), opp.status().name(),
                opp.rejectionReason() != null ? "\"" + opp.rejectionReason().replace("\"", "'") + "\"" : "null",
                opp.decisionLatencyMs());

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("opportunity")
                        .data(json));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    public int getConnectedClients() {
        return emitters.size();
    }
}
