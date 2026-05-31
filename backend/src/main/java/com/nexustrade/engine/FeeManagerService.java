package com.nexustrade.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexustrade.util.HmacAuthUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

@Service
public class FeeManagerService {

    private static final Logger log = LoggerFactory.getLogger(FeeManagerService.class);

    private final EngineConfig engineConfig;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FeeManagerService(EngineConfig engineConfig) {
        this.engineConfig = engineConfig;
        this.httpClient = HttpClient.newBuilder().build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        log.info("Initializing FeeManagerService...");
        updateFees();
    }

    /**
     * Updates fees every hour (3600000 ms).
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 3600000)
    public void updateFees() {
        log.info("Fetching live fee structures from exchanges...");
        updateBinanceFees();
        updateKrakenFees();
        updateCoinbaseFees();
    }

    private void updateBinanceFees() {
        EngineConfig.ExchangeProps props = engineConfig.getExchanges().get("binance");
        if (props == null) return;

        String apiKey = props.getApiKey();
        String apiSecret = props.getApiSecret();

        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            log.warn("[BINANCE] API keys not found. Activating VIP DEMO MODE for Binance fees.");
            props.setFeeMaker(0.0005); // 5 bps
            props.setFeeTaker(0.0010); // 10 bps
            return;
        }

        try {
            long timestamp = Instant.now().toEpochMilli();
            String queryParams = "symbol=BTCUSDT&timestamp=" + timestamp;
            String signature = HmacAuthUtil.generateHmacSha256Hex(apiSecret, queryParams);
            String url = "https://api.binance.com/sapi/v1/asset/tradeFee?" + queryParams + "&signature=" + signature;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-MBX-APIKEY", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.isArray() && !root.isEmpty()) {
                    JsonNode btcUsdt = root.get(0);
                    double maker = btcUsdt.get("makerCommission").asDouble();
                    double taker = btcUsdt.get("takerCommission").asDouble();
                    props.setFeeMaker(maker);
                    props.setFeeTaker(taker);
                    log.info("[BINANCE] Live fees updated - Maker: {}, Taker: {}", maker, taker);
                }
            } else {
                log.error("[BINANCE] Failed to fetch fees. Status: {}, Body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[BINANCE] Exception fetching fees", e);
        }
    }

    private void updateKrakenFees() {
        EngineConfig.ExchangeProps props = engineConfig.getExchanges().get("kraken");
        if (props == null) return;

        String apiKey = props.getApiKey();
        String apiSecret = props.getApiSecret();

        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            log.warn("[KRAKEN] API keys not found. Activating VIP DEMO MODE for Kraken fees.");
            props.setFeeMaker(0.0006); // 6 bps
            props.setFeeTaker(0.0016); // 16 bps
            return;
        }

        try {
            String nonce = String.valueOf(Instant.now().toEpochMilli()) + "000";
            String postData = "nonce=" + nonce + "&pair=XBTUSD";
            String path = "/0/private/TradeVolume";
            String signature = HmacAuthUtil.generateKrakenSignature(path, nonce, postData, apiSecret);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.kraken.com" + path))
                    .header("API-Key", apiKey)
                    .header("API-Sign", signature)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(postData))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("result") && root.get("result").has("fees")) {
                    JsonNode feesNode = root.get("result").get("fees").get("XXBTZUSD");
                    if (feesNode != null && feesNode.has("fee")) {
                        double fee = feesNode.get("fee").asDouble() / 100.0; // kraken returns percent e.g. 0.26
                        double makerFee = feesNode.has("maker") ? (feesNode.get("maker").asDouble() / 100.0) : fee;
                        props.setFeeTaker(fee);
                        props.setFeeMaker(makerFee);
                        log.info("[KRAKEN] Live fees updated - Maker: {}, Taker: {}", makerFee, fee);
                    }
                }
            } else {
                log.error("[KRAKEN] Failed to fetch fees. Status: {}, Body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[KRAKEN] Exception fetching fees", e);
        }
    }

    private void updateCoinbaseFees() {
        EngineConfig.ExchangeProps props = engineConfig.getExchanges().get("coinbase");
        if (props == null) return;

        String apiKey = props.getApiKey();
        String apiSecret = props.getApiSecret();

        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            log.warn("[COINBASE] API keys not found. Activating VIP DEMO MODE for Coinbase fees ($1M-$15M Tier).");
            props.setFeeMaker(0.0008); // 8 bps
            props.setFeeTaker(0.0018); // 18 bps
            return;
        }

        try {
            long timestamp = Instant.now().getEpochSecond();
            String method = "GET";
            String path = "/api/v3/brokerage/transaction_summary";
            String message = timestamp + method + path;
            String signature = HmacAuthUtil.generateHmacSha256Hex(apiSecret, message);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.coinbase.com" + path))
                    .header("CB-ACCESS-KEY", apiKey)
                    .header("CB-ACCESS-SIGN", signature)
                    .header("CB-ACCESS-TIMESTAMP", String.valueOf(timestamp))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("fee_tier")) {
                    JsonNode feeTier = root.get("fee_tier");
                    double taker = feeTier.get("taker_fee_rate").asDouble();
                    double maker = feeTier.get("maker_fee_rate").asDouble();
                    props.setFeeMaker(maker);
                    props.setFeeTaker(taker);
                    log.info("[COINBASE] Live fees updated - Maker: {}, Taker: {}", maker, taker);
                }
            } else {
                log.error("[COINBASE] Failed to fetch fees. Status: {}, Body: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("[COINBASE] Exception fetching fees", e);
        }
    }
}
