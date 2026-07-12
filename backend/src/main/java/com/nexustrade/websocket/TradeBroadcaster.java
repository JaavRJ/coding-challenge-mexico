package com.nexustrade.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class TradeBroadcaster {
    private final SimpMessagingTemplate messaging;

    public TradeBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void broadcastTrade(Object trade) {
        messaging.convertAndSend("/topic/trades", trade);
    }

    public void broadcastEngine(Object stats) {
        messaging.convertAndSend("/topic/engine", stats);
    }

    public void broadcastOrderBooks(Object books) {
        messaging.convertAndSend("/topic/orderbooks", books);
    }

    public void broadcastAlert(Object alert) {
        messaging.convertAndSend("/topic/alerts", alert);
    }
}
