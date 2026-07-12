package com.nexustrade.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades")
public class TradeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant ts = Instant.now();

    @Column(nullable = false, length = 20)
    private String type = "DIRECT";

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "buy_exchange", length = 20)
    private String buyExchange;

    @Column(name = "sell_exchange", length = 20)
    private String sellExchange;

    @Column(length = 20)
    private String exchange;

    @Column(name = "buy_price", precision = 18, scale = 8)
    private BigDecimal buyPrice;

    @Column(name = "sell_price", precision = 18, scale = 8)
    private BigDecimal sellPrice;

    @Column(precision = 18, scale = 8)
    private BigDecimal volume;

    @Column(name = "gross_spread", precision = 18, scale = 8)
    private BigDecimal grossSpread;

    @Column(name = "fees_total", precision = 18, scale = 8)
    private BigDecimal feesTotal;

    @Column(name = "net_profit", precision = 18, scale = 8)
    private BigDecimal netProfit;

    @Column(name = "spread_pct", precision = 10, scale = 6)
    private BigDecimal spreadPct;

    @Column(name = "decision_latency_ms")
    private Integer decisionLatencyMs;

    @Column(name = "rejection_reason", length = 40)
    private String rejectionReason;

    @Column(name = "ai_confidence", precision = 5, scale = 2)
    private BigDecimal aiConfidence;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTs() { return ts; }
    public void setTs(Instant ts) { this.ts = ts; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBuyExchange() { return buyExchange; }
    public void setBuyExchange(String buyExchange) { this.buyExchange = buyExchange; }
    public String getSellExchange() { return sellExchange; }
    public void setSellExchange(String sellExchange) { this.sellExchange = sellExchange; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public BigDecimal getBuyPrice() { return buyPrice; }
    public void setBuyPrice(BigDecimal buyPrice) { this.buyPrice = buyPrice; }
    public BigDecimal getSellPrice() { return sellPrice; }
    public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public BigDecimal getGrossSpread() { return grossSpread; }
    public void setGrossSpread(BigDecimal grossSpread) { this.grossSpread = grossSpread; }
    public BigDecimal getFeesTotal() { return feesTotal; }
    public void setFeesTotal(BigDecimal feesTotal) { this.feesTotal = feesTotal; }
    public BigDecimal getNetProfit() { return netProfit; }
    public void setNetProfit(BigDecimal netProfit) { this.netProfit = netProfit; }
    public BigDecimal getSpreadPct() { return spreadPct; }
    public void setSpreadPct(BigDecimal spreadPct) { this.spreadPct = spreadPct; }
    public Integer getDecisionLatencyMs() { return decisionLatencyMs; }
    public void setDecisionLatencyMs(Integer decisionLatencyMs) { this.decisionLatencyMs = decisionLatencyMs; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public BigDecimal getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(BigDecimal aiConfidence) { this.aiConfidence = aiConfidence; }
}
