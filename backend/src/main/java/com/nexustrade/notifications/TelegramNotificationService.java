package com.nexustrade.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends trade alerts and summaries to a Telegram channel.
 * Rate limited to 1 message per 5 seconds to respect Telegram API limits.
 */
@Service
public class TelegramNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);
    private static final long RATE_LIMIT_MS = 5000;
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.chat.id:}")
    private String chatId;

    private volatile long lastSentMs = 0;
    private final AtomicInteger tradesAlerted = new AtomicInteger(0);
    private volatile BigDecimal cumulativePnl = BigDecimal.ZERO;

    /** Whether Telegram is configured */
    public boolean isEnabled() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }

    /**
     * Send a trade execution alert.
     */
    public void sendTradeAlert(String buyExchange, String sellExchange,
                               BigDecimal netProfit, double spreadPct,
                               long latencyMs, int aiScore, BigDecimal cumPnl) {
        if (!isEnabled()) return;
        cumulativePnl = cumPnl;
        tradesAlerted.incrementAndGet();

        String emoji = netProfit.compareTo(BigDecimal.ZERO) >= 0 ? "🟢" : "🔴";
        String msg = String.format(
            "%s TRADE EJECUTADO — NobaTrade\n\n" +
            "📈 BTC/USDT | %s → %s\n" +
            "💰 Profit: +$%.2f USDT\n" +
            "📊 Spread: %.4f%%\n" +
            "⚡ Latencia: %dms\n" +
            "🤖 AI Score: %d/100\n\n" +
            "📋 P&L Acumulado: +$%.2f",
            emoji, buyExchange, sellExchange,
            netProfit, spreadPct, latencyMs, aiScore, cumPnl
        );
        sendMessage(msg);
    }

    /** Circuit breaker alert */
    public void sendCircuitBreakerAlert(int losses, int pauseSeconds) {
        if (!isEnabled()) return;
        String msg = String.format(
            "⚠️ CIRCUIT BREAKER ACTIVADO — NobaTrade\n\n" +
            "Pérdidas consecutivas: %d\n" +
            "Motor pausado por: %d segundos",
            losses, pauseSeconds
        );
        sendMessage(msg);
    }

    /** Daily summary at midnight UTC */
    @Scheduled(cron = "0 0 0 * * *")
    public void sendDailySummary() {
        if (!isEnabled()) return;
        String msg = String.format(
            "📊 RESUMEN DIARIO — NobaTrade\n\n" +
            "✅ Trades alertados: %d\n" +
            "💰 P&L acumulado: +$%.2f\n" +
            "⏰ %s UTC",
            tradesAlerted.get(), cumulativePnl, Instant.now().toString()
        );
        sendMessage(msg);
        tradesAlerted.set(0);
    }

    /** Rate-limited message sender */
    private void sendMessage(String text) {
        long now = System.currentTimeMillis();
        if (now - lastSentMs < RATE_LIMIT_MS) {
            log.debug("[TELEGRAM] Rate limited, skipping message");
            return;
        }
        lastSentMs = now;

        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String url = String.format(
                    "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s&parse_mode=HTML",
                    botToken, chatId, encoded);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("[TELEGRAM] Failed to send message: HTTP {}", res.statusCode());
            } else {
                log.debug("[TELEGRAM] Message sent successfully");
            }
        } catch (Exception e) {
            log.warn("[TELEGRAM] Error sending message: {}", e.getMessage());
        }
    }
}
